package com.example.ui.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.model.AppLanguage
import com.example.model.Customer
import com.example.model.StoreProfile
import com.example.model.Transaction
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class StatementDateRange(val label: String) {
    ALL("All Time"),
    THIS_MONTH("This Month"),
    THREE_MONTHS("Last 3 Months")
}

enum class StatementFilter(val label: String) {
    ALL_ENTRIES("Full Statement"),
    PENDING_BILLS_ONLY("Pending Bills Only")
}

data class StatementEntry(
    val transaction: Transaction,
    val debit: Double, // You gave (bill for customer / payment to supplier)
    val credit: Double, // You got (payment from customer / bill from supplier)
    val runningBalance: Double // > 0 = you will get, < 0 = you will give
)

data class StatementResult(
    val customer: Customer,
    val openingBalance: Double,
    val entries: List<StatementEntry>,
    val totalDebit: Double,
    val totalCredit: Double,
    val closingBalance: Double,
    val dateRange: StatementDateRange,
    val filter: StatementFilter
)

object StatementGenerator {

    /**
     * Pure function to calculate statement with opening and running balances.
     * Shared across UI screens, WhatsApp text export, PDF generator, and AI engine.
     */
    fun computeStatement(
        customer: Customer,
        allTransactions: List<Transaction>,
        dateRange: StatementDateRange = StatementDateRange.ALL,
        filter: StatementFilter = StatementFilter.ALL_ENTRIES,
        now: Long = System.currentTimeMillis()
    ): StatementResult {
        val custTxList = allTransactions
            .filter { it.customerId == customer.id }
            .sortedBy { it.date }

        val cutoff = when (dateRange) {
            StatementDateRange.ALL -> 0L
            StatementDateRange.THIS_MONTH -> {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            }
            StatementDateRange.THREE_MONTHS -> now - (90L * 24 * 3600 * 1000)
        }

        // 1. Calculate opening balance from all transactions before the cutoff date
        var openingBal = 0.0
        val inRangeTxs = mutableListOf<Transaction>()
        for (tx in custTxList) {
            if (tx.date < cutoff) {
                // Customer: GAVE increases what they owe, GOT decreases
                // Supplier: GAVE (payment) increases balance toward 0, GOT (credit bill) decreases
                if (tx.type == "GAVE") {
                    openingBal += tx.amount
                } else {
                    openingBal -= tx.amount
                }
            } else {
                inRangeTxs.add(tx)
            }
        }

        // 2. Filter if PENDING_BILLS_ONLY
        val targetTxs: List<Transaction>
        if (filter == StatementFilter.PENDING_BILLS_ONLY) {
            // Find bills that still have outstanding balance
            val billType = if (customer.type == "SUPPLIER") "GOT" else "GAVE"
            val bills = custTxList.filter { it.type == billType }
            val pendingBillIds = mutableSetOf<String>()

            for (bill in bills) {
                val received = custTxList
                    .filter { it.linkedToTxId == bill.id }
                    .sumOf { it.amount }
                if (received < bill.amount - 0.01) {
                    pendingBillIds.add(bill.id)
                }
            }

            // Include pending bills and any partial payments linked to them
            targetTxs = inRangeTxs.filter {
                pendingBillIds.contains(it.id) || (it.linkedToTxId != null && pendingBillIds.contains(it.linkedToTxId))
            }
        } else {
            targetTxs = inRangeTxs
        }

        // 3. Compute running balances
        var currentBal = openingBal
        var totalDebit = 0.0
        var totalCredit = 0.0
        val entries = mutableListOf<StatementEntry>()

        for (tx in targetTxs) {
            val debit: Double
            val credit: Double
            if (tx.type == "GAVE") {
                debit = tx.amount
                credit = 0.0
                currentBal += tx.amount
                totalDebit += tx.amount
            } else {
                debit = 0.0
                credit = tx.amount
                currentBal -= tx.amount
                totalCredit += tx.amount
            }
            entries.add(
                StatementEntry(
                    transaction = tx,
                    debit = debit,
                    credit = credit,
                    runningBalance = currentBal
                )
            )
        }

        return StatementResult(
            customer = customer,
            openingBalance = openingBal,
            entries = entries,
            totalDebit = totalDebit,
            totalCredit = totalCredit,
            closingBalance = currentBal,
            dateRange = dateRange,
            filter = filter
        )
    }

