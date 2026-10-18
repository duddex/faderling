package com.duddex.faderling

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(TextView(this).apply { text = getString(R.string.app_name) })
    }
}
