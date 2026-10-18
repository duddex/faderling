package com.duddex.faderling

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.util.SparseIntArray
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * MPC-style 4×4 grid of square Pads, centred in the view. Every touch on a Pad triggers it (also while
 * another finger holds it); a Pad is released when its last finger lifts. Sliding onto other Pads does nothing.
 */
class PadGridView(context: Context) : View(context) {

    interface Listener {
        fun onPadPressed(index: Int)
        fun onPadReleased(index: Int)
    }

    var listener: Listener? = null

    var names: List<String> = emptyList()
        set(v) { field = v; invalidate() }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    /** Pointer id → Pad index it is holding. */
    private val pointers = SparseIntArray()
    private val pressCount = IntArray(Pads.COUNT)
    private val ignoredPointers = HashSet<Int>()

    private val padPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.TRACK }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.ACCENT }
    private val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(15f)
    }
    private val rect = RectF()

    private val cell get() = min(width.toFloat() / Pads.COLUMNS, height.toFloat() / Pads.ROWS)
    private val left get() = (width - cell * Pads.COLUMNS) / 2
    private val top get() = (height - cell * Pads.ROWS) / 2

    override fun onDraw(canvas: Canvas) {
        val gap = dp(6f)
        for (row in 0 until Pads.ROWS) {
            for (column in 0 until Pads.COLUMNS) {
                val index = Pads.indexAt(row, column)
                rect.set(left + column * cell + gap / 2, top + row * cell + gap / 2, left + (column + 1) * cell - gap / 2, top + (row + 1) * cell - gap / 2)
                val pressed = pressCount[index] > 0
                canvas.drawRoundRect(rect, dp(10f), dp(10f), if (pressed) pressedPaint else padPaint)
                namePaint.color = if (pressed) Palette.BACKGROUND else Palette.TEXT
                val name = names.getOrNull(index).orEmpty()
                val label = TextUtils.ellipsize(name, namePaint, rect.width() - dp(8f), TextUtils.TruncateAt.END).toString()
                canvas.drawText(label, rect.centerX(), rect.centerY() - (namePaint.descent() + namePaint.ascent()) / 2, namePaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) ignoredPointers.clear()
                parent?.requestDisallowInterceptTouchEvent(true)
                val i = event.actionIndex
                padAt(event.getX(i), event.getY(i))?.let { press(event.getPointerId(i), it) }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val id = event.getPointerId(event.actionIndex)
                ignoredPointers.remove(id)
                release(id)
            }
            MotionEvent.ACTION_UP -> {
                ignoredPointers.clear()
                release(event.getPointerId(event.actionIndex))
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                releaseAll()
                ignoredPointers.clear()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    /** Releases every held Pad, e.g. when the Page is left or the app goes to the background. */
    fun releaseAll() {
        for (n in pointers.size() - 1 downTo 0) {
            val id = pointers.keyAt(n)
            ignoredPointers += id
            release(id)
        }
    }

    private fun press(pointerId: Int, pad: Int) {
        pointers.put(pointerId, pad)
        pressCount[pad]++
        invalidate()
        listener?.onPadPressed(pad)
    }

    private fun release(pointerId: Int) {
        val pad = pointers.get(pointerId, -1)
        if (pad < 0) return
        pointers.delete(pointerId)
        if (--pressCount[pad] == 0) {
            invalidate()
            listener?.onPadReleased(pad)
        }
    }

    private fun padAt(x: Float, y: Float): Int? {
        val column = ((x - left) / cell).toInt()
        val row = ((y - top) / cell).toInt()
        if (x < left || y < top || column !in 0 until Pads.COLUMNS || row !in 0 until Pads.ROWS) return null
        return Pads.indexAt(row, column)
    }
}
