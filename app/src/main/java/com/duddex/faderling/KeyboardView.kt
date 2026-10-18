package com.duddex.faderling

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.SparseIntArray
import android.view.MotionEvent
import android.view.View

/** Piano-style Keyboard of [Keyboard.KEY_COUNT] Keys; every finger plays its own Key and sliding changes Keys. */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onKeyPressed(index: Int)
        fun onKeyReleased(index: Int)
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    /** Pointer id → Key index it is holding. */
    private val pointers = SparseIntArray()
    private val pressCount = IntArray(Keyboard.KEY_COUNT)

    /** Pointers whose Keys were force-released; they are ignored until lifted so they don't re-trigger. */
    private val ignoredPointers = HashSet<Int>()

    private val whiteCount = (0 until Keyboard.KEY_COUNT).count { !Keyboard.isBlack(it) }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xDD, 0xE1, 0xE8) }
    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.TRACK }
    private val blackBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.BACKGROUND }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.ACCENT }
    private val rect = RectF()

    private val whiteWidth get() = width.toFloat() / whiteCount
    private val blackHeight get() = height * 0.6f

    override fun onDraw(canvas: Canvas) {
        val gap = dp(2f)
        val radius = dp(4f)
        for (i in 0 until Keyboard.KEY_COUNT) {
            if (Keyboard.isBlack(i)) continue
            val x = whitePosition(i) * whiteWidth
            rect.set(x + gap / 2, 0f, x + whiteWidth - gap / 2, height.toFloat())
            canvas.drawRoundRect(rect, radius, radius, if (pressCount[i] > 0) pressedPaint else whitePaint)
        }
        for (i in 0 until Keyboard.KEY_COUNT) {
            if (!Keyboard.isBlack(i)) continue
            blackRect(i, rect)
            canvas.drawRoundRect(rect.left - gap, rect.top, rect.right + gap, rect.bottom + gap, radius, radius, blackBorderPaint)
            canvas.drawRoundRect(rect, radius, radius, if (pressCount[i] > 0) pressedPaint else blackPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) ignoredPointers.clear()
                parent?.requestDisallowInterceptTouchEvent(true)
                val i = event.actionIndex
                keyAt(event.getX(i), event.getY(i))?.let { press(event.getPointerId(i), it) }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    if (id in ignoredPointers) continue
                    val current = pointers.get(id, -1)
                    val key = keyAt(event.getX(i), event.getY(i)) ?: -1
                    if (key != current) {
                        if (current >= 0) release(id)
                        if (key >= 0) press(id, key)
                    }
                }
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

    /** Releases every held Key, e.g. when the Page is left or the app goes to the background. */
    fun releaseAll() {
        for (n in pointers.size() - 1 downTo 0) {
            val id = pointers.keyAt(n)
            ignoredPointers += id
            release(id)
        }
    }

    private fun press(pointerId: Int, key: Int) {
        pointers.put(pointerId, key)
        if (pressCount[key]++ == 0) {
            invalidate()
            listener?.onKeyPressed(key)
        }
    }

    private fun release(pointerId: Int) {
        val key = pointers.get(pointerId, -1)
        if (key < 0) return
        pointers.delete(pointerId)
        if (--pressCount[key] == 0) {
            invalidate()
            listener?.onKeyReleased(key)
        }
    }

    private fun keyAt(x: Float, y: Float): Int? {
        if (x < 0 || x >= width || y < 0 || y >= height) return null
        if (y < blackHeight) {
            for (i in 0 until Keyboard.KEY_COUNT) {
                if (Keyboard.isBlack(i)) {
                    blackRect(i, rect)
                    if (rect.contains(x, y)) return i
                }
            }
        }
        val white = (x / whiteWidth).toInt().coerceIn(0, whiteCount - 1)
        return WHITE_SEMITONES[white % 7] + 12 * (white / 7)
    }

    /** Position of a white Key counted in white keys from the left. */
    private fun whitePosition(index: Int): Int = (index / 12) * 7 + WHITE_SEMITONES.indexOf(index % 12)

    private fun blackRect(index: Int, out: RectF) {
        val boundary = (whitePosition(index - 1) + 1) * whiteWidth
        val w = whiteWidth * 0.6f
        out.set(boundary - w / 2, 0f, boundary + w / 2, blackHeight)
    }

    private companion object {
        val WHITE_SEMITONES = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    }
}
