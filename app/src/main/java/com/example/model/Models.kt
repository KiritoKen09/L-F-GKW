package com.example.model

import java.util.UUID

enum class PartyType(val raw: String) {
    CUSTOMER("CUSTOMER"),
    SUPPLIER("SUPPLIER");

    companion object {
        fun fromString(value: String?): PartyType {
            return when (value?.uppercase()) {
                "SUPPLIER" -> SUPPLIER
                else -> CUSTOMER
            }
        }
    }
}

data class Customer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val address: String = "",
    val gstin: String = "",
    val type: String = "CUSTOMER", // "CUSTOMER" or "SUPPLIER"
    val createdAt: Long = System.currentTimeMillis()
)

data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val customerId: String,
    val amount: Double,
    val type: String, // "GAVE" or "GOT"
    val note: String = "",
    val date: Long = System.currentTimeMillis(),
    val paymentMode: String = "CASH", // CASH, UPI, BANK
    val linkedToTxId: String? = null, // Links payment to a specific credit bill
    val isCompleted: Boolean = false, // Plain checkbox (goods delivered/packed), no money effect
    val billNumber: String = ""
)

data class InventoryItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val itemCode: String = "",
    val hsnCode: String = "",
    val salePrice: Double,
    val purchasePrice: Double = 0.0,
    val sizeStock: Map<String, Int> = emptyMap(),
    val category: String = "General",
    val lowStockThreshold: Int = 5,
    val packSize: Int = 1,
    val packRatio: Map<String, Int> = emptyMap(),
    val gstPercent: Double = 0.0
) {
    val stock: Int get() = sizeStock.values.sum()
    val sizes: List<String> get() = sizeStock.keys.toList()

    constructor(
        id: String = UUID.randomUUID().toString(),
        name: String,
        itemCode: String = "",
        hsnCode: String = "",
        salePrice: Double,
        purchasePrice: Double = 0.0,
        stock: Int = 0,
        category: String = "General",
        sizes: List<String> = emptyList(),
        gstPercent: Double = 0.0,
        lowStockThreshold: Int = 5,
        packSize: Int = 1,
        packRatio: Map<String, Int> = emptyMap()
    ) : this(
        id = id,
        name = name,
        itemCode = itemCode,
        hsnCode = hsnCode,
        salePrice = salePrice,
        purchasePrice = purchasePrice,
        sizeStock = if (sizes.isNotEmpty()) {
            val perSize = stock / sizes.size
            val remainder = stock % sizes.size
            sizes.mapIndexed { idx, s -> s to (perSize + if (idx == 0) remainder else 0) }.toMap()
        } else {
            mapOf("Free" to stock)
        },
        category = category,
        lowStockThreshold = lowStockThreshold,
        packSize = packSize,
        packRatio = packRatio,
        gstPercent = gstPercent
    )
}

data class InvoiceItem(
    val id: String = UUID.randomUUID().toString(),
    val itemId: String? = null,
    val name: String,
    val size: String = "Free",
    val hsnCode: String = "",
    val quantity: Int = 1,
    val unitPrice: Double,
    val gstPercent: Double = 0.0,
    val cgstRate: Double = 0.0,
    val sgstRate: Double = 0.0,
    val igstRate: Double = 0.0,
    val cgstAmount: Double = 0.0,
    val sgstAmount: Double = 0.0,
    val igstAmount: Double = 0.0,
    val taxableAmount: Double = quantity * unitPrice,
    val total: Double = quantity * unitPrice + cgstAmount + sgstAmount + igstAmount
) {
    fun withGstSplit(isInterState: Boolean): InvoiceItem {
        val taxable = quantity * unitPrice
        return if (isInterState) {
            val igst = taxable * (gstPercent / 100.0)
            copy(
                cgstRate = 0.0,
                sgstRate = 0.0,
                igstRate = gstPercent,
                cgstAmount = 0.0,
                sgstAmount = 0.0,
                igstAmount = igst,
                taxableAmount = taxable,
                total = taxable + igst
            )
        } else {
            val halfRate = gstPercent / 2.0
            val halfTax = taxable * (halfRate / 100.0)
            copy(
                cgstRate = halfRate,
                sgstRate = halfRate,
                igstRate = 0.0,
                cgstAmount = halfTax,
                sgstAmount = halfTax,
                igstAmount = 0.0,
                taxableAmount = taxable,
                total = taxable + (halfTax * 2)
            )
        }
    }
}

