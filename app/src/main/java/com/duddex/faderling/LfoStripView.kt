package com.duddex.faderling

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One LFO on the LFOs Page: on/off button with LED at the top, a bar showing the range (two draggable handles
 * for Min and Max) and the current value, and the Speed at the bottom, changed by dragging up or down.
 */
@SuppressLint("ViewConstructor")
class LfoStripView(context: Context, private val index: Int, private val engine: LfoEngine) : View(context) {

    private enum class Drag { NONE, MIN, MAX, SPEED }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val buttonHeight = dp(34f)
    private val nameHeight = dp(22f)
    private val speedHeight = dp(44f)
    private val handleHeight = dp(8f)

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = dp(14f) }
    private val smallPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT; textSize = dp(11f); color = Palette.TEXT_DIM }
    private val hintPaint = TextPaint(smallPaint).apply { textAlign = Paint.Align.CENTER }
    private val speedHint = context.getString(R.string.lfo_speed)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.TRACK }
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x4A, 0x50, 0x5C) }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.TEXT }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.ACCENT }
    private val buttonOffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.TRACK }
    private val buttonOnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.ACCENT }
    private val ledPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val track = RectF()

    private var drag = Drag.NONE
    private var dragStartY = 0f
    private var dragStartFraction = 0f

    /** Swipe along the on/off buttons that started on this LFO; every LFO reached flips. */
    private var swipe: RowSwipe? = null

    private fun updateTrack() {
        val w = (width * 0.4f).coerceAtMost(dp(48f))
        val cx = width / 2f
        track.set(cx - w / 2, buttonHeight + nameHeight + dp(10f), cx + w / 2, height - speedHeight - dp(6f))
    }

    private fun yOf(value: Int) = track.bottom - track.height() * value / 127f
    private fun valueAt(y: Float) = ((track.bottom - y) / track.height() * 127f).roundToInt().coerceIn(0, 127)

    override fun onDraw(canvas: Canvas) {
        updateTrack()
        val range = engine.range(index)
        val running = engine.isRunning(index)
        val cx = width / 2f
        val inset = dp(4f)

        // On/off button with LED
        rect.set(inset, 0f, width - inset, buttonHeight)
        canvas.drawRoundRect(rect, dp(8f), dp(8f), if (running) buttonOnPaint else buttonOffPaint)
        ledPaint.color = if (running) Palette.TEXT else LED_OFF
        canvas.drawCircle(rect.right - dp(9f), rect.top + dp(9f), dp(4f), ledPaint)
        textPaint.color = if (running) Palette.BACKGROUND else Palette.TEXT
        textPaint.textSize = dp(13f)
        val label = TextUtils.ellipsize(engine.assignments[index].name, textPaint, rect.width() - dp(22f), TextUtils.TruncateAt.END).toString()
        canvas.drawText(label, cx, rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2, textPaint)

        // Current value above the bar
        val value = engine.value(index)
        textPaint.color = Palette.TEXT
        textPaint.textSize = dp(16f)
        canvas.drawText(value?.toString() ?: "–", cx, buttonHeight + nameHeight - dp(2f), textPaint)

        // Track, range band, handles and current value
        canvas.drawRoundRect(track, dp(6f), dp(6f), trackPaint)
        val yMin = yOf(range.min)
        val yMax = yOf(range.max)
        canvas.drawRect(track.left, minOf(yMin, yMax), track.right, maxOf(yMin, yMax), bandPaint)
        if (value != null) {
            val y = yOf(value)
            canvas.drawRect(track.left, y - dp(3f), track.right, y + dp(3f), valuePaint)
        }
        drawHandle(canvas, yMin, range.min, drag == Drag.MIN)
        drawHandle(canvas, yMax, range.max, drag == Drag.MAX)

        // Speed at the bottom
        textPaint.color = if (drag == Drag.SPEED) Palette.ACCENT else Palette.TEXT
        textPaint.textSize = dp(15f)
        canvas.drawText(Lfos.speedLabel(range.speedSeconds), cx, height - speedHeight / 2 + dp(2f), textPaint)
        canvas.drawText(speedHint, cx, height - dp(4f), hintPaint)

        if (running) postInvalidateOnAnimation()
    }

    private fun drawHandle(canvas: Canvas, y: Float, value: Int, active: Boolean) {
        handlePaint.color = if (active) Palette.ACCENT_ACTIVE else Palette.TEXT
        rect.set(track.left - dp(4f), y - handleHeight / 2, track.right + dp(4f), y + handleHeight / 2)
        canvas.drawRoundRect(rect, dp(3f), dp(3f), handlePaint)
        canvas.drawText(value.toString(), track.left - dp(7f), y - (smallPaint.descent() + smallPaint.ascent()) / 2, smallPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        updateTrack()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val y = event.y
                when {
                    y < buttonHeight -> {
                        engine.setRunning(index, !engine.isRunning(index))
                        swipe = RowSwipe(rowIndex())
                    }
                    y > height - speedHeight -> {
                        drag = Drag.SPEED
                        dragStartY = y
                        dragStartFraction = Lfos.fractionOf(engine.range(index).speedSeconds)
                    }
                    else -> {
                        val range = engine.range(index)
                        drag = if (abs(y - yOf(range.min)) <= abs(y - yOf(range.max))) Drag.MIN else Drag.MAX
                        moveHandle(y)
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                swipe?.let { swipeAlong(it, event.x) }
                when (drag) {
                    Drag.MIN, Drag.MAX -> moveHandle(event.y)
                    Drag.SPEED -> {
                        val fraction = dragStartFraction + (dragStartY - event.y) / dp(200f)
                        engine.setRange(index, engine.range(index).copy(speedSeconds = Lfos.speedAt(fraction)), save = false)
                    }
                    Drag.NONE -> {}
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.actionMasked == MotionEvent.ACTION_UP) swipe?.let { swipeAlong(it, event.x) }
                if (drag != Drag.NONE) engine.setRange(index, engine.range(index), save = true)
                drag = Drag.NONE
                swipe = null
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun swipeAlong(swipe: RowSwipe, x: Float) {
        val i = rowIndexAt(x) ?: return
        swipe.reach(i).forEach { reached ->
            (rowSibling(reached) as? LfoStripView)?.let {
                engine.setRunning(it.index, !engine.isRunning(it.index))
                it.invalidate()
            }
        }
    }

    private fun moveHandle(y: Float) {
        val range = engine.range(index)
        val v = valueAt(y)
        engine.setRange(index, if (drag == Drag.MIN) range.copy(min = v) else range.copy(max = v), save = false)
    }

    private companion object {
        val LED_OFF = Color.rgb(0x5A, 0x60, 0x6D)
    }
}