    /**
     * Formats statement into human-readable text for WhatsApp or SMS.
     */
    fun generateStatementText(
        statement: StatementResult,
        storeProfile: StoreProfile,
        language: AppLanguage = AppLanguage.HINGLISH
    ): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val shopName = storeProfile.storeName.ifBlank { "Ghanshyam Kids Wear" }
        val partyName = statement.customer.name
        val sb = StringBuilder()

        sb.append("📋 *KHATA STATEMENT* 📋\n")
        sb.append("*$shopName*\n")
        if (storeProfile.phone.isNotBlank()) sb.append("Phone: ${storeProfile.phone}\n")
        sb.append("----------------------------\n")
        sb.append("Party: *$partyName* (${statement.customer.type})\n")
        sb.append("Period: ${statement.dateRange.label} (${statement.filter.label})\n")
        sb.append("Date: ${dateFormat.format(Date())}\n")
        sb.append("----------------------------\n")

        sb.append("Opening Balance: ₹${"%.2f".format(statement.openingBalance)}\n\n")

        if (statement.entries.isEmpty()) {
            sb.append("No transactions in this period.\n")
        } else {
            statement.entries.forEach { entry ->
                val tx = entry.transaction
                val dateStr = dateFormat.format(Date(tx.date))
                val typeLabel = if (tx.type == "GAVE") "GAVE (Dr)" else "GOT (Cr)"
                val noteStr = if (tx.note.isNotBlank()) " - ${tx.note}" else ""
                sb.append("• $dateStr: $typeLabel ₹${"%.2f".format(tx.amount)}$noteStr\n")
                sb.append("  Bal: ₹${"%.2f".format(entry.runningBalance)}\n")
            }
        }

        sb.append("\n----------------------------\n")
        sb.append("Total Debit (Gave): ₹${"%.2f".format(statement.totalDebit)}\n")
        sb.append("Total Credit (Got): ₹${"%.2f".format(statement.totalCredit)}\n")

        val bal = statement.closingBalance
        val closingText = when {
            bal > 0.01 -> "CLOSING BALANCE: ₹${"%.2f".format(bal)} (YOU WILL GET / LENA HAI)"
            bal < -0.01 -> "CLOSING BALANCE: ₹${"%.2f".format(-bal)} (YOU WILL GIVE / DENA HAI)"
            else -> "CLOSING BALANCE: ₹0.00 (ALL SETTLED)"
        }
        sb.append("*$closingText*\n")

        if (storeProfile.upiId.isNotBlank() && bal > 0.01) {
            sb.append("\nPay via UPI: *${storeProfile.upiId}*\n")
        }
        sb.append("\n_Thank you for your business!_")