data class GstRateBreakdown(
    val rate: Double,
    val taxableAmount: Double,
    val cgstAmount: Double,
    val sgstAmount: Double,
    val igstAmount: Double,
    val totalTax: Double
)

data class Invoice(
    val id: String = UUID.randomUUID().toString(),
    val billNumber: String,
    val date: Long = System.currentTimeMillis(),
    val customerId: String? = null,
    val customerName: String,
    val customerPhone: String = "",
    val customerGstin: String = "",
    val items: List<InvoiceItem> = emptyList(),
    val subtotal: Double = 0.0,
    val gstAmount: Double = 0.0,
    val cgstAmount: Double = 0.0,
    val sgstAmount: Double = 0.0,
    val igstAmount: Double = 0.0,
    val isInterState: Boolean = false,
    val discount: Double = 0.0,
    val additionalCharges: Double = 0.0,
    val roundOff: Double = 0.0,
    val totalAmount: Double = 0.0,
    val paymentMode: String = "CASH", // CASH, UPI, CREDIT
    val stateOfSupply: String = "Gujarat",
    val gstin: String = "", // Legacy / optional customer GSTIN
    val notes: String = "",
    val themeName: String = "PREMIUM_BLUE",
    val isCancelled: Boolean = false,
    val cancelReason: String? = null,
    val cancelledAt: Long? = null
) {
    val effectiveCustomerGstin: String get() = customerGstin.ifBlank { gstin }

    fun getTaxBreakdown(): List<GstRateBreakdown> {
        val grouped = items.groupBy { it.gstPercent }
        return grouped.map { (rate, lineItems) ->
            val taxable = lineItems.sumOf { it.taxableAmount }
            val cgst = lineItems.sumOf { it.cgstAmount }
            val sgst = lineItems.sumOf { it.sgstAmount }
            val igst = lineItems.sumOf { it.igstAmount }
            GstRateBreakdown(
                rate = rate,
                taxableAmount = taxable,
                cgstAmount = cgst,
                sgstAmount = sgst,
                igstAmount = igst,
                totalTax = cgst + sgst + igst
            )
        }.sortedBy { it.rate }
    }
}

data class StoreProfile(
    val storeName: String = "",
    val address: String = "",
    val phone: String = "",
    val upiId: String = "",
    val gstin: String = "",
    val ownerName: String = "",
    val state: String = "Gujarat",
    val logoUri: String? = null
)

data class CashEntry(
    val id: String = UUID.randomUUID().toString(),
    val type: String, // "IN" or "OUT"
    val amount: Double,
    val category: String,
    val remark: String = "",
    val date: Long = System.currentTimeMillis(),
    val paymentMode: String = "CASH", // CASH, UPI, BANK, CHEQUE
    val linkedId: String? = null,
    val source: String = "manual" // "manual", "invoice", "khata", "settlement"
)

data class ImportResult(
    val success: Boolean,
    val customerCount: Int = 0,
    val transactionCount: Int = 0,
    val inventoryCount: Int = 0,
    val invoiceCount: Int = 0,
    val cashbookCount: Int = 0,
    val errorMessage: String? = null,
    val repairedCount: Int = 0
) {
    val isValid: Boolean get() = success
    val message: String get() = errorMessage ?: (
        if (repairedCount > 0) {
            "Import successful ($customerCount customers, $transactionCount txs, $inventoryCount items, $invoiceCount bills, $cashbookCount cash entries). Note: $repairedCount unlinked transaction(s) attached to 'Recovered entries'."
        } else {
            "Import successful ($customerCount customers, $transactionCount txs, $inventoryCount items, $invoiceCount bills, $cashbookCount cash entries)"
        }
    )
}

data class CsvCustomerPreview(
    val name: String,
    val phone: String,
    val balance: Double,
    val isDuplicate: Boolean,
    val type: String = "CUSTOMER"
)

enum class AppLanguage(val code: String, val displayName: String, val nativeName: String) {
    HINGLISH("hinglish", "Hinglish", "Hinglish (Default)"),
    HINDI("hi", "Hindi", "हिंदी"),
    GUJARATI("gu", "Gujarati", "ગુજરાતી"),
    ENGLISH("en", "English", "English")
}

data class InvoiceThemeConfig(
    val id: String,
    val name: String,
    val primaryColorHex: String,
    val accentColorHex: String,
    val isPremium: Boolean = false,
    val badgeText: String = ""
)
