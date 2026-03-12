package com.orgzly.android.wear

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MidnightWearRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        PhoneWearService.pushTaskCounts(applicationContext)
        schedule(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "midnight_wear_refresh"

        @JvmStatic
        fun schedule(context: Context) {
            val now = Calendar.getInstance()
            val midnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 15)
                set(Calendar.MILLISECOND, 0)
            }
            val delayMillis = midnight.timeInMillis - now.timeInMillis

            val request = OneTimeWorkRequestBuilder<MidnightWearRefreshWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        @JvmStatic
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
