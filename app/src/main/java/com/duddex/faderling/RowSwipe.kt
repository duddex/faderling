package com.duddex.faderling

import android.view.View
import android.view.ViewGroup

/**
 * One finger sliding along a row of strips (Faders or LFOs) after touching down in the row's label or button area.
 * Each strip is reached at most once per swipe, including the one where the swipe started.
 */
class RowSwipe(start: Int) {
    private val reached = mutableSetOf(start)
    private var last = start

    /**
     * Moves the swipe to [index] and returns the strips reached for the first time, in swipe order.
     * Strips the finger skipped between two touch events count as reached too.
     */
    fun reach(index: Int): List<Int> {
        val passed = if (index >= last) last..index else last downTo index
        last = index
        return passed.filter { reached.add(it) }
    }

    companion object {
        /**
         * Index of the strip whose horizontal span [lefts]..[rights] (exclusive) contains [x]; null in a gap between
         * strips. Beyond either end of the row the outermost strip counts, so a fast swipe off the row still reaches it.
         */
        fun indexAt(x: Float, lefts: List<Int>, rights: List<Int>): Int? = when {
            lefts.isEmpty() -> null
            x < lefts.first() -> 0
            x >= rights.last() -> lefts.lastIndex
            else -> lefts.indices.firstOrNull { x >= lefts[it] && x < rights[it] }
        }
    }
}

/** Position in the parent row of the sibling under [x] (in this view's coordinates), or null. */
fun View.rowIndexAt(x: Float): Int? {
    val row = parent as? ViewGroup ?: return null
    val children = (0 until row.childCount).map(row::getChildAt)
    return RowSwipe.indexAt(left + x, children.map { it.left }, children.map { it.right })
}

fun View.rowIndex(): Int = (parent as? ViewGroup)?.indexOfChild(this) ?: 0

fun View.rowSibling(index: Int): View? = (parent as? ViewGroup)?.getChildAt(index)
