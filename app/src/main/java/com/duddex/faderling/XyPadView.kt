package com.duddex.faderling

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * XY Pad: a square area, centred in the view, sending X (left 0 … right 127) and Y (bottom 0 … top 127).
 * The dot glides to the touched spot like a Fader, then follows the finger; it stays where it is on release.
 */
class XyPadView(context: Context) : View(context) {

    interface Listener {
        /** [x] or [y] is null when that axis did not change. */
        fun onXyChanged(view: XyPadView, x: Int?, y: Int?)
        fun onXyReleased(view: XyPadView, x: Int, y: Int)
    }

    var listener: Listener? = null

    var name: String = ""
        set(v) { field = v; invalidate() }

    var valueX = XyPads.START_VALUE.roundToInt()
        private set
    var valueY = XyPads.START_VALUE.roundToInt()
        private set

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private var posX = XyPads.START_VALUE
    private var posY = XyPads.START_VALUE
    private var targetX = posX
    private var targetY = posY
    private var gliding = false
    private var releasePending = false
    private var touching = false
    private var pointerId = -1
    private var lastFrameNanos = 0L

    private val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.TRACK }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GRID; strokeWidth = dp(1f) }
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.ACCENT; strokeWidth = dp(2f) }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.ACCENT }
    private val dotRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.TEXT; style = Paint.Style.STROKE; strokeWidth = dp(2f) }
    private val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.TEXT_DIM; textSize = dp(14f) }
    private val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.TEXT; textSize = dp(14f); textAlign = Paint.Align.RIGHT }
    private val area = RectF()

    private val labelHeight get() = dp(26f)

    private fun updateArea() {
        val size = min(width.toFloat() - dp(12f), height - labelHeight - dp(8f))
        val left = (width - size) / 2f
        val top = labelHeight + (height - labelHeight - size) / 2f
        area.set(left, top, left + size, top + size)
    }

    private fun xOf(value: Float) = area.left + area.width() * value / 127f
    private fun yOf(value: Float) = area.bottom - area.height() * value / 127f

    override fun onDraw(canvas: Canvas) {
        updateArea()
        canvas.drawRoundRect(area, dp(12f), dp(12f), areaPaint)
        for (i in 1..3) {
            val gx = area.left + area.width() * i / 4
            val gy = area.top + area.height() * i / 4
            canvas.drawLine(gx, area.top, gx, area.bottom, gridPaint)
            canvas.drawLine(area.left, gy, area.right, gy, gridPaint)
        }

        val cx = xOf(posX)
        val cy = yOf(posY)
        crossPaint.alpha = if (touching) 255 else 140
        canvas.drawLine(cx, area.top, cx, area.bottom, crossPaint)
        canvas.drawLine(area.left, cy, area.right, cy, crossPaint)
        val r = if (touching) dp(16f) else dp(12f)
        canvas.drawCircle(cx, cy, r, dotPaint)
        canvas.drawCircle(cx, cy, r, dotRingPaint)

        val baseline = area.top - dp(8f)
        val label = TextUtils.ellipsize(name, namePaint, area.width() / 2, TextUtils.TruncateAt.END).toString()
        canvas.drawText(label, area.left + dp(4f), baseline, namePaint)
        canvas.drawText("X $valueX   Y $valueY", area.right - dp(4f), baseline, valuePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        updateArea()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (pointerId != -1) return true
                val i = event.actionIndex
                if (!area.contains(event.getX(i), event.getY(i))) return true
                parent?.requestDisallowInterceptTouchEvent(true)
                pointerId = event.getPointerId(i)
                touching = true
                releasePending = false
                glideTo(event.getX(i), event.getY(i))
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val i = event.findPointerIndex(pointerId)
                if (i < 0) return true
                if (gliding) setTarget(event.getX(i), event.getY(i)) else jumpTo(event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val id = event.getPointerId(event.actionIndex)
                if (event.actionMasked == MotionEvent.ACTION_POINTER_UP && id != pointerId) return true
                if (pointerId == -1) return true
                pointerId = -1
                touching = false
                invalidate()
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                if (gliding) releasePending = true else listener?.onXyReleased(this, valueX, valueY)
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun valueAtX(x: Float) = ((x - area.left) / area.width() * 127f).coerceIn(0f, 127f)
    private fun valueAtY(y: Float) = ((area.bottom - y) / area.height() * 127f).coerceIn(0f, 127f)

    private fun setTarget(x: Float, y: Float) {
        targetX = valueAtX(x)
        targetY = valueAtY(y)
    }

    private fun glideTo(x: Float, y: Float) {
        setTarget(x, y)
        if (!gliding && (targetX.roundToInt() != valueX || targetY.roundToInt() != valueY)) {
            gliding = true
            lastFrameNanos = System.nanoTime()
            postOnAnimation(glideStep)
        }
    }

    private fun jumpTo(x: Float, y: Float) {
        setTarget(x, y)
        setPosition(targetX, targetY)
    }

    private val glideStep = object : Runnable {
        override fun run() {
            val now = System.nanoTime()
            val maxStep = 127f * (now - lastFrameNanos) / (XyPads.GLIDE_FULL_TRAVEL_MS * 1_000_000f)
            lastFrameNanos = now
            setPosition(XyPads.step(posX, targetX, maxStep), XyPads.step(posY, targetY, maxStep))
            if (posX == targetX && posY == targetY) {
                gliding = false
                if (releasePending) {
                    releasePending = false
                    listener?.onXyReleased(this@XyPadView, valueX, valueY)
                }
            } else {
                postOnAnimation(this)
            }
        }
    }

    private fun setPosition(x: Float, y: Float) {
        posX = x
        posY = y
        invalidate()
        val newX = x.roundToInt()
        val newY = y.roundToInt()
        val changedX = newX != valueX
        val changedY = newY != valueY
        if (changedX || changedY) {
            valueX = newX
            valueY = newY
            listener?.onXyChanged(this, if (changedX) newX else null, if (changedY) newY else null)
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(glideStep)
        gliding = false
        super.onDetachedFromWindow()
    }

    private companion object {
        val GRID = Color.rgb(0x35, 0x3A, 0x44)
    }
}
