package com.duddex.faderling

import android.app.Activity
import android.app.Application
import android.os.Bundle

/** Keeps the RTP-MIDI endpoint alive while any of the app's screens is visible. */
class FaderlingApp : Application(), Application.ActivityLifecycleCallbacks {

    lateinit var endpoint: RtpMidiEndpoint
        private set

    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        endpoint = RtpMidiEndpoint(this, getString(R.string.app_name))
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        if (startedActivities++ == 0) endpoint.start()
    }

    override fun onActivityStopped(activity: Activity) {
        if (--startedActivities == 0) endpoint.stop()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
