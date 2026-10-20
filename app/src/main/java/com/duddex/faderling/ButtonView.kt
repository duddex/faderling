package com.duddex.faderling

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View

/** On/off Control: Momentary is on while held, Latch flips on each press. Sends 127 for on, 0 for off. */
class ButtonView(context: Context) : View(context) {

    var listener: ControlListener? = null

    var name: String = ""
        set(v) { field = v; invalidate() }

    var mode: ButtonMode = ButtonMode.MOMENTARY
        set(v) { field = v; invalidate() }

    var isOn: Boolean = false
        private set

    val value: Int get() = if (isOn) ON else OFF

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val offPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.TRACK }
    private val onPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.ACCENT }
    private val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(15f)
    }
    private val ledPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, resolveSize(width, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val inset = dp(6f)
        rect.set(inset, inset, width - inset, height - inset)
        canvas.drawRoundRect(rect, dp(10f), dp(10f), if (isOn) onPaint else offPaint)

        val cx = width / 2f
        namePaint.color = if (isOn) Palette.BACKGROUND else Palette.TEXT
        val label = TextUtils.ellipsize(name, namePaint, rect.width() - dp(8f), TextUtils.TruncateAt.END).toString()
        canvas.drawText(label, cx, height / 2f - (namePaint.descent() + namePaint.ascent()) / 2, namePaint)

        if (mode == ButtonMode.LATCH) {
            val r = dp(4f)
            ledPaint.color = if (isOn) Palette.TEXT else LED_OFF
            canvas.drawCircle(rect.right - dp(10f), rect.top + dp(10f), r, ledPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                setOn(if (mode == ButtonMode.LATCH) !isOn else true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mode == ButtonMode.MOMENTARY) setOn(false)
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                listener?.onControlReleased(this, value)
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    /** Turns the Button off without notifying the listener; returns whether it was on. */
    fun reset(): Boolean {
        val wasOn = isOn
        isOn = false
        invalidate()
        return wasOn
    }

    private fun setOn(on: Boolean) {
        if (on == isOn) return
        isOn = on
        invalidate()
        listener?.onControlChanged(this, value)
    }

    companion object {
        const val ON = 127
        const val OFF = 0
        private val LED_OFF = android.graphics.Color.rgb(0x5A, 0x60, 0x6D)
    }
}
