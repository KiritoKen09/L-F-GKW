package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.model.*
import java.util.UUID

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val address: String = "",
    val gstin: String = "",
    val type: String = "CUSTOMER", // "CUSTOMER" or "SUPPLIER"
    val createdAt: Long = System.currentTimeMillis()
)

fun CustomerEntity.toDomain() = Customer(
    id = id,
    name = name,
    phone = phone,
    address = address,
    gstin = gstin,
    type = type,
    createdAt = createdAt
)

fun Customer.toEntity() = CustomerEntity(
    id = id,
    name = name,
    phone = phone,
    address = address,
    gstin = gstin,
    type = type,
    createdAt = createdAt
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["customerId"]),
        Index(value = ["date"]),
        Index(value = ["linkedToTxId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TransactionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val customerId: String,
    val amount: Double,
    val type: String, // "GAVE", "GOT"
    val note: String = "",
    val date: Long = System.currentTimeMillis(),
    val paymentMode: String = "CASH",
    val linkedToTxId: String? = null,
    val isCompleted: Boolean = false,
    val billNumber: String = ""
)

fun TransactionEntity.toDomain() = Transaction(
    id = id,
    customerId = customerId,
    amount = amount,
    type = type,
    note = note,
    date = date,
    paymentMode = paymentMode,
    linkedToTxId = linkedToTxId,
    isCompleted = isCompleted,
    billNumber = billNumber
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    customerId = customerId,
    amount = amount,
    type = type,
    note = note,
    date = date,
    paymentMode = paymentMode,
    linkedToTxId = linkedToTxId,
    isCompleted = isCompleted,
    billNumber = billNumber
)

@Entity(tableName = "inventory_items")
data class InventoryItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val itemCode: String = "",
    val hsnCode: String = "",
    val salePrice: Double,
    val purchasePrice: Double = 0.0,
    val stock: Int = 0,
    val sizeStock: Map<String, Int> = emptyMap(),
    val category: String = "General",
    val sizes: List<String> = emptyList(),
    val lowStockThreshold: Int = 5,
    val packSize: Int = 1,
    val packRatio: Map<String, Int> = emptyMap(),
    val gstPercent: Double = 0.0
)

fun InventoryItemEntity.toDomain(): InventoryItem {
    val resolvedSizeStock = if (sizeStock.isNotEmpty()) {
        sizeStock
    } else if (sizes.isNotEmpty()) {
        val perSize = stock / sizes.size
        val rem = stock % sizes.size
        sizes.mapIndexed { idx, s -> s to (perSize + if (idx == 0) rem else 0) }.toMap()
    } else {
        mapOf("Free" to stock)
    }
    return InventoryItem(
        id = id,
        name = name,
        itemCode = itemCode,
        hsnCode = hsnCode,
        salePrice = salePrice,
        purchasePrice = purchasePrice,
        sizeStock = resolvedSizeStock,
        category = category,
        lowStockThreshold = lowStockThreshold,
        packSize = packSize,
        packRatio = packRatio,
        gstPercent = gstPercent
    )
}

fun InventoryItem.toEntity() = InventoryItemEntity(
    id = id,
    name = name,
    itemCode = itemCode,
    hsnCode = hsnCode,
    salePrice = salePrice,
    purchasePrice = purchasePrice,
    stock = stock,
    sizeStock = sizeStock,
    category = category,
    sizes = sizes,
    lowStockThreshold = lowStockThreshold,
    packSize = packSize,
    packRatio = packRatio,
    gstPercent = gstPercent
)

@Entity(
    tableName = "invoices",
    indices = [
        Index(value = ["date"]),
        Index(value = ["customerId"])
    ]
)
data class InvoiceEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val billNumber: String,
    val date: Long = System.currentTimeMillis(),
    val customerId: String? = null,
    val customerName: String,
    val customerPhone: String = "",
    val subtotal: Double,
    val gstAmount: Double,
    val cgstAmount: Double = 0.0,
    val sgstAmount: Double = 0.0,
    val igstAmount: Double = 0.0,
    val isInterState: Boolean = false,
    val discount: Double = 0.0,
    val additionalCharges: Double = 0.0,
    val roundOff: Double = 0.0,
    val totalAmount: Double,
    val paymentMode: String = "CASH",
    val stateOfSupply: String = "Gujarat",
    val gstin: String = "",
    val notes: String = "",
    val themeName: String = "PREMIUM_BLUE",
    val isCancelled: Boolean = false,
    val cancelReason: String? = null,
    val cancelledAt: Long? = null
)

