package com.quvntvn.carlocator.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.quvntvn.carlocator.data.PrefsManager

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }
        if (!PrefsManager(context).isAppEnabled()) {
            return
        }
        Log.d("CarLocator", "Boot completed: scheduling SafetyNet worker.")
        SafetyNetScheduler.schedule(context)
    }
}
