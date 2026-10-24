package com.duddex.faderling

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController

/** Hides status and navigation bar; an edge swipe shows them on top of the app for a moment. */
fun Window.hideSystemBars() {
    if (Build.VERSION.SDK_INT >= 30) {
        insetsController?.let {
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(WindowInsets.Type.systemBars())
        }
    } else {
        @Suppress("DEPRECATION")
        decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN
    }
}

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
