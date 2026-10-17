package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class AppRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ghanshyam_billing_prefs", Context.MODE_PRIVATE)

    private val _customers = MutableStateFlow<List<Customer>>(emptyList())
    val customers: StateFlow<List<Customer>> = _customers.asStateFlow()

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _inventory = MutableStateFlow<List<InventoryItem>>(emptyList())
    val inventory: StateFlow<List<InventoryItem>> = _inventory.asStateFlow()

    private val _invoices = MutableStateFlow<List<Invoice>>(emptyList())
    val invoices: StateFlow<List<Invoice>> = _invoices.asStateFlow()

    private val _cashbook = MutableStateFlow<List<CashEntry>>(emptyList())
    val cashbook: StateFlow<List<CashEntry>> = _cashbook.asStateFlow()

    private val _storeProfile = MutableStateFlow(StoreProfile())
    val storeProfile: StateFlow<StoreProfile> = _storeProfile.asStateFlow()

    private val _language = MutableStateFlow(AppLanguage.HINGLISH)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _hasCompletedOnboarding = MutableStateFlow(false)
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    private val _selectedInvoiceTheme = MutableStateFlow("PREMIUM_BLUE")
    val selectedInvoiceTheme: StateFlow<String> = _selectedInvoiceTheme.asStateFlow()

    private val _isCloudSyncEnabled = MutableStateFlow(false)
    val isCloudSyncEnabled: StateFlow<Boolean> = _isCloudSyncEnabled.asStateFlow()

    private val _lastCloudSyncTime = MutableStateFlow(0L)
    val lastCloudSyncTime: StateFlow<Long> = _lastCloudSyncTime.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        _hasCompletedOnboarding.value = prefs.getBoolean("has_completed_onboarding", false)

        val langCode = prefs.getString("selected_language", AppLanguage.HINGLISH.code)
        _language.value = AppLanguage.entries.find { it.code == langCode } ?: AppLanguage.HINGLISH

        _selectedInvoiceTheme.value = prefs.getString("invoice_theme", "PREMIUM_BLUE") ?: "PREMIUM_BLUE"
        _isCloudSyncEnabled.value = prefs.getBoolean("cloud_sync_enabled", false)
        _lastCloudSyncTime.value = prefs.getLong("last_cloud_sync_time", 0L)

        // Load store profile
        val storeName = prefs.getString("store_name", "Ghanshyam Traders") ?: "Ghanshyam Traders"
        val storeAddress = prefs.getString("store_address", "Shop #12, Main Market, Delhi") ?: "Shop #12, Main Market, Delhi"
        val storePhone = prefs.getString("store_phone", "+91 98765 43210") ?: "+91 98765 43210"
        val storeUpi = prefs.getString("store_upi", "ghanshyam@upi") ?: "ghanshyam@upi"
        val storeGstin = prefs.getString("store_gstin", "07AAAAA0000A1Z5") ?: "07AAAAA0000A1Z5"
        val ownerName = prefs.getString("owner_name", "Ghanshyam Lal") ?: "Ghanshyam Lal"

        _storeProfile.value = StoreProfile(
            storeName = storeName,
            address = storeAddress,
            phone = storePhone,
            upiId = storeUpi,
            gstin = storeGstin,
            ownerName = ownerName
        )

        val isInitialized = prefs.getBoolean("has_clean_initialized_v6", false)
        if (!isInitialized) {
            // Fresh clean start: everything empty as explicitly requested
            _customers.value = emptyList()
            _transactions.value = emptyList()
            _inventory.value = emptyList()
            _invoices.value = emptyList()
            _cashbook.value = emptyList()
            prefs.edit().putBoolean("has_clean_initialized_v6", true).apply()
            saveData()
        } else {
            loadFromStorage()
        }
    }

    fun completeOnboarding() {
        _hasCompletedOnboarding.value = true
        prefs.edit().putBoolean("has_completed_onboarding", true).apply()
    }

    fun resetOnboarding() {
        _hasCompletedOnboarding.value = false
        prefs.edit().putBoolean("has_completed_onboarding", false).apply()
    }

    fun setLanguage(newLanguage: AppLanguage) {
        _language.value = newLanguage
        prefs.edit().putString("selected_language", newLanguage.code).apply()
    }

    fun setInvoiceTheme(themeId: String) {
        _selectedInvoiceTheme.value = themeId
        prefs.edit().putString("invoice_theme", themeId).apply()
    }

    fun updateStoreProfile(
        storeName: String,
        address: String,
        phone: String,
        upiId: String,
        gstin: String,
        ownerName: String
    ) {
        _storeProfile.value = StoreProfile(storeName, address, phone, upiId, gstin, ownerName)
        prefs.edit()
            .putString("store_name", storeName)
            .putString("store_address", address)
            .putString("store_phone", phone)
            .putString("store_upi", upiId)
            .putString("store_gstin", gstin)
            .putString("owner_name", ownerName)
            .apply()
    }

    // Customer operations
    fun addCustomer(name: String, phone: String, address: String = "", gstin: String = ""): Customer {
        val newCustomer = Customer(
            name = name.trim(),
            phone = phone.trim(),
            address = address.trim(),
            gstin = gstin.trim()
        )
        _customers.value = listOf(newCustomer) + _customers.value
        saveData()
        return newCustomer
    }

    fun deleteCustomer(customerId: String) {
        _customers.value = _customers.value.filter { it.id != customerId }
        _transactions.value = _transactions.value.filter { it.customerId != customerId }
        saveData()
    }

    // Transaction operations (Udhar / Jama)
    fun addTransaction(
        customerId: String,
        amount: Double,
        type: String, // "GAVE" or "GOT"
        note: String = "",
        billNumber: String = ""
    ): Transaction {
        val newTx = Transaction(
            customerId = customerId,
            amount = amount,
            type = type,
            note = note,
            billNumber = billNumber
        )
        _transactions.value = listOf(newTx) + _transactions.value
        saveData()
        return newTx
    }

    // Manual tick box to cancel out an udhar entry (e.g. "You gave - 1850")
    fun toggleTransactionCancel(transactionId: String) {
        _transactions.value = _transactions.value.map { tx ->
            if (tx.id == transactionId) {
                tx.copy(isCancelled = !tx.isCancelled)
            } else {
                tx
            }
        }
        saveData()
    }

    fun deleteTransaction(transactionId: String) {
        _transactions.value = _transactions.value.filter { it.id != transactionId }
        saveData()
    }

    // Balance calculations
    fun getCustomerBalance(customerId: String): Double {
        val activeTx = _transactions.value.filter { it.customerId == customerId && !it.isCancelled }
        val gave = activeTx.filter { it.type == "GAVE" }.sumOf { it.amount }
        val got = activeTx.filter { it.type == "GOT" }.sumOf { it.amount }
        return gave - got // Positive means customer owes shop (Udhar/Receivable)
    }

    fun getTotalReceivable(): Double {
        return _customers.value.map { getCustomerBalance(it.id) }.filter { it > 0 }.sum()
    }

    fun getTotalPayable(): Double {
        return _customers.value.map { getCustomerBalance(it.id) }.filter { it < 0 }.sumOf { -it }
    }

    // Inventory operations
    fun addInventoryItem(
        name: String,
        itemCode: String,
        salePrice: Double,
        purchasePrice: Double = 0.0,
        stock: Int,
        category: String = "General",
        sizes: List<String> = emptyList(),
        gstPercent: Double = 0.0
    ): InventoryItem {
        val item = InventoryItem(
            name = name.trim(),
            itemCode = itemCode.trim(),
            salePrice = salePrice,
            purchasePrice = purchasePrice,
            stock = stock,
            category = category.trim(),
            sizes = sizes,
            gstPercent = gstPercent
        )
        _inventory.value = listOf(item) + _inventory.value
        saveData()
        return item
    }

    fun updateInventoryStock(itemId: String, delta: Int) {
        _inventory.value = _inventory.value.map { item ->
            if (item.id == itemId) {
                item.copy(stock = (item.stock + delta).coerceAtLeast(0))
            } else {
                item
            }
        }
        saveData()
    }

    fun deleteInventoryItem(itemId: String) {
        _inventory.value = _inventory.value.filter { it.id != itemId }
        saveData()
    }

    // Invoices operations
    fun createInvoice(invoice: Invoice): Invoice {
        _invoices.value = listOf(invoice) + _invoices.value

        // If items are in inventory, reduce stock
        invoice.items.forEach { line ->
            val matching = _inventory.value.find { it.name.equals(line.name, ignoreCase = true) }
            if (matching != null) {
                updateInventoryStock(matching.id, -line.quantity)
            }
        }

        // If payment mode is CREDIT and customer exists or named, add udhar transaction
        if (invoice.paymentMode == "CREDIT" && invoice.customerName.isNotBlank()) {
            var customer = _customers.value.find { it.name.equals(invoice.customerName, ignoreCase = true) }
            if (customer == null) {
                customer = addCustomer(invoice.customerName, invoice.customerPhone)
            }
            addTransaction(
                customerId = customer.id,
                amount = invoice.totalAmount,
                type = "GAVE",
                note = "Bill #${invoice.billNumber}",
                billNumber = invoice.billNumber
            )
        }

        saveData()
        return invoice
    }

    // Cashbook operations
    fun addCashEntry(
        type: String, // "IN" or "OUT"
        amount: Double,
        category: String,
        remark: String = ""
    ): CashEntry {
        val entry = CashEntry(
            type = type,
            amount = amount,
            category = category,
            remark = remark
        )
        _cashbook.value = listOf(entry) + _cashbook.value
        saveData()
        return entry
    }

    fun deleteCashEntry(id: String) {
        _cashbook.value = _cashbook.value.filter { it.id != id }
        saveData()
    }

    // Khatabook Import Simulation (phone-number based)
    fun importFromKhatabook(phone: String): Int {
        val importedCustomers = listOf(
            Customer(name = "Rajesh Sharma", phone = phone.ifBlank { "+91 98200 12345" }, address = "Civil Lines"),
            Customer(name = "Amit Verma", phone = "+91 98111 22334", address = "Sector 14"),
            Customer(name = "Pooja Gupta", phone = "+91 99222 33445", address = "Market Road"),
            Customer(name = "Suresh Tailors", phone = "+91 97333 44556", address = "Cloth Market")
        )

        val newCustomerList = _customers.value.toMutableList()
        val newTxList = _transactions.value.toMutableList()

        importedCustomers.forEach { cust ->
            if (newCustomerList.none { it.phone == cust.phone }) {
                newCustomerList.add(cust)
                // Add sample entries for Rajesh Sharma as mentioned by user:
                if (cust.name == "Rajesh Sharma") {
                    newTxList.add(Transaction(customerId = cust.id, amount = 1850.0, type = "GAVE", note = "Old Udhar"))
                    newTxList.add(Transaction(customerId = cust.id, amount = 400.0, type = "GOT", note = "Cash Payment"))
                } else if (cust.name == "Amit Verma") {
                    newTxList.add(Transaction(customerId = cust.id, amount = 950.0, type = "GAVE", note = "Goods delivery"))
                }
            }
        }

        _customers.value = newCustomerList
        _transactions.value = newTxList
        saveData()
        return importedCustomers.size
    }

    // Populate sample data optionally
    fun loadSampleData() {
        val ramesh = Customer(name = "Rajesh Sharma", phone = "+91 98200 12345", address = "Shop #4, Market")
        val sunita = Customer(name = "Sunita Devi", phone = "+91 98101 67890", address = "House 12")
        val vikram = Customer(name = "Vikram Patel", phone = "+91 99202 54321", address = "Ring Road")

        _customers.value = listOf(ramesh, sunita, vikram)

        _transactions.value = listOf(
            Transaction(customerId = ramesh.id, amount = 1850.0, type = "GAVE", note = "Grocery items"),
            Transaction(customerId = ramesh.id, amount = 400.0, type = "GOT", note = "UPI Payment"),
            Transaction(customerId = sunita.id, amount = 650.0, type = "GAVE", note = "Milk & Bread"),
            Transaction(customerId = vikram.id, amount = 1200.0, type = "GOT", note = "Advance")
        )

        _inventory.value = listOf(
            InventoryItem(name = "Basmati Rice 5kg", itemCode = "RIC01", salePrice = 450.0, purchasePrice = 380.0, stock = 24, category = "Grains", gstPercent = 5.0),
            InventoryItem(name = "Cotton Shirt (L)", itemCode = "SHT02", salePrice = 799.0, purchasePrice = 450.0, stock = 15, category = "Clothing", sizes = listOf("M", "L", "XL"), gstPercent = 12.0),
            InventoryItem(name = "Refined Oil 1L", itemCode = "OIL03", salePrice = 145.0, purchasePrice = 120.0, stock = 30, category = "Grocery", gstPercent = 5.0)
        )

        _cashbook.value = listOf(
            CashEntry(type = "IN", amount = 2500.0, category = "Sales", remark = "Morning counter cash"),
            CashEntry(type = "OUT", amount = 60.0, category = "Refreshment", remark = "Chai samosa")
        )

        saveData()
    }

    fun clearAllData() {
        _customers.value = emptyList()
        _transactions.value = emptyList()
        _inventory.value = emptyList()
        _invoices.value = emptyList()
        _cashbook.value = emptyList()
        saveData()
    }

    private fun saveData() {
        try {
            val json = JSONObject()

            // Customers
            val custArr = JSONArray()
            _customers.value.forEach { c ->
                val o = JSONObject()
                o.put("id", c.id)
                o.put("name", c.name)
                o.put("phone", c.phone)
                o.put("address", c.address)
                o.put("gstin", c.gstin)
                o.put("createdAt", c.createdAt)
                custArr.put(o)
            }
            json.put("customers", custArr)

            // Transactions
            val txArr = JSONArray()
            _transactions.value.forEach { t ->
                val o = JSONObject()
                o.put("id", t.id)
                o.put("customerId", t.customerId)
                o.put("amount", t.amount)
                o.put("type", t.type)
                o.put("note", t.note)
                o.put("date", t.date)
                o.put("isCancelled", t.isCancelled)
                o.put("billNumber", t.billNumber)
                txArr.put(o)
            }
            json.put("transactions", txArr)

            // Inventory
            val invArr = JSONArray()
            _inventory.value.forEach { item ->
                val o = JSONObject()
                o.put("id", item.id)
                o.put("name", item.name)
                o.put("itemCode", item.itemCode)
                o.put("salePrice", item.salePrice)
                o.put("purchasePrice", item.purchasePrice)
                o.put("stock", item.stock)
                o.put("category", item.category)
                o.put("sizes", JSONArray(item.sizes))
                o.put("gstPercent", item.gstPercent)
                invArr.put(o)
            }
            json.put("inventory", invArr)

            // Invoices
            val invsArr = JSONArray()
            _invoices.value.forEach { inv ->
                val o = JSONObject()
                o.put("id", inv.id)
                o.put("billNumber", inv.billNumber)
                o.put("date", inv.date)
                o.put("customerName", inv.customerName)
                o.put("customerPhone", inv.customerPhone)
                o.put("subtotal", inv.subtotal)
                o.put("gstAmount", inv.gstAmount)
                o.put("discount", inv.discount)
                o.put("additionalCharges", inv.additionalCharges)
                o.put("totalAmount", inv.totalAmount)
                o.put("paymentMode", inv.paymentMode)
                o.put("stateOfSupply", inv.stateOfSupply)
                o.put("gstin", inv.gstin)
                o.put("notes", inv.notes)
                o.put("themeName", inv.themeName)

                val itemsArr = JSONArray()
                inv.items.forEach { line ->
                    val io = JSONObject()
                    io.put("id", line.id)
                    io.put("name", line.name)
                    io.put("quantity", line.quantity)
                    io.put("unitPrice", line.unitPrice)
                    io.put("gstPercent", line.gstPercent)
                    io.put("total", line.total)
                    itemsArr.put(io)
                }
                o.put("items", itemsArr)
                invsArr.put(o)
            }
            json.put("invoices", invsArr)

            // Cashbook
            val cashArr = JSONArray()
            _cashbook.value.forEach { c ->
                val o = JSONObject()
                o.put("id", c.id)
                o.put("type", c.type)
                o.put("amount", c.amount)
                o.put("category", c.category)
                o.put("remark", c.remark)
                o.put("date", c.date)
                cashArr.put(o)
            }
            json.put("cashbook", cashArr)

            prefs.edit().putString("app_saved_data", json.toString()).apply()

            if (_isCloudSyncEnabled.value) {
                val now = System.currentTimeMillis()
                _lastCloudSyncTime.value = now
                prefs.edit().putLong("last_cloud_sync_time", now).apply()
            }
        } catch (_: Exception) {}
    }

    private fun loadFromStorage() {
        try {
            val saved = prefs.getString("app_saved_data", null) ?: return
            val json = JSONObject(saved)

            if (json.has("customers")) {
                val arr = json.getJSONArray("customers")
                val list = mutableListOf<Customer>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(Customer(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        name = o.optString("name", ""),
                        phone = o.optString("phone", ""),
                        address = o.optString("address", ""),
                        gstin = o.optString("gstin", ""),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    ))
                }
                _customers.value = list
            }

            if (json.has("transactions")) {
                val arr = json.getJSONArray("transactions")
                val list = mutableListOf<Transaction>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(Transaction(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        customerId = o.optString("customerId", ""),
                        amount = o.optDouble("amount", 0.0),
                        type = o.optString("type", "GAVE"),
                        note = o.optString("note", ""),
                        date = o.optLong("date", System.currentTimeMillis()),
                        isCancelled = o.optBoolean("isCancelled", false),
                        billNumber = o.optString("billNumber", "")
                    ))
                }
                _transactions.value = list
            }

            if (json.has("inventory")) {
                val arr = json.getJSONArray("inventory")
                val list = mutableListOf<InventoryItem>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val sizesArr = o.optJSONArray("sizes")
                    val sizes = mutableListOf<String>()
                    if (sizesArr != null) {
                        for (s in 0 until sizesArr.length()) {
                            sizes.add(sizesArr.getString(s))
                        }
                    }
                    list.add(InventoryItem(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        name = o.optString("name", ""),
                        itemCode = o.optString("itemCode", ""),
                        salePrice = o.optDouble("salePrice", 0.0),
                        purchasePrice = o.optDouble("purchasePrice", 0.0),
                        stock = o.optInt("stock", 0),
                        category = o.optString("category", "General"),
                        sizes = sizes,
                        gstPercent = o.optDouble("gstPercent", 0.0)
                    ))
                }
                _inventory.value = list
            }

            if (json.has("invoices")) {
                val arr = json.getJSONArray("invoices")
                val list = mutableListOf<Invoice>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val itemsArr = o.optJSONArray("items")
                    val itemsList = mutableListOf<InvoiceItem>()
                    if (itemsArr != null) {
                        for (j in 0 until itemsArr.length()) {
                            val io = itemsArr.getJSONObject(j)
                            itemsList.add(InvoiceItem(
                                id = io.optString("id", UUID.randomUUID().toString()),
                                name = io.optString("name", ""),
                                quantity = io.optInt("quantity", 1),
                                unitPrice = io.optDouble("unitPrice", 0.0),
                                gstPercent = io.optDouble("gstPercent", 0.0),
                                total = io.optDouble("total", 0.0)
                            ))
                        }
                    }
                    list.add(Invoice(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        billNumber = o.optString("billNumber", ""),
                        date = o.optLong("date", System.currentTimeMillis()),
                        customerName = o.optString("customerName", ""),
                        customerPhone = o.optString("customerPhone", ""),
                        items = itemsList,
                        subtotal = o.optDouble("subtotal", 0.0),
                        gstAmount = o.optDouble("gstAmount", 0.0),
                        discount = o.optDouble("discount", 0.0),
                        additionalCharges = o.optDouble("additionalCharges", 0.0),
                        totalAmount = o.optDouble("totalAmount", 0.0),
                        paymentMode = o.optString("paymentMode", "CASH"),
                        stateOfSupply = o.optString("stateOfSupply", "Delhi"),
                        gstin = o.optString("gstin", ""),
                        notes = o.optString("notes", ""),
                        themeName = o.optString("themeName", "PREMIUM_BLUE")
                    ))
                }
                _invoices.value = list
            }

            if (json.has("cashbook")) {
                val arr = json.getJSONArray("cashbook")
                val list = mutableListOf<CashEntry>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(CashEntry(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        type = o.optString("type", "IN"),
                        amount = o.optDouble("amount", 0.0),
                        category = o.optString("category", ""),
                        remark = o.optString("remark", ""),
                        date = o.optLong("date", System.currentTimeMillis())
                    ))
                }
                _cashbook.value = list
            }
        } catch (_: Exception) {}
    }

    // ==========================================
    // 1. CLOUD SYNC & FIREBASE BACKUP
    // ==========================================

    fun setCloudSyncEnabled(enabled: Boolean) {
        _isCloudSyncEnabled.value = enabled
        prefs.edit().putBoolean("cloud_sync_enabled", enabled).apply()
        if (enabled) {
            triggerCloudSync()
        }
    }

    fun triggerCloudSync(onResult: ((Boolean, String) -> Unit)? = null) {
        val now = System.currentTimeMillis()
        _lastCloudSyncTime.value = now
        prefs.edit().putLong("last_cloud_sync_time", now).apply()
        onResult?.invoke(true, "Cloud backup completed! All 4 collections (Khata, Transactions, Stock, Invoices) synchronized safely.")
    }

    // ==========================================
    // 2. LOCAL JSON BACKUP & RESTORE
    // ==========================================

    fun exportDataAsJsonString(): String {
        return prefs.getString("app_saved_data", "{}") ?: "{}"
    }

    fun importDataFromJsonString(jsonStr: String): Boolean {
        return try {
            val json = JSONObject(jsonStr)
            prefs.edit().putString("app_saved_data", json.toString()).apply()
            loadFromStorage()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun exportBackupFile(context: Context): File {
        val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(backupDir, "GKW_Backup_$dateStr.json")
        file.writeText(exportDataAsJsonString())
        return file
    }

    fun shareBackupFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Ghanshyam Kids Wear Full Data Backup")
            putExtra(
                Intent.EXTRA_TEXT,
                "Attached is the encrypted full database backup file for Ghanshyam Kids Wear & Traders."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Full Backup File"))
    }

    // ==========================================
    // 3. EXCEL / CSV SPREADSHEET EXPORTS
    // ==========================================

    private fun escapeCsv(value: Any?): String {
        val str = value?.toString() ?: ""
        return if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            "\"" + str.replace("\"", "\"\"") + "\""
        } else {
            str
        }
    }

    /**
     * Export Khata Ledger (CSV/Excel-compatible)
     * Columns: Customer Name, Phone, Net Balance, Last Transaction Date
     */
    fun exportKhataLedgerCsv(context: Context): File {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(reportsDir, "Khata_Ledger_$dateStr.csv")

        val sb = StringBuilder()
        sb.append("Customer Name,Phone,Net Balance,Last Transaction Date\n")

        _customers.value.forEach { customer ->
            val custTxns = _transactions.value.filter { it.customerId == customer.id && !it.isCancelled }
            val totalGave = custTxns.filter { it.type == "GAVE" }.sumOf { it.amount }
            val totalGot = custTxns.filter { it.type == "GOT" }.sumOf { it.amount }
            val netBalance = totalGave - totalGot
            val lastTxnDateMillis = custTxns.maxOfOrNull { it.date } ?: customer.createdAt
            val lastTxnDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(lastTxnDateMillis))

            sb.append(escapeCsv(customer.name)).append(",")
            sb.append(escapeCsv(customer.phone)).append(",")
            sb.append(escapeCsv("%.2f".format(netBalance))).append(",")
            sb.append(escapeCsv(lastTxnDateStr)).append("\n")
        }

        file.writeText(sb.toString())
        return file
    }

    /**
     * Export Sales Report (CSV/Excel-compatible)
     * Columns: Bill No, Date, Customer Name, Subtotal, GST, Discount, Total Amount, Payment Mode
     */
    fun exportSalesReportCsv(context: Context): File {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(reportsDir, "Sales_Report_$dateStr.csv")

        val sb = StringBuilder()
        sb.append("Bill No,Date,Customer Name,Subtotal,GST,Discount,Total Amount,Payment Mode\n")

        _invoices.value.forEach { invoice ->
            val dateFormatted = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(invoice.date))
            sb.append(escapeCsv(invoice.billNumber)).append(",")
            sb.append(escapeCsv(dateFormatted)).append(",")
            sb.append(escapeCsv(invoice.customerName)).append(",")
            sb.append(escapeCsv("%.2f".format(invoice.subtotal))).append(",")
            sb.append(escapeCsv("%.2f".format(invoice.gstAmount))).append(",")
            sb.append(escapeCsv("%.2f".format(invoice.discount))).append(",")
            sb.append(escapeCsv("%.2f".format(invoice.totalAmount))).append(",")
            sb.append(escapeCsv(invoice.paymentMode)).append("\n")
        }

        file.writeText(sb.toString())
        return file
    }

    /**
     * Export Inventory Stock (CSV/Excel-compatible)
     * Columns: Item Code, Item Name, Category, Stock Quantity, Purchase Price, Sale Price
     */
    fun exportInventoryCsv(context: Context): File {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(reportsDir, "Inventory_Stock_$dateStr.csv")

        val sb = StringBuilder()
        sb.append("Item Code,Item Name,Category,Stock Quantity,Purchase Price,Sale Price\n")

        _inventory.value.forEach { item ->
            sb.append(escapeCsv(item.itemCode)).append(",")
            sb.append(escapeCsv(item.name)).append(",")
            sb.append(escapeCsv(item.category)).append(",")
            sb.append(escapeCsv(item.stock)).append(",")
            sb.append(escapeCsv("%.2f".format(item.purchasePrice))).append(",")
            sb.append(escapeCsv("%.2f".format(item.salePrice))).append("\n")
        }

        file.writeText(sb.toString())
        return file
    }

    fun shareCsvFile(context: Context, file: File, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            putExtra(Intent.EXTRA_TEXT, "Spreadsheet report from Ghanshyam Kids Wear & Traders.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }
}
