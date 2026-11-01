package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY createdAt DESC")
    fun getAll(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY createdAt DESC")
    suspend fun getAllList(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customer: CustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(customers: List<CustomerEntity>)

    @Update
    suspend fun update(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM customers")
    suspend fun deleteAll()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    suspend fun getAllList(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE customerId = :customerId ORDER BY date DESC")
    suspend fun getByCustomerId(customerId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE billNumber = :billNumber")
    suspend fun getByBillNumber(billNumber: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE linkedToTxId = :linkedToTxId")
    suspend fun getByLinkedToTxId(linkedToTxId: String): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE linkedToTxId = :linkedToTxId")
    suspend fun deleteByLinkedToTxId(linkedToTxId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM transactions WHERE customerId = :customerId")
    suspend fun deleteByCustomerId(customerId: String)

    @Query("DELETE FROM transactions WHERE billNumber = :billNumber")
    suspend fun deleteByBillNumber(billNumber: String)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_items ORDER BY name ASC")
    fun getAll(): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items ORDER BY name ASC")
    suspend fun getAllList(): List<InventoryItemEntity>

    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): InventoryItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: InventoryItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<InventoryItemEntity>)

    @Update
    suspend fun update(item: InventoryItemEntity)

    @Query("DELETE FROM inventory_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM inventory_items")
    suspend fun deleteAll()
}

@Dao
interface InvoiceDao {
    @Transaction
    @Query("SELECT * FROM invoices ORDER BY date DESC")
    fun getAllWithItems(): Flow<List<InvoiceWithItems>>

    @Transaction
    @Query("SELECT * FROM invoices ORDER BY date DESC")
    suspend fun getAllWithItemsList(): List<InvoiceWithItems>

    @Transaction
    @Query("SELECT * FROM invoices WHERE id = :id LIMIT 1")
    suspend fun getWithItemsById(id: String): InvoiceWithItems?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: InvoiceEntity)

    @Update
    suspend fun updateInvoice(invoice: InvoiceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoices(invoices: List<InvoiceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<InvoiceItemEntity>)

    @Query("DELETE FROM invoices WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM invoice_items WHERE invoiceId = :invoiceId")
    suspend fun deleteItemsByInvoiceId(invoiceId: String)

    @Query("DELETE FROM invoices")
    suspend fun deleteAll()
}

@Dao
interface CashbookDao {
    @Query("SELECT * FROM cash_entries ORDER BY date DESC")
    fun getAll(): Flow<List<CashEntryEntity>>

    @Query("SELECT * FROM cash_entries ORDER BY date DESC")
    suspend fun getAllList(): List<CashEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CashEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<CashEntryEntity>)

    @Query("DELETE FROM cash_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM cash_entries WHERE linkedId = :linkedId")
    suspend fun deleteByLinkedId(linkedId: String)

    @Query("DELETE FROM cash_entries WHERE linkedId IN (:linkedIds)")
    suspend fun deleteByLinkedIds(linkedIds: List<String>)

    @Query("DELETE FROM cash_entries")
    suspend fun deleteAll()
}

@Dao
interface StoreProfileDao {
    @Query("SELECT * FROM store_profile WHERE id = 1 LIMIT 1")
    fun getProfile(): Flow<StoreProfileEntity?>

    @Query("SELECT * FROM store_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfileDirect(): StoreProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: StoreProfileEntity)

    @Query("DELETE FROM store_profile")
    suspend fun delete()
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: AppSettingsEntity)

    @Query("DELETE FROM app_settings")
    suspend fun delete()
}

@Dao
interface InvoiceSequenceDao {
    @Query("SELECT lastSequence FROM invoice_sequences WHERE financialYear = :fy LIMIT 1")
    suspend fun getLastSequence(fy: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(seq: InvoiceSequenceEntity)

    @Query("DELETE FROM invoice_sequences")
    suspend fun deleteAll()
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(msg: ChatMessageEntity)

    @Update
    suspend fun updateMessage(msg: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE id NOT IN (SELECT id FROM chat_messages ORDER BY timestamp DESC LIMIT 50)")
    suspend fun pruneOldMessages()

    @Query("UPDATE chat_messages SET isApplied = 1 WHERE id = :id")
    suspend fun markApplied(id: String)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()
}
