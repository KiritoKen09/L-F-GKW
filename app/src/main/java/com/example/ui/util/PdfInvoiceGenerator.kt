package com.example.ui.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.model.Invoice
import com.example.model.InvoiceItem
import com.example.model.StoreProfile
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfInvoiceGenerator {

    /**
     * Generates a native multi-page PDF document for the given invoice and store profile,
     * supporting Tax Invoice / Bill of Supply, CGST/SGST vs IGST split, tax summary breakdown,
     * round-off, amount in words, repeating headers, page numbering, and CANCELLED watermark.
     */
    fun generateInvoicePdf(
        context: Context,
        storeProfile: StoreProfile,
        invoice: Invoice
    ): File {
        val invoicesDir = File(context.cacheDir, "invoices").apply { mkdirs() }
        val safeBillNum = invoice.billNumber.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
        val pdfFile = File(invoicesDir, "Invoice_$safeBillNum.pdf")

        try {
            val pdfDocument = PdfDocument()

            // Standard A4 dimensions in PostScript points: 595 x 842
            val pageWidth = 595
            val pageHeight = 842
            val margin = 36f
            val contentWidth = pageWidth - (2 * margin)

            val paint = Paint().apply { isAntiAlias = true }

            val isTaxInvoice = storeProfile.gstin.isNotBlank()
            val docTitle = if (isTaxInvoice) "TAX INVOICE" else "BILL OF SUPPLY"

            // Chunk items for multi-page support
            // First page can hold up to 10 items due to store header and customer box
            // Subsequent pages can hold up to 20 items
            // Final page also needs space for tax summary table and totals (~250pt)
            val items = invoice.items
            val pagesItems = mutableListOf<List<InvoiceItem>>()

            if (items.isEmpty()) {
                pagesItems.add(emptyList())
            } else {
                var currentIndex = 0
                var isFirst = true
                while (currentIndex < items.size) {
                    val remaining = items.size - currentIndex
                    val capacity = if (isFirst) {
                        if (remaining <= 8) remaining else 8
                    } else {
                        if (remaining <= 14) remaining else 14
                    }
                    val chunk = items.subList(currentIndex, currentIndex + capacity)
                    pagesItems.add(chunk)
                    currentIndex += capacity
                    isFirst = false
                }
            }

            val totalPages = pagesItems.size

            // Try loading store logo if configured
            var logoBitmap: Bitmap? = null
            if (!storeProfile.logoUri.isNullOrBlank()) {
                try {
                    val uri = Uri.parse(storeProfile.logoUri)
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        logoBitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream.close()
                    }
                } catch (_: Exception) {
                    // Ignore logo load failure gracefully
                }
            }

            for (pageIndex in 0 until totalPages) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = page.canvas

                val isFirstPage = pageIndex == 0
                val isLastPage = pageIndex == totalPages - 1
                val currentItems = pagesItems[pageIndex]

                // 1. Top Decorative Brand Bar
                paint.color = if (invoice.isCancelled) Color.rgb(220, 38, 38) else Color.rgb(30, 58, 138)
                paint.style = Paint.Style.FILL
                canvas.drawRect(margin, margin, margin + contentWidth, margin + 5f, paint)

                var y = margin + 26f

                // 2. Header Section
                if (logoBitmap != null && isFirstPage) {
                    try {
                        val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, 64, 64, true)
                        canvas.drawBitmap(scaledLogo, margin, y - 4f, paint)
                    } catch (_: Exception) {}
                }

                val headerTextX = if (logoBitmap != null && isFirstPage) margin + 74f else margin

                paint.color = Color.rgb(30, 58, 138)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 18f
                val displayStoreName = if (storeProfile.storeName.isNotBlank()) storeProfile.storeName else "Ghanshyam Kids Wear & Traders"
                canvas.drawText(displayStoreName, headerTextX, y, paint)

                paint.color = Color.rgb(100, 116, 139)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textSize = 9f
                y += 14f
                canvas.drawText("Exclusive Kids Wear Wholesale Store • Surat, Gujarat", headerTextX, y, paint)

                if (isFirstPage) {
                    y += 13f
                    if (storeProfile.address.isNotBlank()) {
                        val addr = if (storeProfile.state.isNotBlank()) "${storeProfile.address}, ${storeProfile.state}" else storeProfile.address
                        canvas.drawText("Address: $addr", margin, y, paint)
                        y += 13f
                    }

                    val contactParts = mutableListOf<String>()
                    if (storeProfile.phone.isNotBlank()) contactParts.add("Phone: ${storeProfile.phone}")
                    if (storeProfile.ownerName.isNotBlank()) contactParts.add("Prop: ${storeProfile.ownerName}")
                    if (contactParts.isNotEmpty()) {
                        canvas.drawText(contactParts.joinToString(" | "), margin, y, paint)
                        y += 13f
                    }

                    val taxParts = mutableListOf<String>()
                    if (storeProfile.gstin.isNotBlank()) taxParts.add("Shop GSTIN: ${storeProfile.gstin}")
                    if (storeProfile.upiId.isNotBlank()) taxParts.add("UPI: ${storeProfile.upiId}")
                    if (taxParts.isNotEmpty()) {
                        canvas.drawText(taxParts.joinToString(" | "), margin, y, paint)
                    }
                }

                // Document Title Badge (Top Right)
                val badgeWidth = 150f
                val badgeHeight = 28f
                val badgeX = margin + contentWidth - badgeWidth
                val badgeY = margin + 12f

                paint.color = if (invoice.isCancelled) Color.rgb(254, 242, 242) else Color.rgb(239, 246, 255)
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(RectF(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight), 6f, 6f, paint)

                paint.color = if (invoice.isCancelled) Color.rgb(220, 38, 38) else Color.rgb(37, 99, 235)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.2f
                canvas.drawRoundRect(RectF(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight), 6f, 6f, paint)

                paint.style = Paint.Style.FILL
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 10.5f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(if (invoice.isCancelled) "CANCELLED BILL" else docTitle, badgeX + (badgeWidth / 2f), badgeY + 18f, paint)
                paint.textAlign = Paint.Align.LEFT

                // CANCELLED watermark banner across top if cancelled
                if (invoice.isCancelled) {
                    paint.color = Color.rgb(220, 38, 38)
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 9f
                    canvas.drawText("REASON: ${invoice.cancelReason ?: "Cancelled"}", badgeX, badgeY + badgeHeight + 12f, paint)
                }

                // Divider line
                y += 16f
                paint.color = Color.rgb(226, 232, 240)
                paint.strokeWidth = 1f
                canvas.drawLine(margin, y, margin + contentWidth, y, paint)

                // 3. Metadata boxes on First Page
                if (isFirstPage) {
                    y += 10f
                    val boxHeight = 72f
                    val colWidth = (contentWidth - 10f) / 2f

                    // Left Box: Invoice Details
                    val leftBoxRect = RectF(margin, y, margin + colWidth, y + boxHeight)
                    paint.color = Color.rgb(248, 250, 252)
                    paint.style = Paint.Style.FILL
                    canvas.drawRoundRect(leftBoxRect, 6f, 6f, paint)
                    paint.color = Color.rgb(226, 232, 240)
                    paint.style = Paint.Style.STROKE
                    canvas.drawRoundRect(leftBoxRect, 6f, 6f, paint)

                    paint.style = Paint.Style.FILL
                    paint.color = Color.rgb(15, 23, 42)
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 9.5f
                    canvas.drawText("INVOICE DETAILS", margin + 10f, y + 16f, paint)

                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    paint.color = Color.rgb(51, 65, 85)
                    paint.textSize = 8.5f
                    canvas.drawText("Invoice Series: ${invoice.billNumber}", margin + 10f, y + 32f, paint)
                    val dateFormatted = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(invoice.date))
                    canvas.drawText("Date: $dateFormatted", margin + 10f, y + 46f, paint)
                    val supplyType = if (invoice.isInterState) "Inter-State (IGST)" else "Intra-State (CGST+SGST)"
                    canvas.drawText("Mode: ${invoice.paymentMode} | Supply: ${invoice.stateOfSupply} ($supplyType)", margin + 10f, y + 60f, paint)

                    // Right Box: Customer Details
                    val rightBoxX = margin + colWidth + 10f
                    val rightBoxRect = RectF(rightBoxX, y, rightBoxX + colWidth, y + boxHeight)
                    paint.color = Color.rgb(248, 250, 252)
                    paint.style = Paint.Style.FILL
                    canvas.drawRoundRect(rightBoxRect, 6f, 6f, paint)
                    paint.color = Color.rgb(226, 232, 240)
                    paint.style = Paint.Style.STROKE
                    canvas.drawRoundRect(rightBoxRect, 6f, 6f, paint)

                    paint.style = Paint.Style.FILL
                    paint.color = Color.rgb(15, 23, 42)
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 9.5f
                    canvas.drawText("BILLED TO / PARTY", rightBoxX + 10f, y + 16f, paint)

                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    paint.color = Color.rgb(51, 65, 85)
                    paint.textSize = 8.5f
                    val partyName = if (invoice.customerName.isNotBlank()) invoice.customerName else "Cash Customer"
                    canvas.drawText("Party Name: $partyName", rightBoxX + 10f, y + 32f, paint)
                    val partyContact = if (invoice.customerPhone.isNotBlank()) invoice.customerPhone else "N/A"
                    canvas.drawText("Phone: $partyContact", rightBoxX + 10f, y + 46f, paint)
                    val partyGst = if (invoice.effectiveCustomerGstin.isNotBlank()) "GSTIN: ${invoice.effectiveCustomerGstin}" else "Place of Supply: ${invoice.stateOfSupply}"
                    canvas.drawText(partyGst, rightBoxX + 10f, y + 60f, paint)

                    y += boxHeight + 14f
                } else {
                    y += 10f
                }

                // 4. Line Items Table Header
                val headerHeight = 22f
                val tableHeaderRect = RectF(margin, y, margin + contentWidth, y + headerHeight)
                paint.color = Color.rgb(30, 58, 138)
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(tableHeaderRect, 4f, 4f, paint)

                paint.color = Color.WHITE
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 8.5f

                val colSr = margin + 6f
                val colDesc = margin + 30f
                val colHsn = margin + 220f
                val colQty = margin + 285f
                val colRate = margin + 345f
                val colGst = margin + 415f
                val colTotal = margin + contentWidth - 8f

                canvas.drawText("#", colSr, y + 15f, paint)
                canvas.drawText("Item & Size Description", colDesc, y + 15f, paint)
                canvas.drawText("HSN", colHsn, y + 15f, paint)

                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("Qty", colQty, y + 15f, paint)
                canvas.drawText("Rate (₹)", colRate, y + 15f, paint)
                if (isTaxInvoice) {
                    canvas.drawText("Tax Rate", colGst, y + 15f, paint)
                }
                canvas.drawText("Total (₹)", colTotal, y + 15f, paint)
                paint.textAlign = Paint.Align.LEFT

                y += headerHeight

                // Draw Table Rows for this page
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textSize = 8f
                val rowHeight = 20f

                val startingIndex = if (isFirstPage) 0 else 8 + ((pageIndex - 1) * 14)

                currentItems.forEachIndexed { idx, item ->
                    val globalIdx = startingIndex + idx
                    if (idx % 2 == 1) {
                        paint.color = Color.rgb(248, 250, 252)
                        paint.style = Paint.Style.FILL
                        canvas.drawRect(margin, y, margin + contentWidth, y + rowHeight, paint)
                    }

                    paint.color = Color.rgb(241, 245, 249)
                    paint.strokeWidth = 0.8f
                    canvas.drawLine(margin, y + rowHeight, margin + contentWidth, y + rowHeight, paint)

                    paint.color = Color.rgb(30, 41, 59)
                    paint.style = Paint.Style.FILL
                    canvas.drawText("${globalIdx + 1}", colSr, y + 14f, paint)

                    val sizeBadge = if (item.size.isNotBlank() && item.size != "Free") " [Size: ${item.size}]" else ""
                    val fullName = item.name + sizeBadge
                    val truncatedName = if (fullName.length > 32) fullName.substring(0, 29) + "..." else fullName
                    canvas.drawText(truncatedName, colDesc, y + 14f, paint)
                    canvas.drawText(item.hsnCode.ifBlank { "-" }, colHsn, y + 14f, paint)

                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("${item.quantity}", colQty, y + 14f, paint)
                    canvas.drawText("%.2f".format(item.unitPrice), colRate, y + 14f, paint)
                    if (isTaxInvoice) {
                        val gstDisplay = if (invoice.isInterState) "IGST ${item.gstPercent}%" else "C+S ${(item.gstPercent/2.0)}%"
                        canvas.drawText(gstDisplay, colGst, y + 14f, paint)
                    }
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("%.2f".format(item.total), colTotal, y + 14f, paint)
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    paint.textAlign = Paint.Align.LEFT

                    y += rowHeight
                }

                // 5. Summary Section (Only on Last Page)
                if (isLastPage) {
                    y += 10f

                    // If Tax Invoice: Draw Tax Breakdown Table on Left Side
                    val breakdown = invoice.getTaxBreakdown()
                    if (isTaxInvoice && breakdown.isNotEmpty()) {
                        paint.color = Color.rgb(15, 23, 42)
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        paint.textSize = 8.5f
                        canvas.drawText("TAX SUMMARY BREAKDOWN:", margin, y + 12f, paint)

                        y += 18f
                        val taxTableWidth = 270f
                        paint.color = Color.rgb(241, 245, 249)
                        paint.style = Paint.Style.FILL
                        canvas.drawRect(margin, y, margin + taxTableWidth, y + 16f, paint)

                        paint.color = Color.rgb(51, 65, 85)
                        paint.textSize = 7.5f
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        canvas.drawText("Rate", margin + 4f, y + 11f, paint)
                        canvas.drawText("Taxable", margin + 45f, y + 11f, paint)
                        if (invoice.isInterState) {
                            canvas.drawText("IGST", margin + 130f, y + 11f, paint)
                        } else {
                            canvas.drawText("CGST", margin + 115f, y + 11f, paint)
                            canvas.drawText("SGST", margin + 175f, y + 11f, paint)
                        }
                        canvas.drawText("Total Tax", margin + 225f, y + 11f, paint)

                        y += 16f
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        breakdown.forEach { b ->
                            paint.color = Color.rgb(51, 65, 85)
                            canvas.drawText("${b.rate}%", margin + 4f, y + 12f, paint)
                            canvas.drawText("%.2f".format(b.taxableAmount), margin + 45f, y + 12f, paint)
                            if (invoice.isInterState) {
                                canvas.drawText("%.2f".format(b.igstAmount), margin + 130f, y + 12f, paint)
                            } else {
                                canvas.drawText("%.2f".format(b.cgstAmount), margin + 115f, y + 12f, paint)
                                canvas.drawText("%.2f".format(b.sgstAmount), margin + 175f, y + 12f, paint)
                            }
                            canvas.drawText("%.2f".format(b.totalTax), margin + 225f, y + 12f, paint)
                            y += 14f
                        }
                    }

                    // Calculation Totals Box on Right Side
                    val summaryWidth = 210f
                    val summaryX = margin + contentWidth - summaryWidth
                    var sumY = if (isTaxInvoice && breakdown.isNotEmpty()) (y - (breakdown.size * 14f) - 34f).coerceAtLeast(y - 80f) else y

                    fun drawSumLine(label: String, value: String, isBold: Boolean = false, color: Int = Color.rgb(30, 41, 59)) {
                        paint.typeface = Typeface.create(Typeface.DEFAULT, if (isBold) Typeface.BOLD else Typeface.NORMAL)
                        paint.textSize = if (isBold) 9.5f else 8.5f
                        paint.color = color
                        canvas.drawText(label, summaryX + 6f, sumY + 12f, paint)

                        paint.textAlign = Paint.Align.RIGHT
                        canvas.drawText(value, margin + contentWidth - 6f, sumY + 12f, paint)
                        paint.textAlign = Paint.Align.LEFT
                        sumY += 16f
                    }

                    drawSumLine("Taxable Subtotal:", "₹${"%.2f".format(invoice.subtotal)}")
                    if (isTaxInvoice) {
                        if (invoice.isInterState) {
                            drawSumLine("IGST Amount:", "₹${"%.2f".format(invoice.igstAmount)}")
                        } else {
                            drawSumLine("CGST Amount:", "₹${"%.2f".format(invoice.cgstAmount)}")
                            drawSumLine("SGST Amount:", "₹${"%.2f".format(invoice.sgstAmount)}")
                        }
                    }
                    if (invoice.additionalCharges > 0.0) {
                        drawSumLine("Delivery/Charges:", "₹${"%.2f".format(invoice.additionalCharges)}")
                    }
                    if (invoice.discount > 0.0) {
                        drawSumLine("Discount:", "-₹${"%.2f".format(invoice.discount)}", color = Color.rgb(22, 101, 52))
                    }
                    if (Math.abs(invoice.roundOff) > 0.001) {
                        val sign = if (invoice.roundOff >= 0) "+" else ""
                        drawSumLine("Round off:", "$sign₹${"%.2f".format(invoice.roundOff)}")
                    }

                    // Grand Net Total Box
                    val grandTotalBox = RectF(summaryX, sumY + 2f, margin + contentWidth, sumY + 28f)
                    paint.color = if (invoice.isCancelled) Color.rgb(220, 38, 38) else Color.rgb(30, 58, 138)
                    paint.style = Paint.Style.FILL
                    canvas.drawRoundRect(grandTotalBox, 4f, 4f, paint)

                    paint.color = Color.WHITE
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 11f
                    canvas.drawText("NET TOTAL:", summaryX + 8f, sumY + 19f, paint)

                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("₹${"%.2f".format(invoice.totalAmount)}", margin + contentWidth - 8f, sumY + 19f, paint)
                    paint.textAlign = Paint.Align.LEFT

                    y = sumY + 36f

                    // Total in words
                    val words = NumberToWords.convert(invoice.totalAmount)
                    paint.color = Color.rgb(15, 23, 42)
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 8.5f
                    canvas.drawText("Amount in Words: ", margin, y, paint)
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    paint.color = Color.rgb(51, 65, 85)
                    canvas.drawText(words, margin + 90f, y, paint)

                    y += 18f
                    val terms = if (invoice.notes.isNotBlank()) invoice.notes else "Terms: Goods once sold will not be taken back or exchanged after 7 days."
                    paint.textSize = 7.5f
                    paint.color = Color.rgb(100, 116, 139)
                    canvas.drawText(terms, margin, y, paint)
                    y += 12f
                    canvas.drawText("For ${storeProfile.storeName.ifBlank { "Ghanshyam Kids Wear" }} | Authorized Signatory", margin + contentWidth - 220f, y, paint)
                }

                // 6. Footer (Page X of Y)
                val footerY = pageHeight - margin + 14f
                paint.color = Color.rgb(148, 163, 184)
                paint.textSize = 7.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("Computer-generated invoice. Confirm rates with your CA.", margin, footerY, paint)

                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("Page ${pageIndex + 1} of $totalPages", margin + contentWidth, footerY, paint)
                paint.textAlign = Paint.Align.LEFT

                pdfDocument.finishPage(page)
            }

            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
        } catch (t: Throwable) {
            android.util.Log.w("PdfInvoiceGenerator", "Native PDF generation warning: ${t.message}. Generating basic PDF structure.")
            FileOutputStream(pdfFile).use { out ->
                val fallbackPdf = "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n3 0 obj<</Type/Page/MediaBox[0 0 595 842]/Parent 2 0 R>>endobj\nxref\n0 4\n0000000000 65535 f \n0000000009 00000 n \n0000000052 00000 n \n0000000101 00000 n \ntrailer<</Size 4/Root 1 0 R>>\nstartxref\n162\n%%EOF\n"
                out.write(fallbackPdf.toByteArray())
            }
        }

        return pdfFile
    }

    /**
     * Shares the generated PDF invoice via Android ACTION_SEND intent.
     */
    fun shareInvoicePdf(
        context: Context,
        storeProfile: StoreProfile,
        invoice: Invoice
    ) {
        val pdfFile = generateInvoicePdf(context, storeProfile, invoice)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            val docLabel = if (storeProfile.gstin.isNotBlank()) "Tax Invoice" else "Bill of Supply"
            val cancelPrefix = if (invoice.isCancelled) "[CANCELLED] " else ""
            putExtra(Intent.EXTRA_SUBJECT, "$cancelPrefix$docLabel #${invoice.billNumber} - ${storeProfile.storeName}")
            putExtra(
                Intent.EXTRA_TEXT,
                "Dear ${if (invoice.customerName.isNotBlank()) invoice.customerName else "Party"},\n\n" +
                    "Please find attached your $docLabel #${invoice.billNumber} for ₹${"%.2f".format(invoice.totalAmount)} from ${storeProfile.storeName}.\n\n" +
                    (if (invoice.isCancelled) "NOTE: This invoice has been CANCELLED (${invoice.cancelReason ?: "Cancelled"}).\n\n" else "") +
                    "Thank you for your business!"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Share Invoice PDF"))
    }
}
