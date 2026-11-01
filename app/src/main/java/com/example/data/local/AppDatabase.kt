package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CustomerEntity::class,
        TransactionEntity::class,
        InventoryItemEntity::class,
        InvoiceEntity::class,
        InvoiceItemEntity::class,
        CashEntryEntity::class,
        StoreProfileEntity::class,
        AppSettingsEntity::class,
        InvoiceSequenceEntity::class,
        ChatMessageEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun customerDao(): CustomerDao
    abstract fun transactionDao(): TransactionDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun cashbookDao(): CashbookDao
    abstract fun storeProfileDao(): StoreProfileDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun invoiceSequenceDao(): InvoiceSequenceDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add party type to customers
                db.execSQL("ALTER TABLE customers ADD COLUMN type TEXT NOT NULL DEFAULT 'CUSTOMER'")

                // 2. Recreate transactions table to drop isCancelled and add paymentMode, linkedToTxId, isCompleted
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS transactions_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        customerId TEXT NOT NULL,
                        amount REAL NOT NULL,
                        type TEXT NOT NULL,
                        note TEXT NOT NULL,
                        date INTEGER NOT NULL,
                        paymentMode TEXT NOT NULL DEFAULT 'CASH',
                        linkedToTxId TEXT DEFAULT NULL,
                        isCompleted INTEGER NOT NULL DEFAULT 0,
                        billNumber TEXT NOT NULL,
                        FOREIGN KEY(customerId) REFERENCES customers(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO transactions_new (id, customerId, amount, type, note, date, paymentMode, linkedToTxId, isCompleted, billNumber)
                    SELECT id, customerId, amount, type, note, date, 'CASH', NULL, 0, billNumber
                    FROM transactions
                """.trimIndent())

                // Map old isCancelled = 1 rows into linked settled payments
                val cursor = db.query("SELECT id, customerId, amount, date FROM transactions WHERE isCancelled = 1 AND type = 'GAVE'")
                while (cursor.moveToNext()) {
                    val billId = cursor.getString(0)
                    val custId = cursor.getString(1)
                    val amt = cursor.getDouble(2)
                    val dt = cursor.getLong(3)
                    val paymentId = java.util.UUID.randomUUID().toString()
                    db.execSQL(
                        "INSERT INTO transactions_new (id, customerId, amount, type, note, date, paymentMode, linkedToTxId, isCompleted, billNumber) VALUES (?, ?, ?, 'GOT', 'Settled (migrated)', ?, 'CASH', ?, 1, '')",
                        arrayOf(paymentId, custId, amt, dt, billId)
                    )
                    db.execSQL("UPDATE transactions_new SET isCompleted = 1 WHERE id = ?", arrayOf(billId))
                }
                cursor.close()

                db.execSQL("DROP TABLE transactions")
                db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")

                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_customerId ON transactions(customerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_date ON transactions(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_linkedToTxId ON transactions(linkedToTxId)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Inventory items updates
                db.execSQL("ALTER TABLE inventory_items ADD COLUMN hsnCode TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE inventory_items ADD COLUMN sizeStock TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE inventory_items ADD COLUMN lowStockThreshold INTEGER NOT NULL DEFAULT 5")
                db.execSQL("ALTER TABLE inventory_items ADD COLUMN packSize INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE inventory_items ADD COLUMN packRatio TEXT NOT NULL DEFAULT '{}'")

                val invCursor = db.query("SELECT id, stock FROM inventory_items")
                while (invCursor.moveToNext()) {
                    val id = invCursor.getString(0)
                    val stk = invCursor.getInt(1)
                    val json = "{\"Free\":$stk}"
                    db.execSQL("UPDATE inventory_items SET sizeStock = ? WHERE id = ?", arrayOf(json, id))
                }
                invCursor.close()

                // 2. Invoices updates
                db.execSQL("ALTER TABLE invoices ADD COLUMN customerId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE invoices ADD COLUMN isInterState INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN cgstAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN sgstAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN igstAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN roundOff REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN isCancelled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN cancelReason TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE invoices ADD COLUMN cancelledAt INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_customerId ON invoices(customerId)")

                // 3. Invoice items updates
                db.execSQL("ALTER TABLE invoice_items ADD COLUMN itemId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE invoice_items ADD COLUMN size TEXT NOT NULL DEFAULT 'Free'")
                db.execSQL("ALTER TABLE invoice_items ADD COLUMN hsnCode TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE invoice_items ADD COLUMN cgstRate REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoice_items ADD COLUMN sgstRate REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoice_items ADD COLUMN igstRate REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoice_items ADD COLUMN cgstAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoice_items ADD COLUMN sgstAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoice_items ADD COLUMN igstAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoice_items ADD COLUMN taxableAmount REAL NOT NULL DEFAULT 0.0")

                // 4. Store profile updates
                db.execSQL("ALTER TABLE store_profile ADD COLUMN logoUri TEXT DEFAULT NULL")

                // 5. Invoice sequences table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS invoice_sequences (
                        financialYear TEXT NOT NULL PRIMARY KEY,
                        lastSequence INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS chat_messages (
                        id TEXT NOT NULL PRIMARY KEY,
                        sender TEXT NOT NULL,
                        text TEXT NOT NULL,
                        actionType TEXT DEFAULT NULL,
                        actionData TEXT DEFAULT NULL,
                        actionTitle TEXT DEFAULT NULL,
                        reportContent TEXT DEFAULT NULL,
                        timestamp INTEGER NOT NULL,
                        isApplied INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_timestamp ON chat_messages(timestamp)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ghanshyam_billing.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
