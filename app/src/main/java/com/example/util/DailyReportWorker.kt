package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import java.text.SimpleDateFormat
import java.util.*

class DailyReportWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val CHANNEL_ID = "ghanshyam_daily_report"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_OPEN_AI_REPORT = "EXTRA_OPEN_AI_REPORT"
        const val EXTRA_DAILY_REPORT_CONTENT = "EXTRA_DAILY_REPORT_CONTENT"
    }

    override suspend fun doWork(): Result {
        return try {
            val database = AppDatabase.getInstance(appContext)

            // Start of today (midnight)
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = cal.timeInMillis

            // 1. Today's Sales
            val allInvoices = database.invoiceDao().getAllWithItemsList()
            val todayInvoices = allInvoices.filter { it.invoice.date >= startOfDay && !it.invoice.isCancelled }
            val todaySales = todayInvoices.sumOf { it.invoice.totalAmount }

            // 2. Collected Today (Cashbook IN)
            val allCash = database.cashbookDao().getAllList()
            val todayCashIn = allCash.filter { it.date >= startOfDay && it.type.equals("IN", ignoreCase = true) }
            val collectedToday = todayCashIn.sumOf { it.amount }

            // 3. Given on Credit Today (Transactions GAVE)
            val allTxs = database.transactionDao().getAllList()
            val todayUdhar = allTxs.filter { it.date >= startOfDay && it.type.equals("GAVE", ignoreCase = true) }
            val givenOnCreditToday = todayUdhar.sumOf { it.amount }

            // 4. Cash in Hand
            val totalIn = allCash.filter { it.type.equals("IN", ignoreCase = true) }.sumOf { it.amount }
            val totalOut = allCash.filter { it.type.equals("OUT", ignoreCase = true) }.sumOf { it.amount }
            val cashInHand = totalIn - totalOut

            // 5. Top 5 Parties to Chase
            val customers = database.customerDao().getAllList()
            val customerBalances = customers.map { customer ->
                val custTxs = allTxs.filter { it.customerId == customer.id && !it.isCompleted }
                val gave = custTxs.filter { it.type == "GAVE" }.sumOf { it.amount }
                val got = custTxs.filter { it.type == "GOT" }.sumOf { it.amount }
                customer to (gave - got)
            }.filter { it.second > 0.0 }
             .sortedByDescending { it.second }
             .take(5)

            // 6. Low-Stock items
            val inventory = database.inventoryDao().getAllList()
            val lowStockItems = inventory.filter { it.stock < it.lowStockThreshold }

            // Format Report
            val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
            val sb = StringBuilder()
            sb.append("📊 GHANSHYAM KIDS WEAR - DAILY REPORT\n")
            sb.append("📅 Date: $dateStr\n")
            sb.append("────────────────────────\n")
            sb.append("💰 Today's Sales: ₹${"%.2f".format(todaySales)}\n")
            sb.append("📥 Collected Today: ₹${"%.2f".format(collectedToday)}\n")
            sb.append("📤 Given on Credit: ₹${"%.2f".format(givenOnCreditToday)}\n")
            sb.append("💵 Cash in Hand: ₹${"%.2f".format(cashInHand)}\n")
            sb.append("────────────────────────\n")

            if (customerBalances.isNotEmpty()) {
                sb.append("⚠️ Top Parties to Chase:\n")
                customerBalances.forEachIndexed { idx, (c, bal) ->
                    val phonePart = if (c.phone.isNotBlank()) " (${c.phone})" else ""
                    sb.append("${idx + 1}. ${c.name}: ₹${"%.2f".format(bal)}$phonePart\n")
                }
            } else {
                sb.append("✅ No outstanding receivables to chase!\n")
            }

            sb.append("────────────────────────\n")
            if (lowStockItems.isNotEmpty()) {
                sb.append("📦 Low Stock Alerts (${lowStockItems.size} items):\n")
                lowStockItems.take(5).forEach { item ->
                    sb.append("• ${item.name}: ${item.stock} left (alert at ${item.lowStockThreshold})\n")
                }
            } else {
                sb.append("✅ Stock levels healthy\n")
            }

            val reportContent = sb.toString()

            // Post Notification
            postReportNotification(reportContent, todaySales, cashInHand)

            // Reschedule next run for tomorrow
            DailyReportScheduler.scheduleDailyReport(appContext)

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("DailyReportWorker", "Error calculating daily report: ${e.message}", e)
            Result.retry()
        }
    }

    private fun postReportNotification(reportText: String, sales: Double, cash: Double) {
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Daily Business Report",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily evening business summaries for sales, cashbook, and udhar"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_AI_REPORT, true)
            putExtra(EXTRA_DAILY_REPORT_CONTENT, reportText)
        }

        val pendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Ghanshyam Kids Wear - Aaj ka Hisaab 📊")
            .setContentText("Sales: ₹${"%.0f".format(sales)} | Cash: ₹${"%.0f".format(cash)} • Tap to view")
            .setStyle(NotificationCompat.BigTextStyle().bigText(reportText))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
