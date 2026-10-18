package com.duddex.faderling

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import kotlin.math.min
import kotlin.math.roundToInt

/** Rotary Control turned by dragging vertically; the change is relative, so it never jumps. */
class KnobView(context: Context) : View(context) {

    var listener: ControlListener? = null

    var name: String = ""
        set(v) { field = v; invalidate() }

    var value: Int = 0
        private set

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val dragForFullRange = dp(200f)
    private var position = 0f
    private var dragStartY = 0f
    private var dragStartPosition = 0f
    private var touching = false

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.TRACK
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arcPaint = Paint(trackPaint).apply { color = Palette.ACCENT }
    private val arcActivePaint = Paint(trackPaint).apply { color = Palette.ACCENT_ACTIVE }
    private val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.TEXT
        textAlign = Paint.Align.CENTER
        textSize = dp(18f)
    }
    private val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.TEXT_DIM
        textAlign = Paint.Align.CENTER
        textSize = dp(14f)
    }
    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        val labelHeight = dp(24f)
        val available = min(width.toFloat(), height - labelHeight) - dp(8f)
        val stroke = (available * 0.07f).coerceAtLeast(dp(8f))
        val size = available - stroke
        if (size <= 0) return
        valuePaint.textSize = (size * 0.2f).coerceAtLeast(dp(18f))
        val cx = width / 2f
        val cy = (height - labelHeight) / 2f
        oval.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)

        trackPaint.strokeWidth = stroke
        arcPaint.strokeWidth = stroke
        arcActivePaint.strokeWidth = stroke
        canvas.drawArc(oval, START_ANGLE, SWEEP_ANGLE, false, trackPaint)
        if (position > 0f) {
            canvas.drawArc(oval, START_ANGLE, SWEEP_ANGLE * position / 127f, false, if (touching) arcActivePaint else arcPaint)
        }

        canvas.drawText(value.toString(), cx, cy - (valuePaint.descent() + valuePaint.ascent()) / 2, valuePaint)
        val label = TextUtils.ellipsize(name, namePaint, width - dp(4f), TextUtils.TruncateAt.END).toString()
        canvas.drawText(label, cx, height - dp(6f), namePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                touching = true
                dragStartY = event.y
                dragStartPosition = position
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                position = (dragStartPosition + (dragStartY - event.y) / dragForFullRange * 127f).coerceIn(0f, 127f)
                invalidate()
                val newValue = position.roundToInt()
                if (newValue != value) {
                    value = newValue
                    listener?.onControlChanged(this, value)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touching = false
                invalidate()
                listener?.onControlReleased(this, value)
            }
        }
        return true
    }

    private companion object {
        const val START_ANGLE = 135f
        const val SWEEP_ANGLE = 270f
    }
}
