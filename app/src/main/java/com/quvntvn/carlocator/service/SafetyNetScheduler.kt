package com.quvntvn.carlocator.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SafetyNetScheduler {
    private const val WORK_NAME = "CarLocatorSafetyNet"

    fun schedule(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<SafetyNetWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
