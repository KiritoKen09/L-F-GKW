package com.example.util

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.*
import java.util.concurrent.TimeUnit

object DailyReportScheduler {

    private const val PREFS_NAME = "daily_report_settings"
    private const val KEY_HOUR = "report_hour"
    private const val KEY_MINUTE = "report_minute"
    private const val DEFAULT_HOUR = 20 // 8 PM
    private const val DEFAULT_MINUTE = 30 // 30 mins -> 8:30 PM
    const val UNIQUE_WORK_NAME = "ghanshyam_daily_report_work"

    fun getScheduledHour(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_HOUR, DEFAULT_HOUR)
    }

    fun getScheduledMinute(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_MINUTE, DEFAULT_MINUTE)
    }

    fun updateScheduleTime(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .apply()
        scheduleDailyReport(context)
    }

    fun scheduleDailyReport(context: Context) {
        val hour = getScheduledHour(context)
        val minute = getScheduledMinute(context)

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val initialDelay = target.timeInMillis - now.timeInMillis

        val workRequest = OneTimeWorkRequestBuilder<DailyReportWorker>()
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .addTag(UNIQUE_WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    /**
     * Immediately triggers the DailyReportWorker for testing and instant preview.
     */
    fun triggerNow(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<DailyReportWorker>()
            .addTag("immediate_daily_report")
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
