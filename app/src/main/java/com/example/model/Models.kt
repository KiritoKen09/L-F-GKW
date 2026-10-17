package com.example.model

import java.util.UUID

data class Customer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val address: String = "",
    val gstin: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val customerId: String,
    val amount: Double,
    val type: String, // "GAVE" (Udhar) or "GOT" (Jama)
    val note: String = "",
    val date: Long = System.currentTimeMillis(),
    val isCancelled: Boolean = false, // Manual cancel out tick box
    val billNumber: String = ""
)

data class InventoryItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val itemCode: String = "",
    val salePrice: Double,
    val purchasePrice: Double = 0.0,
    val stock: Int = 0,
    val category: String = "General",
    val sizes: List<String> = emptyList(),
    val gstPercent: Double = 0.0
)

data class InvoiceItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: Int = 1,
    val unitPrice: Double,
    val gstPercent: Double = 0.0,
    val total: Double = quantity * unitPrice
)

data class Invoice(
    val id: String = UUID.randomUUID().toString(),
    val billNumber: String,
    val date: Long = System.currentTimeMillis(),
    val customerName: String,
    val customerPhone: String = "",
    val items: List<InvoiceItem> = emptyList(),
    val subtotal: Double = 0.0,
    val gstAmount: Double = 0.0,
    val discount: Double = 0.0,
    val additionalCharges: Double = 0.0,
    val totalAmount: Double = 0.0,
    val paymentMode: String = "CASH", // CASH, UPI, CREDIT
    val stateOfSupply: String = "Delhi",
    val gstin: String = "",
    val notes: String = "",
    val themeName: String = "PREMIUM_BLUE"
)

data class StoreProfile(
    val storeName: String = "Ghanshyam Traders",
    val address: String = "Shop #12, Main Market, Delhi",
    val phone: String = "+91 98765 43210",
    val upiId: String = "ghanshyam@upi",
    val gstin: String = "07AAAAA0000A1Z5",
    val ownerName: String = "Ghanshyam Lal"
)

data class CashEntry(
    val id: String = UUID.randomUUID().toString(),
    val type: String, // "IN" or "OUT"
    val amount: Double,
    val category: String,
    val remark: String = "",
    val date: Long = System.currentTimeMillis()
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
