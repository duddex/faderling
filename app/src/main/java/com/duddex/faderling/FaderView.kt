package com.duddex.faderling

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextUtils
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

class FaderView(context: Context) : View(context) {

    interface Listener {
        fun onFaderMoved(fader: FaderView, value: Int)
        fun onFaderReleased(fader: FaderView, value: Int)
    }

    var listener: Listener? = null

    var name: String = ""
        set(v) { field = v; invalidate() }

    var value: Int = 0
        private set

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x2A, 0x2E, 0x36) }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xA7, 0x26) }
    private val fillActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xCC, 0x80) }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xEC, 0xEF, 0xF4) }
    private val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0xEC, 0xEF, 0xF4)
        textAlign = Paint.Align.CENTER
        textSize = dp(18f)
    }
    private val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0xB0, 0xB6, 0xC2)
        textAlign = Paint.Align.CENTER
        textSize = dp(14f)
    }

    private val labelHeight = dp(28f)
    private val thumbHeight = dp(14f)
    private val trackRect = RectF()
    private val rect = RectF()
    private var touching = false

    private fun updateTrackRect() {
        val trackWidth = (width * 0.55f).coerceAtMost(dp(64f))
        val cx = width / 2f
        trackRect.set(cx - trackWidth / 2, labelHeight + thumbHeight / 2, cx + trackWidth / 2, height - labelHeight - thumbHeight / 2)
    }

    override fun onDraw(canvas: Canvas) {
        updateTrackRect()
        val cx = width / 2f
        val radius = dp(6f)

        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)

        val thumbY = trackRect.bottom - trackRect.height() * value / 127f
        rect.set(trackRect.left, thumbY, trackRect.right, trackRect.bottom)
        canvas.drawRoundRect(rect, radius, radius, if (touching) fillActivePaint else fillPaint)

        rect.set(trackRect.left - dp(4f), thumbY - thumbHeight / 2, trackRect.right + dp(4f), thumbY + thumbHeight / 2)
        canvas.drawRoundRect(rect, dp(3f), dp(3f), thumbPaint)

        canvas.drawText(value.toString(), cx, labelHeight - dp(8f), valuePaint)

        val label = TextUtils.ellipsize(name, namePaint, width - dp(4f), TextUtils.TruncateAt.END).toString()
        canvas.drawText(label, cx, height - dp(8f), namePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                touching = true
                setFromY(event.y)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> setFromY(event.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touching = false
                invalidate()
                listener?.onFaderReleased(this, value)
            }
        }
        return true
    }

    private fun setFromY(y: Float) {
        updateTrackRect()
        val fraction = ((trackRect.bottom - y) / trackRect.height()).coerceIn(0f, 1f)
        val newValue = (fraction * 127).roundToInt()
        if (newValue != value) {
            value = newValue
            invalidate()
            listener?.onFaderMoved(this, value)
        }
    }
}
