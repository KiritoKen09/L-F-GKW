package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.example.data.AppRepository
import com.example.ui.GhanshyamApp
import com.example.ui.theme.MyApplicationTheme
import com.example.util.AppLockManager
import com.example.util.DailyReportScheduler
import com.example.util.DailyReportWorker

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize AppLockManager and DailyReportScheduler
        AppLockManager.init(this)
        DailyReportScheduler.scheduleDailyReport(this)

        val app = application as GhanshyamApplication
        val repository = app.repository

        val openAiReport = intent?.getBooleanExtra(DailyReportWorker.EXTRA_OPEN_AI_REPORT, false) ?: false
        val initialDailyReport = intent?.getStringExtra(DailyReportWorker.EXTRA_DAILY_REPORT_CONTENT)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    GhanshyamApp(
                        repository = repository,
                        initialDailyReportContent = initialDailyReport,
                        openAiOnLaunch = openAiReport
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