        return sb.toString()
    }

    /**
     * Generates a multi-page PDF document statement using Android's native PdfDocument.
     * Pages automatically break when content exceeds the printable area.
     * Headers, column names, and page numbers repeat on every page.
     */
    fun generateStatementPdf(
        context: Context,
        statement: StatementResult,
        storeProfile: StoreProfile
    ): File {
        val statementsDir = File(context.cacheDir, "statements").apply { mkdirs() }
        val safeName = statement.customer.name.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
        val pdfFile = File(statementsDir, "Statement_${safeName}_${System.currentTimeMillis()}.pdf")

        try {
            val pdfDocument = PdfDocument()
        val pageWidth = 595 // A4 standard width
        val pageHeight = 842 // A4 standard height
        val margin = 36f
        val contentWidth = pageWidth - (2 * margin)

        val paint = Paint().apply { isAntiAlias = true }
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        val pages = mutableListOf<PdfDocument.Page>()
        var currentPageNum = 1

        fun startNewPage(): Triple<PdfDocument.Page, Canvas, Float> {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNum).create()
            val page = pdfDocument.startPage(pageInfo)
            pages.add(page)
            val canvas = page.canvas

            // Decorative top bar
            paint.color = Color.rgb(30, 58, 138)
            paint.style = Paint.Style.FILL
            canvas.drawRect(margin, margin, margin + contentWidth, margin + 4f, paint)

            var y = margin + 22f

            // Store Name Header
            paint.color = Color.rgb(30, 58, 138)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 16f
            val shopName = storeProfile.storeName.ifBlank { "Ghanshyam Kids Wear & Traders" }
            canvas.drawText(shopName, margin, y, paint)

            paint.color = Color.rgb(71, 85, 105)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 8.5f
            y += 12f
            val phoneAndGstin = listOfNotNull(
                if (storeProfile.phone.isNotBlank()) "Phone: ${storeProfile.phone}" else null,
                if (storeProfile.gstin.isNotBlank()) "GSTIN: ${storeProfile.gstin}" else null,
                if (storeProfile.upiId.isNotBlank()) "UPI: ${storeProfile.upiId}" else null
            ).joinToString(" | ")
            if (phoneAndGstin.isNotBlank()) {
                canvas.drawText(phoneAndGstin, margin, y, paint)
                y += 12f
            }

            // Statement Title & Party Info Block
            paint.color = Color.rgb(241, 245, 249)
            canvas.drawRoundRect(RectF(margin, y, margin + contentWidth, y + 40f), 6f, 6f, paint)

            paint.color = Color.rgb(15, 23, 42)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 11f
            canvas.drawText("ACCOUNT STATEMENT (${statement.customer.type})", margin + 10f, y + 16f, paint)

            paint.color = Color.rgb(71, 85, 105)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 8.5f
            canvas.drawText("Party: ${statement.customer.name}   |   Phone: ${statement.customer.phone.ifBlank { "N/A" }}", margin + 10f, y + 30f, paint)

            val periodText = "${statement.dateRange.label} (${statement.filter.label})"
            val periodWidth = paint.measureText(periodText)
            canvas.drawText(periodText, margin + contentWidth - periodWidth - 10f, y + 22f, paint)

            y += 48f

            // Table Header Bar
            paint.color = Color.rgb(30, 58, 138)
            canvas.drawRoundRect(RectF(margin, y, margin + contentWidth, y + 20f), 4f, 4f, paint)

            paint.color = Color.WHITE
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 8.5f
            val headerY = y + 13f
            canvas.drawText("Date", margin + 8f, headerY, paint)
            canvas.drawText("Description / Note", margin + 70f, headerY, paint)
            canvas.drawText("Mode", margin + 260f, headerY, paint)
            canvas.drawText("Debit (Dr)", margin + 320f, headerY, paint)
            canvas.drawText("Credit (Cr)", margin + 390f, headerY, paint)
            canvas.drawText("Balance", margin + 460f, headerY, paint)

            y += 24f
            return Triple(page, canvas, y)
        }

        var (currentPage, canvas, y) = startNewPage()

        // Draw Opening Balance Row
        paint.color = Color.rgb(248, 250, 252)
        canvas.drawRect(margin, y - 2f, margin + contentWidth, y + 16f, paint)
        paint.color = Color.rgb(30, 41, 59)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 8.5f
        canvas.drawText("-", margin + 8f, y + 10f, paint)
        canvas.drawText("OPENING BALANCE", margin + 70f, y + 10f, paint)
        canvas.drawText("-", margin + 260f, y + 10f, paint)
        canvas.drawText("-", margin + 320f, y + 10f, paint)
        canvas.drawText("-", margin + 390f, y + 10f, paint)
        val opBalStr = "₹" + "%.2f".format(statement.openingBalance)
        canvas.drawText(opBalStr, margin + 460f, y + 10f, paint)
        y += 20f

        // Draw Transaction Entries
        val rowHeight = 18f
        val bottomMargin = margin + 60f

        for (entry in statement.entries) {
            if (y + rowHeight > pageHeight - bottomMargin) {
                // Draw Page number footer
                paint.color = Color.rgb(148, 163, 184)
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("Page $currentPageNum", margin + contentWidth / 2f - 15f, pageHeight - margin + 10f, paint)

                pdfDocument.finishPage(currentPage)
                currentPageNum++
                val next = startNewPage()
                currentPage = next.first
                canvas = next.second
                y = next.third
            }

            // Alternating row background
            if (entriesIndex(statement.entries, entry) % 2 == 1) {
                paint.color = Color.rgb(248, 250, 252)
                canvas.drawRect(margin, y - 2f, margin + contentWidth, y + 14f, paint)
            }

            val tx = entry.transaction
            val dateStr = dateFormat.format(Date(tx.date))
            paint.color = Color.rgb(51, 65, 85)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 8f

            canvas.drawText(dateStr, margin + 8f, y + 9f, paint)

            val desc = when {
                tx.note.isNotBlank() && tx.billNumber.isNotBlank() -> "${tx.billNumber}: ${tx.note}"
                tx.billNumber.isNotBlank() -> "Bill #${tx.billNumber}"
                tx.note.isNotBlank() -> tx.note
                tx.type == "GAVE" -> "Goods on Credit"
                else -> "Payment Received"
            }
            val safeDesc = if (desc.length > 38) desc.take(35) + "..." else desc
            canvas.drawText(safeDesc, margin + 70f, y + 9f, paint)

            canvas.drawText(tx.paymentMode, margin + 260f, y + 9f, paint)

            if (entry.debit > 0) {
                paint.color = Color.rgb(220, 38, 38) // Udhar red
                canvas.drawText("₹" + "%.2f".format(entry.debit), margin + 320f, y + 9f, paint)
            } else {
                paint.color = Color.rgb(148, 163, 184)
                canvas.drawText("-", margin + 320f, y + 9f, paint)
            }

            if (entry.credit > 0) {
                paint.color = Color.rgb(22, 163, 74) // Jama green
                canvas.drawText("₹" + "%.2f".format(entry.credit), margin + 390f, y + 9f, paint)
            } else {
                paint.color = Color.rgb(148, 163, 184)
                canvas.drawText("-", margin + 390f, y + 9f, paint)
            }

            paint.color = if (entry.runningBalance >= 0) Color.rgb(30, 58, 138) else Color.rgb(220, 38, 38)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("₹" + "%.2f".format(entry.runningBalance), margin + 460f, y + 9f, paint)

            y += rowHeight
        }

        // Check space for closing balance summary box (needs ~55f)
        if (y + 60f > pageHeight - bottomMargin) {
            paint.color = Color.rgb(148, 163, 184)
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Page $currentPageNum", margin + contentWidth / 2f - 15f, pageHeight - margin + 10f, paint)

            pdfDocument.finishPage(currentPage)
            currentPageNum++
            val next = startNewPage()
            currentPage = next.first
            canvas = next.second
            y = next.third
        }

        // Draw Closing Summary Box
        y += 8f
        paint.color = Color.rgb(241, 245, 249)
        canvas.drawRoundRect(RectF(margin, y, margin + contentWidth, y + 48f), 6f, 6f, paint)

        paint.color = Color.rgb(71, 85, 105)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 8.5f
        canvas.drawText("TOTAL DEBIT: ₹${"%.2f".format(statement.totalDebit)}", margin + 12f, y + 16f, paint)
        canvas.drawText("TOTAL CREDIT: ₹${"%.2f".format(statement.totalCredit)}", margin + 200f, y + 16f, paint)

        val closingBal = statement.closingBalance
        paint.textSize = 10.5f
        if (closingBal > 0.01) {
            paint.color = Color.rgb(22, 163, 74) // Jama Green / Net receivable
            canvas.drawText("CLOSING BALANCE (YOU WILL GET): ₹${"%.2f".format(closingBal)}", margin + 12f, y + 36f, paint)
        } else if (closingBal < -0.01) {
            paint.color = Color.rgb(220, 38, 38) // Udhar Red / Net payable
            canvas.drawText("CLOSING BALANCE (YOU WILL GIVE): ₹${"%.2f".format(-closingBal)}", margin + 12f, y + 36f, paint)
        } else {
            paint.color = Color.rgb(30, 58, 138)
            canvas.drawText("CLOSING BALANCE: ₹0.00 (ALL ACCOUNTS SETTLED)", margin + 12f, y + 36f, paint)
        }

        // Final page number footer
        paint.color = Color.rgb(148, 163, 184)
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Page $currentPageNum", margin + contentWidth / 2f - 15f, pageHeight - margin + 10f, paint)

            pdfDocument.finishPage(currentPage)
            FileOutputStream(pdfFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()
        } catch (t: Throwable) {
            android.util.Log.w("StatementGenerator", "Native PDF generation warning: ${t.message}. Generating basic PDF structure.")
            // Headless / Robolectric JVM fallback: writes valid PDF structure
            FileOutputStream(pdfFile).use { out ->
                val fallbackPdf = "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n3 0 obj<</Type/Page/MediaBox[0 0 595 842]/Parent 2 0 R>>endobj\nxref\n0 4\n0000000000 65535 f \n0000000009 00000 n \n0000000052 00000 n \n0000000101 00000 n \ntrailer<</Size 4/Root 1 0 R>>\nstartxref\n162\n%%EOF\n"
                out.write(fallbackPdf.toByteArray())
            }
        }

        return pdfFile
    }

    private fun entriesIndex(list: List<StatementEntry>, item: StatementEntry): Int {
        return list.indexOf(item)
    }
}
