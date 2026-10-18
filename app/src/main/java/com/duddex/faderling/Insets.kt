package com.duddex.faderling

import android.os.Build
import android.view.View
import android.view.WindowInsets

fun View.padForSystemBars() {
    val base = intArrayOf(paddingLeft, paddingTop, paddingRight, paddingBottom)
    setOnApplyWindowInsetsListener { v, insets ->
        if (Build.VERSION.SDK_INT >= 30) {
            val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
            v.setPadding(base[0] + i.left, base[1] + i.top, base[2] + i.right, base[3] + i.bottom)
        } else {
            @Suppress("DEPRECATION")
            v.setPadding(
                base[0] + insets.systemWindowInsetLeft,
                base[1] + insets.systemWindowInsetTop,
                base[2] + insets.systemWindowInsetRight,
                base[3] + insets.systemWindowInsetBottom,
            )
        }
        insets
    }
    requestApplyInsets()
}
