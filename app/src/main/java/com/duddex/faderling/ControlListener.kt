package com.duddex.faderling

import android.view.View

/** Receives value changes from any Control view. */
interface ControlListener {
    fun onControlChanged(control: View, value: Int)
    fun onControlReleased(control: View, value: Int)
}
