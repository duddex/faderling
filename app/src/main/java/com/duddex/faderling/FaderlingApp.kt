package com.duddex.faderling

import android.app.Activity
import android.app.Application
import android.os.Bundle

/** Keeps the RTP-MIDI endpoint and the LFOs alive while any of the app's screens is visible. */
class FaderlingApp : Application(), Application.ActivityLifecycleCallbacks {

    lateinit var endpoint: RtpMidiEndpoint
        private set

    lateinit var lfos: LfoEngine
        private set

    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        endpoint = RtpMidiEndpoint(this, AppleMidi.serviceName(getString(R.string.app_name), android.os.Build.MODEL))
        lfos = LfoEngine(AssignmentStore(this), endpoint)
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        if (startedActivities++ == 0) {
            endpoint.start()
            lfos.start()
        }
    }

    override fun onActivityStopped(activity: Activity) {
        if (--startedActivities == 0) {
            lfos.stop()
            endpoint.stop()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