@Entity(
    tableName = "invoice_items",
    indices = [
        Index(value = ["invoiceId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class InvoiceItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val invoiceId: String,
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
    val taxableAmount: Double = 0.0,
    val total: Double
)

fun InvoiceItemEntity.toDomain() = InvoiceItem(
    id = id,
    itemId = itemId,
    name = name,
    size = size,
    hsnCode = hsnCode,
    quantity = quantity,
    unitPrice = unitPrice,
    gstPercent = gstPercent,
    cgstRate = cgstRate,
    sgstRate = sgstRate,
    igstRate = igstRate,
    cgstAmount = cgstAmount,
    sgstAmount = sgstAmount,
    igstAmount = igstAmount,
    taxableAmount = if (taxableAmount > 0.0) taxableAmount else quantity * unitPrice,
    total = total
)

fun InvoiceItem.toEntity(invoiceId: String) = InvoiceItemEntity(
    id = id,
    invoiceId = invoiceId,
    itemId = itemId,
    name = name,
    size = size,
    hsnCode = hsnCode,
    quantity = quantity,
    unitPrice = unitPrice,
    gstPercent = gstPercent,
    cgstRate = cgstRate,
    sgstRate = sgstRate,
    igstRate = igstRate,
    cgstAmount = cgstAmount,
    sgstAmount = sgstAmount,
    igstAmount = igstAmount,
    taxableAmount = taxableAmount,
    total = total
)

data class InvoiceWithItems(
    @Embedded
    val invoice: InvoiceEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "invoiceId"
    )
    val items: List<InvoiceItemEntity>
)

fun InvoiceWithItems.toDomain() = Invoice(
    id = invoice.id,
    billNumber = invoice.billNumber,
    date = invoice.date,
    customerId = invoice.customerId,
    customerName = invoice.customerName,
    customerPhone = invoice.customerPhone,
    customerGstin = invoice.gstin,
    items = items.map { it.toDomain() },
    subtotal = invoice.subtotal,
    gstAmount = invoice.gstAmount,
    cgstAmount = invoice.cgstAmount,
    sgstAmount = invoice.sgstAmount,
    igstAmount = invoice.igstAmount,
    isInterState = invoice.isInterState,
    discount = invoice.discount,
    additionalCharges = invoice.additionalCharges,
    roundOff = invoice.roundOff,
    totalAmount = invoice.totalAmount,
    paymentMode = invoice.paymentMode,
    stateOfSupply = invoice.stateOfSupply,
    gstin = invoice.gstin,
    notes = invoice.notes,
    themeName = invoice.themeName,
    isCancelled = invoice.isCancelled,
    cancelReason = invoice.cancelReason,
    cancelledAt = invoice.cancelledAt
)

fun Invoice.toEntity() = InvoiceEntity(
    id = id,
    billNumber = billNumber,
    date = date,
    customerId = customerId,
    customerName = customerName,
    customerPhone = customerPhone,
    subtotal = subtotal,
    gstAmount = gstAmount,
    cgstAmount = cgstAmount,
    sgstAmount = sgstAmount,
    igstAmount = igstAmount,
    isInterState = isInterState,
    discount = discount,
    additionalCharges = additionalCharges,
    roundOff = roundOff,
    totalAmount = totalAmount,
    paymentMode = paymentMode,
    stateOfSupply = stateOfSupply,
    gstin = effectiveCustomerGstin,
    notes = notes,
    themeName = themeName,
    isCancelled = isCancelled,
    cancelReason = cancelReason,
    cancelledAt = cancelledAt
)

@Entity(
    tableName = "cash_entries",
    indices = [
        Index(value = ["date"])
    ]
)
data class CashEntryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val type: String, // "IN", "OUT"
    val amount: Double,
    val category: String,
    val remark: String = "",
    val date: Long = System.currentTimeMillis(),
    val paymentMode: String = "CASH",
    val linkedId: String? = null,
    val source: String = "manual"
)

fun CashEntryEntity.toDomain() = CashEntry(
    id = id,
    type = type,
    amount = amount,
    category = category,
    remark = remark,
    date = date,
    paymentMode = paymentMode,
    linkedId = linkedId,
    source = source
)

fun CashEntry.toEntity() = CashEntryEntity(
    id = id,
    type = type,
    amount = amount,
    category = category,
    remark = remark,
    date = date,
    paymentMode = paymentMode,
    linkedId = linkedId,
    source = source
)

@Entity(tableName = "store_profile")
data class StoreProfileEntity(
    @PrimaryKey
    val id: Int = 1,
    val storeName: String = "",
    val address: String = "",
    val phone: String = "",
    val upiId: String = "",
    val gstin: String = "",
    val ownerName: String = "",
    val state: String = "Gujarat",
    val logoUri: String? = null
)

fun StoreProfileEntity.toDomain() = StoreProfile(
    storeName = storeName,
    address = address,
    phone = phone,
    upiId = upiId,
    gstin = gstin,
    ownerName = ownerName,
    state = state,
    logoUri = logoUri
)

fun StoreProfile.toEntity() = StoreProfileEntity(
    id = 1,
    storeName = storeName,
    address = address,
    phone = phone,
    upiId = upiId,
    gstin = gstin,
    ownerName = ownerName,
    state = state,
    logoUri = logoUri
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val language: String = "hinglish",
    val theme: String = "PREMIUM_BLUE",
    val hasCompletedOnboarding: Boolean = false
)

@Entity(tableName = "invoice_sequences")
data class InvoiceSequenceEntity(
    @PrimaryKey
    val financialYear: String,
    val lastSequence: Int = 0
)
