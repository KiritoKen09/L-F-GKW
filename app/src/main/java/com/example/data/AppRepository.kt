package com.example.data

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.example.data.local.*
import com.example.model.*
import com.example.ui.util.PhoneUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AppRepository(
    val context: Context,
    val database: AppDatabase = AppDatabase.getInstance(context),
    val asyncInit: Boolean = true
) {
    private val prefs = context.getSharedPreferences("ghanshyam_billing_prefs", Context.MODE_PRIVATE)
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    private val _migrationError = MutableStateFlow<String?>(null)
    val migrationError: StateFlow<String?> = _migrationError.asStateFlow()

    private val _isMigrationBlocked = MutableStateFlow(false)
    val isMigrationBlocked: StateFlow<Boolean> = _isMigrationBlocked.asStateFlow()

    private val _migrationNotice = MutableStateFlow<String?>(null)
    val migrationNotice: StateFlow<String?> = _migrationNotice.asStateFlow()

    // Backwards compatibility alias for corruptDataBanner
    val corruptDataBanner: StateFlow<String?> = _migrationError.asStateFlow()

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

    private val _storeProfile = MutableStateFlow<StoreProfile>(StoreProfile())
    val storeProfile: StateFlow<StoreProfile> = _storeProfile.asStateFlow()

    private val _language = MutableStateFlow<AppLanguage>(AppLanguage.HINGLISH)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _selectedInvoiceTheme = MutableStateFlow<String>("PREMIUM_BLUE")
    val selectedInvoiceTheme: StateFlow<String> = _selectedInvoiceTheme.asStateFlow()

    private val _hasCompletedOnboarding = MutableStateFlow<Boolean>(false)
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessageEntity>> = _chatMessages.asStateFlow()

    init {
        val persistentError = prefs.getString("legacy_migration_error", null)
        if (!persistentError.isNullOrBlank()) {
            _migrationError.value = persistentError
            _isMigrationBlocked.value = true
        }

        if (asyncInit) {
            repositoryScope.launch(Dispatchers.IO) {
                try {
                    if (!_isMigrationBlocked.value) {
                        performOneTimeMigrationIfNeededInternal()
                    }
                    refreshAllInMemoryCachesInternal()
                } catch (e: Exception) {
                    Log.e("AppRepository", "Async startup initialization error: ${e.message}", e)
                    _migrationError.value = e.localizedMessage ?: "Startup error"
                    _isMigrationBlocked.value = true
                } finally {
                    _isInitialLoading.value = false
                }
                startRoomFlowCollectors()
            }
        } else {
            runBlocking(Dispatchers.IO) {
                if (!_isMigrationBlocked.value) {
                    performOneTimeMigrationIfNeededInternal()
                }
                refreshAllInMemoryCachesInternal()
                _isInitialLoading.value = false
                startRoomFlowCollectors()
            }
        }
    }

    suspend fun awaitInitialization() {
        isInitialLoading.first { !it }
    }

    private fun checkWritesAllowed() {
        if (_isMigrationBlocked.value) {
            throw IllegalStateException("Database operations are blocked due to an unhandled migration error: ${_migrationError.value}. Please resolve the error first.")
        }
    }

    private suspend fun refreshAllInMemoryCachesInternal() {
        _customers.value = database.customerDao().getAllList().map { it.toDomain() }
        _transactions.value = database.transactionDao().getAllList().map { it.toDomain() }
        _inventory.value = database.inventoryDao().getAllList().map { it.toDomain() }
        _invoices.value = database.invoiceDao().getAllWithItemsList().map { it.toDomain() }
        _cashbook.value = database.cashbookDao().getAllList().map { it.toDomain() }
        _storeProfile.value = database.storeProfileDao().getProfileDirect()?.toDomain() ?: StoreProfile()
        val settings = database.appSettingsDao().getSettingsDirect()
        _language.value = when (settings?.language?.lowercase()) {
            "hi", "hindi" -> AppLanguage.HINDI
            "gu", "gujarati" -> AppLanguage.GUJARATI
            "en", "english" -> AppLanguage.ENGLISH
            else -> AppLanguage.HINGLISH
        }
        _selectedInvoiceTheme.value = settings?.theme ?: "PREMIUM_BLUE"
        _hasCompletedOnboarding.value = settings?.hasCompletedOnboarding ?: false
        _chatMessages.value = database.chatMessageDao().getAllMessages().firstOrNull() ?: emptyList()
    }

    fun refreshAllInMemoryCaches() = runBlocking(Dispatchers.IO) {
        refreshAllInMemoryCachesInternal()
    }

    private fun startRoomFlowCollectors() {
        database.customerDao().getAll()
            .map { list -> list.map { it.toDomain() } }
            .onEach { _customers.value = it }
            .launchIn(repositoryScope)

        database.transactionDao().getAll()
            .map { list -> list.map { it.toDomain() } }
            .onEach { _transactions.value = it }
            .launchIn(repositoryScope)

        database.inventoryDao().getAll()
            .map { list -> list.map { it.toDomain() } }
            .onEach { _inventory.value = it }
            .launchIn(repositoryScope)

        database.invoiceDao().getAllWithItems()
            .map { list -> list.map { it.toDomain() } }
            .onEach { _invoices.value = it }
            .launchIn(repositoryScope)

        database.cashbookDao().getAll()
            .map { list -> list.map { it.toDomain() } }
            .onEach { _cashbook.value = it }
            .launchIn(repositoryScope)

        database.storeProfileDao().getProfile()
            .map { it?.toDomain() ?: StoreProfile() }
            .onEach { _storeProfile.value = it }
            .launchIn(repositoryScope)

        database.appSettingsDao().getSettings()
            .onEach { settings ->
                if (settings != null) {
                    _language.value = when (settings.language.lowercase()) {
                        "hi", "hindi" -> AppLanguage.HINDI
                        "gu", "gujarati" -> AppLanguage.GUJARATI
                        "en", "english" -> AppLanguage.ENGLISH
                        else -> AppLanguage.HINGLISH
                    }
                    _selectedInvoiceTheme.value = settings.theme
                    _hasCompletedOnboarding.value = settings.hasCompletedOnboarding
                }
            }
            .launchIn(repositoryScope)

        database.chatMessageDao().getAllMessages()
            .onEach { _chatMessages.value = it }
            .launchIn(repositoryScope)
    }

    // ==========================================
    // ONE-TIME MIGRATION (SharedPreferences -> Room)
    // ==========================================

    private suspend fun performOneTimeMigrationIfNeededInternal() {
        if (!prefs.contains("app_saved_data")) {
            ensureDefaultProfileAndSettings()
            return
        }

        val savedJson = prefs.getString("app_saved_data", null)
        if (savedJson.isNullOrBlank()) {
            prefs.edit().putString("app_saved_data_migrated", "").remove("app_saved_data").commit()
            ensureDefaultProfileAndSettings()
            return
        }

        try {
            val json = JSONObject(savedJson)
            val tempCustomers = mutableListOf<CustomerEntity>()
            val rawTransactions = mutableListOf<TransactionEntity>()
            val tempInventory = mutableListOf<InventoryItemEntity>()
            val tempInvoices = mutableListOf<InvoiceEntity>()
            val tempInvoiceItems = mutableListOf<InvoiceItemEntity>()
            val tempCashbook = mutableListOf<CashEntryEntity>()

            if (json.has("customers")) {
                val arr = json.getJSONArray("customers")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    tempCustomers.add(CustomerEntity(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        name = o.optString("name", "").trim().ifBlank { "Party" },
                        phone = PhoneUtil.normalize(o.optString("phone", "").trim()),
                        address = o.optString("address", "").trim(),
                        gstin = o.optString("gstin", "").trim(),
                        type = o.optString("type", "CUSTOMER").uppercase(),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    ))
                }
            }

            if (json.has("transactions")) {
                val arr = json.getJSONArray("transactions")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val txId = o.optString("id", UUID.randomUUID().toString())
                    val custId = o.optString("customerId", "").trim()
                    val amt = o.optDouble("amount", 0.0)
                    val txType = o.optString("type", "GAVE")
                    val dt = o.optLong("date", System.currentTimeMillis())
                    val oldIsCancelled = o.optBoolean("isCancelled", false)

                    rawTransactions.add(TransactionEntity(
                        id = txId,
                        customerId = custId,
                        amount = amt,
                        type = txType,
                        note = o.optString("note", ""),
                        date = dt,
                        paymentMode = o.optString("paymentMode", "CASH"),
                        linkedToTxId = if (o.has("linkedToTxId") && !o.isNull("linkedToTxId")) o.getString("linkedToTxId") else null,
                        isCompleted = o.optBoolean("isCompleted", oldIsCancelled),
                        billNumber = o.optString("billNumber", "")
                    ))

                    // Map old isCancelled = true on credit bills into linked payments
                    if (oldIsCancelled && txType == "GAVE") {
                        rawTransactions.add(TransactionEntity(
                            id = UUID.randomUUID().toString(),
                            customerId = custId,
                            amount = amt,
                            type = "GOT",
                            note = "Settled (migrated)",
                            date = dt,
                            paymentMode = "CASH",
                            linkedToTxId = txId,
                            isCompleted = true,
                            billNumber = ""
                        ))
                    }
                }
            }

            // Tolerant migration: attach transactions whose customerId is missing or unknown to "Recovered entries"
            val validCustIds = tempCustomers.map { it.id }.toSet()
            val repairedTransactions = mutableListOf<TransactionEntity>()
            var orphanCount = 0
            var recoveredCustomer: CustomerEntity? = tempCustomers.find { it.name.equals("Recovered entries", ignoreCase = true) }

            for (tx in rawTransactions) {
                if (tx.customerId.isBlank() || !validCustIds.contains(tx.customerId)) {
                    orphanCount++
                    if (recoveredCustomer == null) {
                        recoveredCustomer = CustomerEntity(
                            id = UUID.randomUUID().toString(),
                            name = "Recovered entries",
                            phone = "",
                            address = "Auto-created for unassigned legacy transactions"
                        )
                        tempCustomers.add(recoveredCustomer)
                    }
                    repairedTransactions.add(tx.copy(customerId = recoveredCustomer.id))
                } else {
                    repairedTransactions.add(tx)
                }
            }

            if (json.has("inventory")) {
                val arr = json.getJSONArray("inventory")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val sizesArr = o.optJSONArray("sizes")
                    val sizes = mutableListOf<String>()
                    if (sizesArr != null) {
                        for (j in 0 until sizesArr.length()) {
                            sizes.add(sizesArr.getString(j))
                        }
                    }
                    tempInventory.add(InventoryItemEntity(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        name = o.optString("name", "").trim().ifBlank { "Item" },
                        itemCode = o.optString("itemCode", "").trim(),
                        salePrice = o.optDouble("salePrice", 0.0),
                        purchasePrice = o.optDouble("purchasePrice", 0.0),
                        stock = o.optInt("stock", 0),
                        category = o.optString("category", "General").trim().ifBlank { "General" },
                        sizes = sizes,
                        gstPercent = o.optDouble("gstPercent", 0.0)
                    ))
                }
            }

            if (json.has("invoices")) {
                val arr = json.getJSONArray("invoices")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val invId = o.optString("id", UUID.randomUUID().toString())
                    val themeName = prefs.getString("invoice_theme", null)
                        ?: o.optString("themeName", "PREMIUM_BLUE")
                    tempInvoices.add(InvoiceEntity(
                        id = invId,
                        billNumber = o.optString("billNumber", "INV-${System.currentTimeMillis() % 100000}"),
                        date = o.optLong("date", System.currentTimeMillis()),
                        customerName = o.optString("customerName", ""),
                        customerPhone = o.optString("customerPhone", ""),
                        subtotal = o.optDouble("subtotal", 0.0),
                        gstAmount = o.optDouble("gstAmount", 0.0),
                        discount = o.optDouble("discount", 0.0),
                        additionalCharges = o.optDouble("additionalCharges", 0.0),
                        totalAmount = o.optDouble("totalAmount", 0.0),
                        paymentMode = o.optString("paymentMode", "CASH"),
                        stateOfSupply = o.optString("stateOfSupply", "Gujarat"),
                        gstin = o.optString("gstin", ""),
                        notes = o.optString("notes", ""),
                        themeName = themeName
                    ))

                    val itemsArr = o.optJSONArray("items")
                    if (itemsArr != null) {
                        for (j in 0 until itemsArr.length()) {
                            val io = itemsArr.getJSONObject(j)
                            tempInvoiceItems.add(InvoiceItemEntity(
                                id = io.optString("id", UUID.randomUUID().toString()),
                                invoiceId = invId,
                                name = io.optString("name", "Item"),
                                quantity = io.optInt("quantity", 1),
                                unitPrice = io.optDouble("unitPrice", 0.0),
                                gstPercent = io.optDouble("gstPercent", 0.0),
                                total = io.optDouble("total", 0.0)
                            ))
                        }
                    }
                }
            }

            if (json.has("cashbook")) {
                val arr = json.getJSONArray("cashbook")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    tempCashbook.add(CashEntryEntity(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        type = o.optString("type", "IN"),
                        amount = o.optDouble("amount", 0.0),
                        category = o.optString("category", ""),
                        remark = o.optString("remark", ""),
                        date = o.optLong("date", System.currentTimeMillis()),
                        paymentMode = o.optString("paymentMode", "CASH"),
                        linkedId = if (o.has("linkedId") && !o.isNull("linkedId")) o.getString("linkedId") else null,
                        source = o.optString("source", "manual")
                    ))
                }
            }

            val storeProfile = StoreProfileEntity(
                id = 1,
                storeName = prefs.getString("store_name", "") ?: "",
                address = prefs.getString("store_address", "") ?: "",
                phone = prefs.getString("store_phone", "") ?: "",
                upiId = prefs.getString("store_upi", "") ?: "",
                gstin = prefs.getString("store_gstin", "") ?: "",
                ownerName = prefs.getString("owner_name", "") ?: "",
                state = prefs.getString("store_state", "Gujarat") ?: "Gujarat"
            )

            val invoiceThemePref = prefs.getString("invoice_theme", null)
                ?: prefs.getString("selected_invoice_theme", "PREMIUM_BLUE")
                ?: "PREMIUM_BLUE"

            val appSettings = AppSettingsEntity(
                id = 1,
                language = prefs.getString("selected_language", "hinglish") ?: "hinglish",
                theme = invoiceThemePref,
                hasCompletedOnboarding = prefs.getBoolean("has_completed_onboarding", false)
            )

            // Insert everything inside ONE atomic transaction
            database.withTransaction {
                database.customerDao().insertAll(tempCustomers)
                database.transactionDao().insertAll(repairedTransactions)
                database.inventoryDao().insertAll(tempInventory)
                database.invoiceDao().insertInvoices(tempInvoices)
                database.invoiceDao().insertItems(tempInvoiceItems)
                database.cashbookDao().insertAll(tempCashbook)
                database.storeProfileDao().insertOrUpdate(storeProfile)
                database.appSettingsDao().insertOrUpdate(appSettings)
            }

            // Only after success rename the old key to app_saved_data_migrated (do not delete it)
            prefs.edit()
                .putString("app_saved_data_migrated", savedJson)
                .remove("app_saved_data")
                .remove("legacy_migration_error")
                .commit()

            _migrationError.value = null
            _isMigrationBlocked.value = false

            if (orphanCount > 0) {
                _migrationNotice.value = "Migration recovered $orphanCount unlinked transaction(s) and attached them to 'Recovered entries'."
            }

            Log.i("AppRepository", "Migration to Room succeeded.")
        } catch (e: Exception) {
            Log.e("AppRepository", "Error during one-time migration: ${e.message}", e)
            val timestamp = System.currentTimeMillis()
            prefs.edit()
                .putString("legacy_migration_error", e.localizedMessage ?: "Migration error")
                .putString("app_saved_data_corrupt_$timestamp", savedJson)
                .commit()
            // CRITICAL: keep app_saved_data UNTOUCHED
            _migrationError.value = e.localizedMessage ?: "Data migration error detected"
            _isMigrationBlocked.value = true
        }
    }

    fun performOneTimeMigrationIfNeeded() = runBlocking(Dispatchers.IO) {
        performOneTimeMigrationIfNeededInternal()
    }

    fun retryMigration() {
        repositoryScope.launch(Dispatchers.IO) {
            _migrationError.value = null
            _isMigrationBlocked.value = false
            prefs.edit().remove("legacy_migration_error").commit()
            performOneTimeMigrationIfNeededInternal()
            refreshAllInMemoryCachesInternal()
        }
    }

    fun getDamagedLegacyJson(): String {
        val currentSaved = prefs.getString("app_saved_data", null)
        if (!currentSaved.isNullOrBlank()) return currentSaved
        val corruptKey = prefs.all.keys.filter { it.startsWith("app_saved_data_corrupt_") }.maxOrNull()
        if (corruptKey != null) {
            return prefs.getString(corruptKey, "") ?: ""
        }
        return ""
    }

    fun startFreshFromDamagedData() {
        val raw = getDamagedLegacyJson()
        val timestamp = System.currentTimeMillis()
        prefs.edit()
            .putString("app_saved_data_discarded_$timestamp", raw)
            .remove("app_saved_data")
            .remove("legacy_migration_error")
            .commit()
        _migrationError.value = null
        _isMigrationBlocked.value = false
        clearAllData()
    }

    fun dismissMigrationNotice() {
        _migrationNotice.value = null
    }

    private suspend fun ensureDefaultProfileAndSettings() {
        val existingProfile = database.storeProfileDao().getProfileDirect()
        if (existingProfile == null) {
            database.storeProfileDao().insertOrUpdate(
                StoreProfileEntity(
                    id = 1,
                    storeName = prefs.getString("store_name", "") ?: "",
                    address = prefs.getString("store_address", "") ?: "",
                    phone = prefs.getString("store_phone", "") ?: "",
                    upiId = prefs.getString("store_upi", "") ?: "",
                    gstin = prefs.getString("store_gstin", "") ?: "",
                    ownerName = prefs.getString("owner_name", "") ?: "",
                    state = prefs.getString("store_state", "Gujarat") ?: "Gujarat"
                )
            )
        }

        val existingSettings = database.appSettingsDao().getSettingsDirect()
        if (existingSettings == null) {
            val invoiceThemePref = prefs.getString("invoice_theme", null)
                ?: prefs.getString("selected_invoice_theme", "PREMIUM_BLUE")
                ?: "PREMIUM_BLUE"

            database.appSettingsDao().insertOrUpdate(
                AppSettingsEntity(
                    id = 1,
                    language = prefs.getString("selected_language", "hinglish") ?: "hinglish",
                    theme = invoiceThemePref,
                    hasCompletedOnboarding = prefs.getBoolean("has_completed_onboarding", false)
                )
            )
        }
    }

    fun dismissCorruptDataBanner() {
        _migrationNotice.value = null
    }

    fun startFreshAfterCorruption() {
        startFreshFromDamagedData()
    }

    // ==========================================
    // STORE PROFILE & SETTINGS
    // ==========================================

    fun updateStoreProfile(
        storeName: String,
        address: String,
        phone: String,
        upiId: String,
        gstin: String,
        ownerName: String,
        state: String = "Gujarat",
        logoUri: String? = _storeProfile.value.logoUri
    ) {
        checkWritesAllowed()
        val profile = StoreProfileEntity(
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
        runBlocking(Dispatchers.IO) {
            database.storeProfileDao().insertOrUpdate(profile)
            _storeProfile.value = profile.toDomain()
        }
    }

    fun setLanguage(newLanguage: AppLanguage) {
        checkWritesAllowed()
        runBlocking(Dispatchers.IO) {
            val current = database.appSettingsDao().getSettingsDirect() ?: AppSettingsEntity(id = 1)
            database.appSettingsDao().insertOrUpdate(current.copy(language = newLanguage.code))
            _language.value = newLanguage
        }
    }

    fun setInvoiceTheme(theme: String) {
        checkWritesAllowed()
        runBlocking(Dispatchers.IO) {
            val current = database.appSettingsDao().getSettingsDirect() ?: AppSettingsEntity(id = 1)
            database.appSettingsDao().insertOrUpdate(current.copy(theme = theme))
            _selectedInvoiceTheme.value = theme
        }
    }

    fun completeOnboarding() {
        checkWritesAllowed()
        runBlocking(Dispatchers.IO) {
            val current = database.appSettingsDao().getSettingsDirect() ?: AppSettingsEntity(id = 1)
            database.appSettingsDao().insertOrUpdate(current.copy(hasCompletedOnboarding = true))
            _hasCompletedOnboarding.value = true
        }
    }

    // ==========================================
    // CUSTOMER OPERATIONS
    // ==========================================

    fun addCustomer(
        name: String,
        phone: String,
        address: String = "",
        gstin: String = "",
        type: String = "CUSTOMER"
    ): Customer {
        checkWritesAllowed()
        val normalizedPhone = PhoneUtil.normalize(phone)
        val newCustomer = CustomerEntity(
            name = name.trim(),
            phone = normalizedPhone,
            address = address.trim(),
            gstin = gstin.trim(),
            type = type.uppercase()
        )
        runBlocking(Dispatchers.IO) {
            database.customerDao().insert(newCustomer)
            _customers.value = database.customerDao().getAllList().map { it.toDomain() }
        }
        return newCustomer.toDomain()
    }

    fun addCustomer(customer: Customer): Customer {
        checkWritesAllowed()
        val normalizedPhone = PhoneUtil.normalize(customer.phone)
        val custId = if (customer.id.isNotBlank()) customer.id else UUID.randomUUID().toString()
        val newCustomer = CustomerEntity(
            id = custId,
            name = customer.name.trim(),
            phone = normalizedPhone,
            address = customer.address.trim(),
            gstin = customer.gstin.trim(),
            type = customer.type.uppercase(),
            createdAt = if (customer.createdAt > 0L) customer.createdAt else System.currentTimeMillis()
        )
        runBlocking(Dispatchers.IO) {
            database.customerDao().insert(newCustomer)
            _customers.value = database.customerDao().getAllList().map { it.toDomain() }
        }
        return newCustomer.toDomain()
    }

    fun updateCustomer(
        id: String,
        name: String,
        phone: String,
        address: String = "",
        gstin: String = "",
        type: String = "CUSTOMER"
    ) {
        checkWritesAllowed()
        val normalizedPhone = PhoneUtil.normalize(phone)
        runBlocking(Dispatchers.IO) {
            val current = database.customerDao().getById(id)
            if (current != null) {
                database.customerDao().update(
                    current.copy(
                        name = name.trim(),
                        phone = normalizedPhone,
                        address = address.trim(),
                        gstin = gstin.trim(),
                        type = type.uppercase()
                    )
                )
                _customers.value = database.customerDao().getAllList().map { it.toDomain() }
            }
        }
    }

    fun deleteCustomer(customerId: String) {
        checkWritesAllowed()
        runBlocking(Dispatchers.IO) {
            database.withTransaction {
                val txs = database.transactionDao().getByCustomerId(customerId)
                val txIds = txs.map { it.id }
                if (txIds.isNotEmpty()) {
                    database.cashbookDao().deleteByLinkedIds(txIds)
                }
                database.transactionDao().deleteByCustomerId(customerId)
                database.customerDao().deleteById(customerId)
            }
            _customers.value = database.customerDao().getAllList().map { it.toDomain() }
            _transactions.value = database.transactionDao().getAllList().map { it.toDomain() }
            _cashbook.value = database.cashbookDao().getAllList().map { it.toDomain() }
        }
    }

    fun getCustomerBalance(customerId: String): Double {
        val txs = transactions.value.filter { it.customerId == customerId }
        val gave = txs.filter { it.type == "GAVE" }.sumOf { it.amount }
        val got = txs.filter { it.type == "GOT" }.sumOf { it.amount }
        return gave - got
    }

    fun getTotalReceivable(): Double {
        return customers.value.sumOf { getCustomerBalance(it.id).coerceAtLeast(0.0) }
    }

    fun getTotalPayable(): Double {
        return customers.value.sumOf { (-getCustomerBalance(it.id)).coerceAtLeast(0.0) }
    }

    fun getBillReceivedAmount(billId: String): Double {
        return transactions.value
            .filter { it.linkedToTxId == billId }
            .sumOf { it.amount }
    }

    fun getBillOutstandingAmount(bill: Transaction): Double {
        val received = getBillReceivedAmount(bill.id)
        return (bill.amount - received).coerceAtLeast(0.0)
    }

    enum class BillStatus {
        PENDING,
        PARTLY_RECEIVED,
        RECEIVED
    }

    fun getBillStatus(bill: Transaction): BillStatus {
        val received = getBillReceivedAmount(bill.id)
        return when {
            received >= bill.amount - 0.001 -> BillStatus.RECEIVED
            received > 0.001 -> BillStatus.PARTLY_RECEIVED
            else -> BillStatus.PENDING
        }
    }

    // ==========================================
    // TRANSACTION OPERATIONS
    // ==========================================

    fun addTransaction(
        customerId: String,
        amount: Double,
        type: String, // "GAVE" or "GOT"
        note: String = "",
        billNumber: String = "",
        paymentMode: String = "CASH",
        linkedToTxId: String? = null,
        isCompleted: Boolean = false,
        date: Long = System.currentTimeMillis()
    ): Transaction {
        checkWritesAllowed()
        val newTx = TransactionEntity(
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
        runBlocking(Dispatchers.IO) {
            database.transactionDao().insert(newTx)
            _transactions.value = database.transactionDao().getAllList().map { it.toDomain() }
        }
        return newTx.toDomain()
    }

    fun createTransaction(
        customerId: String,
        amount: Double,
        type: String,
        note: String = "",
        paymentMode: String = "CASH",
        linkedToTxId: String? = null,
        isCompleted: Boolean = false,
        date: Long = System.currentTimeMillis()
    ): Transaction = addTransaction(
        customerId = customerId,
        amount = amount,
        type = type,
        note = note,
        billNumber = "",
        paymentMode = paymentMode,
        linkedToTxId = linkedToTxId,
        isCompleted = isCompleted,
        date = date
    )

    fun markBillReceived(
        billId: String,
        amount: Double,
        paymentMode: String = "CASH",
        date: Long = System.currentTimeMillis(),
        addToCashbook: Boolean = true
    ): Transaction? {
        checkWritesAllowed()
        return runBlocking(Dispatchers.IO) {
            database.withTransaction {
                val bill = database.transactionDao().getById(billId) ?: return@withTransaction null
                val customer = database.customerDao().getById(bill.customerId)
                val cleanAmount = amount.coerceAtLeast(0.01)

                // For CUSTOMER credit bill (GAVE), payment received is GOT
                // For SUPPLIER credit bill (GOT), payment to supplier is GAVE
                val paymentType = if (customer?.type == "SUPPLIER") "GAVE" else "GOT"
                val paymentTxId = UUID.randomUUID().toString()

                val paymentTx = TransactionEntity(
                    id = paymentTxId,
                    customerId = bill.customerId,
                    amount = cleanAmount,
                    type = paymentType,
                    note = if (bill.billNumber.isNotBlank()) "Received for Bill ${bill.billNumber}" else "Received for bill",
                    date = date,
                    paymentMode = paymentMode,
                    linkedToTxId = billId,
                    isCompleted = true,
                    billNumber = ""
                )
                database.transactionDao().insert(paymentTx)

                // Payment status is derived dynamically via getBillStatus from linked payments

                // Add to Cashbook if requested
                if (addToCashbook) {
                    val cashType = if (paymentType == "GOT") "IN" else "OUT"
                    val partyLabel = customer?.name ?: "Party"
                    val remark = if (cashType == "IN") "Received from $partyLabel" else "Paid to $partyLabel"
                    database.cashbookDao().insert(
                        CashEntryEntity(
                            type = cashType,
                            amount = cleanAmount,
                            category = "Khata Settlement",
                            remark = remark,
                            date = date,
                            paymentMode = paymentMode,
                            linkedId = paymentTxId,
                            source = "khata"
                        )
                    )
                }

                paymentTx.toDomain()
            }.also {
                _transactions.value = database.transactionDao().getAllList().map { it.toDomain() }
                _cashbook.value = database.cashbookDao().getAllList().map { it.toDomain() }
            }
        }
    }

    fun markBillsAllReceived(
        billIds: List<String>,
        paymentMode: String = "CASH",
        addToCashbook: Boolean = true
    ) {
        checkWritesAllowed()
        runBlocking(Dispatchers.IO) {
            database.withTransaction {
                for (billId in billIds) {
                    val bill = database.transactionDao().getById(billId) ?: continue
                    val received = database.transactionDao().getByLinkedToTxId(billId).sumOf { it.amount }
                    val outstanding = bill.amount - received
                    if (outstanding > 0.01) {
                        val customer = database.customerDao().getById(bill.customerId)
                        val paymentType = if (customer?.type == "SUPPLIER") "GAVE" else "GOT"
                        val paymentTxId = UUID.randomUUID().toString()

                        val paymentTx = TransactionEntity(
                            id = paymentTxId,
                            customerId = bill.customerId,
                            amount = outstanding,
                            type = paymentType,
                            note = if (bill.billNumber.isNotBlank()) "Received for Bill ${bill.billNumber}" else "Received for bill",
                            date = System.currentTimeMillis(),
                            paymentMode = paymentMode,
                            linkedToTxId = billId,
                            isCompleted = true,
                            billNumber = ""
                        )
                        database.transactionDao().insert(paymentTx)
                        database.transactionDao().update(bill.copy(isCompleted = true))

                        if (addToCashbook) {
                            val cashType = if (paymentType == "GOT") "IN" else "OUT"
                            val partyLabel = customer?.name ?: "Party"
                            val remark = if (cashType == "IN") "Received from $partyLabel" else "Paid to $partyLabel"
                            database.cashbookDao().insert(
                                CashEntryEntity(
                                    type = cashType,
                                    amount = outstanding,
                                    category = "Khata Settlement",
                                    remark = remark,
                                    date = System.currentTimeMillis(),
                                    paymentMode = paymentMode,
                                    linkedId = paymentTxId,
                                    source = "khata"
                                )
                            )
                        }
                    }
                }
            }
            _transactions.value = database.transactionDao().getAllList().map { it.toDomain() }
            _cashbook.value = database.cashbookDao().getAllList().map { it.toDomain() }
        }
    }

    fun undoPayment(paymentTxId: String) {
        checkWritesAllowed()
        runBlocking(Dispatchers.IO) {
            database.withTransaction {
                val payment = database.transactionDao().getById(paymentTxId) ?: return@withTransaction
                val billId = payment.linkedToTxId

                database.cashbookDao().deleteByLinkedId(paymentTxId)
                database.transactionDao().deleteById(paymentTxId)

                if (!billId.isNullOrBlank()) {
                    val bill = database.transactionDao().getById(billId)
                    if (bill != null) {
                        val remainingReceived = database.transactionDao().getByLinkedToTxId(billId).sumOf { it.amount }
                        if (remainingReceived < bill.amount - 0.001) {
                            database.transactionDao().update(bill.copy(isCompleted = false))
                        }
                    }
                }
            }
            _transactions.value = database.transactionDao().getAllList().map { it.toDomain() }
            _cashbook.value = database.cashbookDao().getAllList().map { it.toDomain() }
        }
    }

    fun toggleBillCompleted(billId: String) {
        checkWritesAllowed()
        runBlocking(Dispatchers.IO) {
            val bill = database.transactionDao().getById(billId) ?: return@runBlocking
            database.transactionDao().update(bill.copy(isCompleted = !bill.isCompleted))
            _transactions.value = database.transactionDao().getAllList().map { it.toDomain() }
        }
    }

    fun editTransaction(
        txId: String,
        amount: Double,
        note: String,
        date: Long,
        paymentMode: String
    ) {
        checkWritesAllowed()
        runBlocking(Dispatchers.IO) {
            database.withTransaction {
                val current = database.transactionDao().getById(txId) ?: return@withTransaction
                val updated = current.copy(
                    amount = amount.coerceAtLeast(0.01),
                    note = note.trim(),
                    date = date,
                    paymentMode = paymentMode
                )
                database.transactionDao().update(updated)

                val linkedCash = database.cashbookDao().getAllList().find { it.linkedId == txId }
                if (linkedCash != null) {
                    database.cashbookDao().insert(
                        linkedCash.copy(
                            amount = updated.amount,
                            remark = if (updated.note.isNotBlank()) updated.note else linkedCash.remark,
                            date = updated.date,
                            paymentMode = updated.paymentMode
                        )
                    )
                }
            }
            _transactions.value = database.transactionDao().getAllList().map { it.toDomain() }
            _cashbook.value = database.cashbookDao().getAllList().map { it.toDomain() }
        }
    }

    fun deleteTransaction(transactionId: String) {
        checkWritesAllowed()
        runBlocking(Dispatchers.IO) {
            database.withTransaction {
                database.cashbookDao().deleteByLinkedId(transactionId)
                database.transactionDao().deleteById(transactionId)
            }
            _transactions.value = database.transactionDao().getAllList().map { it.toDomain() }
            _cashbook.value = database.cashbookDao().getAllList().map { it.toDomain() }
        }
    }

    // ==========================================
    // INVENTORY OPERATIONS
    // ==========================================

    // ==========================================
    // BILL NUMBERING (Consecutive Financial Year Series)
    // ==========================================

    fun getFinancialYear(timestamp: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) // 0-indexed: April is Calendar.APRIL (3)
        val startYear = if (month >= Calendar.APRIL) year else year - 1
        val startYY = (startYear % 100).toString().padStart(2, '0')
        val endYY = ((startYear + 1) % 100).toString().padStart(2, '0')
        return "$startYY-$endYY"
    }

    fun getNextBillNumberPreview(timestamp: Long = System.currentTimeMillis()): String {
        val fy = getFinancialYear(timestamp)
        val lastSeq = runBlocking(Dispatchers.IO) { database.invoiceSequenceDao().getLastSequence(fy) ?: 0 }
        val nextSeq = lastSeq + 1
        return "GKW/$fy/${nextSeq.toString().padStart(4, '0')}"
    }

    // ==========================================
    // INVENTORY OPERATIONS
    // ==========================================

    fun addInventoryItem(
        name: String,
        itemCode: String = "",
        hsnCode: String = "",
        salePrice: Double,
        purchasePrice: Double = 0.0,
        stock: Int = 0,
        sizeStock: Map<String, Int> = emptyMap(),
        category: String = "General",
        sizes: List<String> = emptyList(),
        gstPercent: Double = 0.0,
        lowStockThreshold: Int = 5,
        packSize: Int = 1,
        packRatio: Map<String, Int> = emptyMap()
    ): InventoryItem {
        checkWritesAllowed()
        val resolvedSizeStock = if (sizeStock.isNotEmpty()) {
            sizeStock
        } else if (sizes.isNotEmpty()) {
            val perSize = stock / sizes.size
            val rem = stock % sizes.size
            sizes.mapIndexed { idx, s -> s to (perSize + if (idx == 0) rem else 0) }.toMap()
        } else {
            mapOf("Free" to stock)
        }
        val newItem = InventoryItemEntity(
            name = name.trim(),
            itemCode = itemCode.trim(),
            hsnCode = hsnCode.trim(),
            salePrice = salePrice,
            purchasePrice = purchasePrice,
            stock = resolvedSizeStock.values.sum(),
            sizeStock = resolvedSizeStock,
            category = category.trim().ifBlank { "General" },
            sizes = resolvedSizeStock.keys.toList(),
            gstPercent = gstPercent,
            lowStockThreshold = lowStockThreshold,
            packSize = packSize,
            packRatio = packRatio
        )
        runBlocking(Dispatchers.IO) {
            database.inventoryDao().insert(newItem)
            _inventory.value = database.inventoryDao().getAllList().map { it.toDomain() }
        }
        return newItem.toDomain()
    }

    fun updateInventoryStock(itemId: String, stockDelta: Int) {
        checkWritesAllowed()
        runBlocking(Dispatchers.IO) {
            val current = database.inventoryDao().getById(itemId)
            if (current != null) {
                val map = current.sizeStock.toMutableMap()
                val targetKey = if (map.containsKey("Free")) "Free" else map.keys.firstOrNull() ?: "Free"
                val curQty = map[targetKey] ?: current.stock
                map[targetKey] = (curQty + stockDelta).coerceAtLeast(0)
                val updated = current.copy(
                    sizeStock = map,
                    stock = map.values.sum(),
                    sizes = map.keys.toList()
                )
                database.inventoryDao().update(updated)
                _inventory.value = database.inventoryDao().getAllList().map { it.toDomain() }
            }
        }
    }

    fun updateInventorySizeStock(itemId: String, size: String, delta: Int) {
        checkWritesAllowed()
        runBlocking(Dispatchers.IO) {
            val current = database.inventoryDao().getById(itemId)
            if (current != null) {
                val map = current.sizeStock.toMutableMap()
                val targetKey = if (map.containsKey(size)) size else if (map.containsKey("Free")) "Free" else size
                val curQty = map[targetKey] ?: 0
                map[targetKey] = (curQty + delta).coerceAtLeast(0)
                val updated = current.copy(
                    sizeStock = map,
                    stock = map.values.sum(),
                    sizes = map.keys.toList()
                )
                database.inventoryDao().update(updated)
                _inventory.value = database.inventoryDao().getAllList().map { it.toDomain() }
            }
        }
    }

    fun deleteInventoryItem(itemId: String) {
        checkWritesAllowed()
        runBlocking(Dispatchers.IO) {
            database.inventoryDao().deleteById(itemId)
            _inventory.value = database.inventoryDao().getAllList().map { it.toDomain() }
        }
    }

    // ==========================================
    // INVOICE OPERATIONS (All-or-Nothing Transaction)
    // ==========================================

    fun createInvoice(invoice: Invoice): Invoice {
        checkWritesAllowed()
        var createdInvoice: Invoice = invoice
        runBlocking(Dispatchers.IO) {
            database.withTransaction {
                val storeProfile = database.storeProfileDao().getProfileDirect()?.toDomain() ?: StoreProfile()
                val isInterState = !storeProfile.state.trim().equals(invoice.stateOfSupply.trim(), ignoreCase = true)

                // 1. Consecutive Bill Number Allocation inside transaction
                val finalBillNumber = if (invoice.billNumber.isBlank() || invoice.billNumber.startsWith("INV-")) {
                    val fy = getFinancialYear(invoice.date)
                    val lastSeq = database.invoiceSequenceDao().getLastSequence(fy) ?: 0
                    val nextSeq = lastSeq + 1
                    database.invoiceSequenceDao().insertOrUpdate(InvoiceSequenceEntity(fy, nextSeq))
                    "GKW/$fy/${nextSeq.toString().padStart(4, '0')}"
                } else {
                    // If caller specified a GKW format, track sequence
                    if (invoice.billNumber.startsWith("GKW/")) {
                        val parts = invoice.billNumber.split("/")
                        if (parts.size >= 3) {
                            val fy = parts[1]
                            val num = parts[2].toIntOrNull() ?: 0
                            val currentSeq = database.invoiceSequenceDao().getLastSequence(fy) ?: 0
                            if (num > currentSeq) {
                                database.invoiceSequenceDao().insertOrUpdate(InvoiceSequenceEntity(fy, num))
                            }
                        }
                    }
                    invoice.billNumber
                }

                // 2. Validate and reduce stock per size
                val currentInventory = database.inventoryDao().getAllList().associateBy { it.id }.toMutableMap()
                val updatedItemsList = mutableListOf<InvoiceItem>()

                for (line in invoice.items) {
                    val matching = if (!line.itemId.isNullOrBlank()) {
                        currentInventory[line.itemId]
                    } else {
                        currentInventory.values.find { it.name.equals(line.name, ignoreCase = true) }
                    }

                    if (matching != null) {
                        val sizeStockMap = matching.sizeStock.toMutableMap()
                        if (line.size == "Pack" && matching.packRatio.isNotEmpty()) {
                            // Wholesale pack selling
                            matching.packRatio.forEach { (sz, ratio) ->
                                val needed = ratio * line.quantity
                                val available = sizeStockMap[sz] ?: 0
                                if (available < needed) {
                                    throw IllegalStateException("Insufficient stock for ${matching.name} size $sz. Available: $available, required: $needed")
                                }
                                sizeStockMap[sz] = available - needed
                            }
                        } else {
                            // Specific size or Free size
                            val targetKey = if (sizeStockMap.containsKey(line.size)) line.size else if (sizeStockMap.containsKey("Free")) "Free" else line.size
                            val available = sizeStockMap[targetKey] ?: matching.stock
                            if (available < line.quantity) {
                                throw IllegalStateException("Insufficient stock for ${matching.name} size ${line.size}. Available: $available, required: ${line.quantity}")
                            }
                            sizeStockMap[targetKey] = (available - line.quantity).coerceAtLeast(0)
                        }

                        val updatedItem = matching.copy(
                            sizeStock = sizeStockMap,
                            stock = sizeStockMap.values.sum(),
                            sizes = sizeStockMap.keys.toList()
                        )
                        currentInventory[matching.id] = updatedItem
                        database.inventoryDao().update(updatedItem)

                        // Attach itemId and HSN code to invoice line
                        updatedItemsList.add(
                            line.copy(
                                itemId = matching.id,
                                hsnCode = if (line.hsnCode.isNotBlank()) line.hsnCode else matching.hsnCode
                            ).withGstSplit(isInterState)
                        )
                    } else {
                        updatedItemsList.add(line.withGstSplit(isInterState))
                    }
                }

                // 3. Compute GST totals and round-off
                val subtotal = updatedItemsList.sumOf { it.taxableAmount }
                val cgst = updatedItemsList.sumOf { it.cgstAmount }
                val sgst = updatedItemsList.sumOf { it.sgstAmount }
                val igst = updatedItemsList.sumOf { it.igstAmount }
                val totalGst = cgst + sgst + igst
                val exactTotal = (subtotal + totalGst + invoice.additionalCharges - invoice.discount).coerceAtLeast(0.0)
                val roundedTotal = Math.round(exactTotal).toDouble()
                val roundOff = roundedTotal - exactTotal

                // 4. Customer Link / Inline creation
                var customerId = invoice.customerId
                if (customerId.isNullOrBlank() && invoice.customerName.isNotBlank() && !invoice.customerName.equals("Cash Customer", ignoreCase = true)) {
                    var customer = database.customerDao().getAllList().find {
                        it.name.equals(invoice.customerName, ignoreCase = true) ||
                        (invoice.customerPhone.isNotBlank() && it.phone == PhoneUtil.normalize(invoice.customerPhone))
                    }
                    if (customer == null) {
                        customer = CustomerEntity(
                            name = invoice.customerName.trim(),
                            phone = PhoneUtil.normalize(invoice.customerPhone.trim()),
                            gstin = invoice.customerGstin.ifBlank { invoice.gstin }
                        )
                        database.customerDao().insert(customer)
                    }
                    customerId = customer.id
                }

                // 5. Build final invoice entity
                val finalInv = invoice.copy(
                    billNumber = finalBillNumber,
                    customerId = customerId,
                    items = updatedItemsList,
                    subtotal = subtotal,
                    gstAmount = totalGst,
                    cgstAmount = cgst,
                    sgstAmount = sgst,
                    igstAmount = igst,
                    isInterState = isInterState,
                    roundOff = roundOff,
                    totalAmount = roundedTotal,
                    isCancelled = false
                )
                createdInvoice = finalInv

                database.invoiceDao().insertInvoice(finalInv.toEntity())
                database.invoiceDao().insertItems(updatedItemsList.map { it.toEntity(finalInv.id) })

                // 6. Payment handling: CASH/UPI posts ONE Cash IN; CREDIT posts to khata
                if (finalInv.paymentMode == "CASH" || finalInv.paymentMode == "UPI") {
                    database.cashbookDao().insert(
                        CashEntryEntity(
                            type = "IN",
                            amount = finalInv.totalAmount,
                            category = "Sales",
                            remark = "Bill #${finalInv.billNumber} (${if (finalInv.customerName.isNotBlank()) finalInv.customerName else "Cash Customer"})",
                            paymentMode = finalInv.paymentMode,
                            linkedId = finalInv.id,
                            source = "invoice"
                        )
                    )
                } else if (finalInv.paymentMode == "CREDIT" && !customerId.isNullOrBlank()) {
                    database.transactionDao().insert(
                        TransactionEntity(
                            customerId = customerId,
                            amount = finalInv.totalAmount,
                            type = "GAVE",
                            note = "Bill #${finalInv.billNumber}",
                            billNumber = finalInv.billNumber
                        )
                    )
                }
            }
            _invoices.value = database.invoiceDao().getAllWithItemsList().map { it.toDomain() }
            _inventory.value = database.inventoryDao().getAllList().map { it.toDomain() }
            _cashbook.value = database.cashbookDao().getAllList().map { it.toDomain() }
            _transactions.value = database.transactionDao().getAllList().map { it.toDomain() }
            _customers.value = database.customerDao().getAllList().map { it.toDomain() }
        }
        return createdInvoice
    }

    fun cancelInvoice(invoiceId: String, reason: String) {
        checkWritesAllowed()
        runBlocking(Dispatchers.IO) {
            database.withTransaction {
                val invWithItems = database.invoiceDao().getWithItemsById(invoiceId) ?: return@withTransaction
                val invoice = invWithItems.invoice
                if (invoice.isCancelled) return@withTransaction

                // 1. Mark invoice as cancelled (keeping its number)
                val updatedInvoice = invoice.copy(
                    isCancelled = true,
                    cancelReason = reason.ifBlank { "Cancelled by user" },
                    cancelledAt = System.currentTimeMillis()
                )
                database.invoiceDao().updateInvoice(updatedInvoice)

                // 2. Restore stock by itemId and size
                val currentInventory = database.inventoryDao().getAllList().associateBy { it.id }.toMutableMap()
                invWithItems.items.forEach { line ->
                    val matching = if (!line.itemId.isNullOrBlank()) {
                        currentInventory[line.itemId]
                    } else {
                        currentInventory.values.find { it.name.equals(line.name, ignoreCase = true) }
                    }

                    if (matching != null) {
                        val sizeStockMap = matching.sizeStock.toMutableMap()
                        if (line.size == "Pack" && matching.packRatio.isNotEmpty()) {
                            matching.packRatio.forEach { (sz, ratio) ->
                                val cur = sizeStockMap[sz] ?: 0
                                sizeStockMap[sz] = cur + (ratio * line.quantity)
                            }
                        } else {
                            val targetKey = if (sizeStockMap.containsKey(line.size)) line.size else if (sizeStockMap.containsKey("Free")) "Free" else line.size
                            val cur = sizeStockMap[targetKey] ?: 0
                            sizeStockMap[targetKey] = cur + line.quantity
                        }
                        val restored = matching.copy(
                            sizeStock = sizeStockMap,
                            stock = sizeStockMap.values.sum(),
                            sizes = sizeStockMap.keys.toList()
                        )
                        currentInventory[matching.id] = restored
                        database.inventoryDao().update(restored)
                    }
                }

                // 3. Remove linked cashbook entry for bill
                database.cashbookDao().deleteByLinkedId(invoice.id)

                // 4. Remove linked credit transaction and any payments linked to it
                if (invoice.billNumber.isNotBlank()) {
                    val txs = database.transactionDao().getByBillNumber(invoice.billNumber)
                    val txIds = txs.map { it.id }
                    if (txIds.isNotEmpty()) {
                        // Delete any cashbook entries created for payments on this bill
                        database.cashbookDao().deleteByLinkedIds(txIds)
                        // Delete payment transactions that paid towards this bill
                        for (txId in txIds) {
                            val payments = database.transactionDao().getByLinkedToTxId(txId)
                            val pIds = payments.map { it.id }
                            if (pIds.isNotEmpty()) {
                                database.cashbookDao().deleteByLinkedIds(pIds)
                            }
                            database.transactionDao().deleteByLinkedToTxId(txId)
                        }
                    }
                    database.transactionDao().deleteByBillNumber(invoice.billNumber)
                }
            }
            _invoices.value = database.invoiceDao().getAllWithItemsList().map { it.toDomain() }
            _inventory.value = database.inventoryDao().getAllList().map { it.toDomain() }
            _cashbook.value = database.cashbookDao().getAllList().map { it.toDomain() }
            _transactions.value = database.transactionDao().getAllList().map { it.toDomain() }
        }
    }

    fun deleteInvoice(invoiceId: String) {
        // Delegate to cancelInvoice to satisfy "never delete a numbered invoice" rule
        cancelInvoice(invoiceId, "Deleted")
    }

    // ==========================================
    // CASHBOOK OPERATIONS
    // ==========================================

    fun addCashEntry(
        type: String, // "IN" or "OUT"
        amount: Double,
        category: String,
        remark: String = "",
        paymentMode: String = "CASH",
        linkedId: String? = null,
        source: String = "manual"
    ): CashEntry {
        checkWritesAllowed()
        val entry = CashEntryEntity(
            type = type,
            amount = amount,
            category = category,
            remark = remark,
            paymentMode = paymentMode,
            linkedId = linkedId,
            source = source
        )
        runBlocking(Dispatchers.IO) {
            database.cashbookDao().insert(entry)
            _cashbook.value = database.cashbookDao().getAllList().map { it.toDomain() }
        }
        return entry.toDomain()
    }

    fun deleteCashEntry(id: String) {
        checkWritesAllowed()
        runBlocking(Dispatchers.IO) {
            database.cashbookDao().deleteById(id)
            _cashbook.value = database.cashbookDao().getAllList().map { it.toDomain() }
        }
    }

    fun getCashInHand(): Double {
        val totalIn = cashbook.value.filter { it.type.equals("IN", ignoreCase = true) }.sumOf { it.amount }
        val totalOut = cashbook.value.filter { it.type.equals("OUT", ignoreCase = true) }.sumOf { it.amount }
        return totalIn - totalOut
    }

    // ==========================================
    // CSV IMPORT
    // ==========================================

    fun previewCustomersFromCsv(csvText: String): List<CsvCustomerPreview> {
        val lines = csvText.lines()
        val result = mutableListOf<CsvCustomerPreview>()
        val existing = customers.value

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            val parts = trimmed.split(",").map { it.trim().trim('"', '\'') }
            val rawName = parts.getOrNull(0) ?: ""
            if (rawName.isBlank() || rawName.equals("name", ignoreCase = true) || rawName.equals("customer name", ignoreCase = true)) {
                continue
            }

            val rawPhone = parts.getOrNull(1) ?: ""
            val rawBalanceStr = parts.getOrNull(2) ?: "0"
            val balance = rawBalanceStr.toDoubleOrNull() ?: 0.0

            val normPhone = rawPhone.replace(Regex("[^0-9]"), "")
            val isDup = existing.any { cust ->
                val existingNorm = cust.phone.replace(Regex("[^0-9]"), "")
                if (normPhone.isNotBlank() && existingNorm.isNotBlank()) {
                    normPhone == existingNorm
                } else {
                    cust.name.trim().equals(rawName.trim(), ignoreCase = true)
                }
            } || result.any { r ->
                val prevNorm = r.phone.replace(Regex("[^0-9]"), "")
                if (normPhone.isNotBlank() && prevNorm.isNotBlank()) {
                    normPhone == prevNorm
                } else {
                    r.name.trim().equals(rawName.trim(), ignoreCase = true)
                }
            }

            result.add(CsvCustomerPreview(name = rawName, phone = rawPhone, balance = balance, isDuplicate = isDup))
        }
        return result
    }

    fun importCustomersFromCsv(previews: List<CsvCustomerPreview>): Int {
        checkWritesAllowed()
        val nonDuplicates = previews.filter { !it.isDuplicate }
        return runBlocking(Dispatchers.IO) {
            val count = database.withTransaction {
                var importedCount = 0
                for (item in nonDuplicates) {
                    val newCust = CustomerEntity(
                        name = item.name.trim(),
                        phone = PhoneUtil.normalize(item.phone),
                        type = item.type.uppercase()
                    )
                    database.customerDao().insert(newCust)
                    if (Math.abs(item.balance) > 0.009) {
                        database.transactionDao().insert(
                            TransactionEntity(
                                customerId = newCust.id,
                                amount = Math.abs(item.balance),
                                type = if (item.balance > 0) "GAVE" else "GOT",
                                note = "Opening Balance (CSV Import)"
                            )
                        )
                    }
                    importedCount++
                }
                importedCount
            }
            _customers.value = database.customerDao().getAllList().map { it.toDomain() }
            _transactions.value = database.transactionDao().getAllList().map { it.toDomain() }
            count
        }
    }

    fun importCustomersFromCsv(csvText: String): Pair<Int, Int> {
        val preview = previewCustomersFromCsv(csvText)
        val imported = importCustomersFromCsv(preview)
        return Pair(imported, preview.size - imported)
    }

    fun clearAllData() {
        checkWritesAllowed()
        runBlocking(Dispatchers.IO) {
            database.withTransaction {
                database.customerDao().deleteAll()
                database.transactionDao().deleteAll()
                database.inventoryDao().deleteAll()
                database.invoiceDao().deleteAll()
                database.cashbookDao().deleteAll()
                database.invoiceSequenceDao().deleteAll()
                database.chatMessageDao().deleteAll()
            }
            _customers.value = emptyList()
            _transactions.value = emptyList()
            _inventory.value = emptyList()
            _invoices.value = emptyList()
            _cashbook.value = emptyList()
            _chatMessages.value = emptyList()
        }
    }

    suspend fun saveChatMessage(
        sender: String,
        text: String,
        actionType: String? = null,
        actionData: String? = null,
        actionTitle: String? = null,
        reportContent: String? = null
    ): com.example.data.local.ChatMessageEntity = withContext(Dispatchers.IO) {
        val entity = com.example.data.local.ChatMessageEntity(
            sender = sender,
            text = text,
            actionType = actionType,
            actionData = actionData,
            actionTitle = actionTitle,
            reportContent = reportContent,
            timestamp = System.currentTimeMillis()
        )
        database.chatMessageDao().insertMessage(entity)
        database.chatMessageDao().pruneOldMessages()
        _chatMessages.value = database.chatMessageDao().getAllMessages().firstOrNull() ?: emptyList()
        entity
    }

    suspend fun markChatActionApplied(id: String) = withContext(Dispatchers.IO) {
        database.chatMessageDao().markApplied(id)
        _chatMessages.value = database.chatMessageDao().getAllMessages().firstOrNull() ?: emptyList()
    }

    suspend fun clearChatMessages() = withContext(Dispatchers.IO) {
        database.chatMessageDao().deleteAll()
        _chatMessages.value = emptyList()
    }

    // ==========================================
    // BACKUP & RESTORE (ROOM BACKED)
    // ==========================================

    fun exportDataAsJsonString(): String {
        return runBlocking(Dispatchers.IO) {
            val json = JSONObject()

            val custArr = JSONArray()
            database.customerDao().getAllList().forEach { c ->
                val o = JSONObject()
                o.put("id", c.id)
                o.put("name", c.name)
                o.put("phone", c.phone)
                o.put("address", c.address)
                o.put("gstin", c.gstin)
                o.put("type", c.type)
                o.put("createdAt", c.createdAt)
                custArr.put(o)
            }
            json.put("customers", custArr)

            val txArr = JSONArray()
            database.transactionDao().getAllList().forEach { t ->
                val o = JSONObject()
                o.put("id", t.id)
                o.put("customerId", t.customerId)
                o.put("amount", t.amount)
                o.put("type", t.type)
                o.put("note", t.note)
                o.put("date", t.date)
                o.put("paymentMode", t.paymentMode)
                o.put("linkedToTxId", t.linkedToTxId)
                o.put("isCompleted", t.isCompleted)
                o.put("billNumber", t.billNumber)
                txArr.put(o)
            }
            json.put("transactions", txArr)

            val invArr = JSONArray()
            database.inventoryDao().getAllList().forEach { item ->
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

            val invsArr = JSONArray()
            database.invoiceDao().getAllWithItemsList().forEach { invWithItems ->
                val inv = invWithItems.invoice
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
                invWithItems.items.forEach { line ->
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

            val cashArr = JSONArray()
            database.cashbookDao().getAllList().forEach { ce ->
                val o = JSONObject()
                o.put("id", ce.id)
                o.put("type", ce.type)
                o.put("amount", ce.amount)
                o.put("category", ce.category)
                o.put("remark", ce.remark)
                o.put("date", ce.date)
                o.put("paymentMode", ce.paymentMode)
                o.put("linkedId", ce.linkedId)
                o.put("source", ce.source)
                cashArr.put(o)
            }
            json.put("cashbook", cashArr)

            json.toString(2)
        }
    }

    fun validateBackupJson(jsonStr: String): ImportResult {
        return try {
            val json = JSONObject(jsonStr)
            val expectedKeys = listOf("customers", "transactions", "inventory", "invoices", "cashbook")
            var foundAny = false
            for (k in expectedKeys) {
                if (json.has(k)) foundAny = true
            }
            if (!foundAny) {
                return ImportResult(success = false, errorMessage = "Backup JSON missing expected data arrays")
            }

            var custCount = 0
            if (json.has("customers")) {
                val arr = json.getJSONArray("customers")
                custCount = arr.length()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.optString("name", "").isBlank()) {
                        return ImportResult(success = false, errorMessage = "Customer at position $i missing name")
                    }
                }
            }

            var txCount = 0
            if (json.has("transactions")) {
                val arr = json.getJSONArray("transactions")
                txCount = arr.length()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (!o.has("amount")) {
                        return ImportResult(success = false, errorMessage = "Transaction at position $i missing amount")
                    }
                }
            }

            var invCount = 0
            if (json.has("inventory")) {
                val arr = json.getJSONArray("inventory")
                invCount = arr.length()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.optString("name", "").isBlank()) {
                        return ImportResult(success = false, errorMessage = "Inventory item at position $i missing name")
                    }
                }
            }

            var billCount = 0
            if (json.has("invoices")) {
                val arr = json.getJSONArray("invoices")
                billCount = arr.length()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (!o.has("billNumber")) {
                        return ImportResult(success = false, errorMessage = "Invoice at position $i missing billNumber")
                    }
                }
            }

            var cashCount = 0
            if (json.has("cashbook")) {
                val arr = json.getJSONArray("cashbook")
                cashCount = arr.length()
            }

            ImportResult(
                success = true,
                customerCount = custCount,
                transactionCount = txCount,
                inventoryCount = invCount,
                invoiceCount = billCount,
                cashbookCount = cashCount
            )
        } catch (e: Exception) {
            Log.e("AppRepository", "Validation failed for backup JSON: ${e.message}", e)
            ImportResult(success = false, errorMessage = "Invalid JSON structure: ${e.localizedMessage ?: "Syntax error"}")
        }
    }

    fun importDataFromJsonString(jsonStr: String): ImportResult {
        checkWritesAllowed()
        val validation = validateBackupJson(jsonStr)
        if (!validation.success) {
            return validation
        }

        return try {
            val currentRaw = exportDataAsJsonString()
            prefs.edit().putString("app_saved_data_prev", currentRaw).commit()

            val json = JSONObject(jsonStr)
            val newCustomers = mutableListOf<CustomerEntity>()
            val rawTransactions = mutableListOf<TransactionEntity>()
            val newInventory = mutableListOf<InventoryItemEntity>()
            val newInvoices = mutableListOf<InvoiceEntity>()
            val newInvoiceItems = mutableListOf<InvoiceItemEntity>()
            val newCashbook = mutableListOf<CashEntryEntity>()

            if (json.has("customers")) {
                val arr = json.getJSONArray("customers")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    newCustomers.add(CustomerEntity(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        name = o.optString("name", "").trim().ifBlank { "Party" },
                        phone = PhoneUtil.normalize(o.optString("phone", "").trim()),
                        address = o.optString("address", "").trim(),
                        gstin = o.optString("gstin", "").trim(),
                        type = o.optString("type", "CUSTOMER").uppercase(),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    ))
                }
            }

            if (json.has("transactions")) {
                val arr = json.getJSONArray("transactions")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val txId = o.optString("id", UUID.randomUUID().toString())
                    val custId = o.optString("customerId", "").trim()
                    val amt = o.optDouble("amount", 0.0)
                    val txType = o.optString("type", "GAVE")
                    val dt = o.optLong("date", System.currentTimeMillis())
                    val oldIsCancelled = o.optBoolean("isCancelled", false)

                    rawTransactions.add(TransactionEntity(
                        id = txId,
                        customerId = custId,
                        amount = amt,
                        type = txType,
                        note = o.optString("note", ""),
                        date = dt,
                        paymentMode = o.optString("paymentMode", "CASH"),
                        linkedToTxId = if (o.has("linkedToTxId") && !o.isNull("linkedToTxId")) o.getString("linkedToTxId") else null,
                        isCompleted = o.optBoolean("isCompleted", oldIsCancelled),
                        billNumber = o.optString("billNumber", "")
                    ))

                    // Map old isCancelled = true on credit bills into linked payments
                    if (oldIsCancelled && txType == "GAVE") {
                        rawTransactions.add(TransactionEntity(
                            id = UUID.randomUUID().toString(),
                            customerId = custId,
                            amount = amt,
                            type = "GOT",
                            note = "Settled (migrated)",
                            date = dt,
                            paymentMode = "CASH",
                            linkedToTxId = txId,
                            isCompleted = true,
                            billNumber = ""
                        ))
                    }
                }
            }

            // Repair orphan transactions whose customerId is missing or unknown
            val validCustIds = newCustomers.map { it.id }.toSet()
            val repairedTransactions = mutableListOf<TransactionEntity>()
            var orphanCount = 0
            var recoveredCustomer: CustomerEntity? = null

            for (tx in rawTransactions) {
                if (tx.customerId.isBlank() || !validCustIds.contains(tx.customerId)) {
                    orphanCount++
                    if (recoveredCustomer == null) {
                        recoveredCustomer = newCustomers.find { it.name.equals("Recovered entries", ignoreCase = true) }
                        if (recoveredCustomer == null) {
                            recoveredCustomer = CustomerEntity(
                                id = UUID.randomUUID().toString(),
                                name = "Recovered entries",
                                phone = "",
                                address = "Auto-created for unassigned backup transactions"
                            )
                            newCustomers.add(recoveredCustomer)
                        }
                    }
                    repairedTransactions.add(tx.copy(customerId = recoveredCustomer.id))
                } else {
                    repairedTransactions.add(tx)
                }
            }

            if (json.has("inventory")) {
                val arr = json.getJSONArray("inventory")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val sizesArr = o.optJSONArray("sizes")
                    val sizes = mutableListOf<String>()
                    if (sizesArr != null) {
                        for (j in 0 until sizesArr.length()) {
                            sizes.add(sizesArr.getString(j))
                        }
                    }
                    newInventory.add(InventoryItemEntity(
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
            }

            if (json.has("invoices")) {
                val arr = json.getJSONArray("invoices")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val invId = o.optString("id", UUID.randomUUID().toString())
                    newInvoices.add(InvoiceEntity(
                        id = invId,
                        billNumber = o.optString("billNumber", ""),
                        date = o.optLong("date", System.currentTimeMillis()),
                        customerName = o.optString("customerName", ""),
                        customerPhone = o.optString("customerPhone", ""),
                        subtotal = o.optDouble("subtotal", 0.0),
                        gstAmount = o.optDouble("gstAmount", 0.0),
                        discount = o.optDouble("discount", 0.0),
                        additionalCharges = o.optDouble("additionalCharges", 0.0),
                        totalAmount = o.optDouble("totalAmount", 0.0),
                        paymentMode = o.optString("paymentMode", "CASH"),
                        stateOfSupply = o.optString("stateOfSupply", "Gujarat"),
                        gstin = o.optString("gstin", ""),
                        notes = o.optString("notes", ""),
                        themeName = o.optString("themeName", "PREMIUM_BLUE")
                    ))

                    val itemsArr = o.optJSONArray("items")
                    if (itemsArr != null) {
                        for (j in 0 until itemsArr.length()) {
                            val io = itemsArr.getJSONObject(j)
                            newInvoiceItems.add(InvoiceItemEntity(
                                id = io.optString("id", UUID.randomUUID().toString()),
                                invoiceId = invId,
                                name = io.optString("name", ""),
                                quantity = io.optInt("quantity", 1),
                                unitPrice = io.optDouble("unitPrice", 0.0),
                                gstPercent = io.optDouble("gstPercent", 0.0),
                                total = io.optDouble("total", 0.0)
                            ))
                        }
                    }
                }
            }

            if (json.has("cashbook")) {
                val arr = json.getJSONArray("cashbook")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    newCashbook.add(CashEntryEntity(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        type = o.optString("type", "IN"),
                        amount = o.optDouble("amount", 0.0),
                        category = o.optString("category", ""),
                        remark = o.optString("remark", ""),
                        date = o.optLong("date", System.currentTimeMillis()),
                        paymentMode = o.optString("paymentMode", "CASH"),
                        linkedId = if (o.has("linkedId") && !o.isNull("linkedId")) o.getString("linkedId") else null,
                        source = o.optString("source", "manual")
                    ))
                }
            }

            runBlocking(Dispatchers.IO) {
                database.withTransaction {
                    database.customerDao().deleteAll()
                    database.transactionDao().deleteAll()
                    database.inventoryDao().deleteAll()
                    database.invoiceDao().deleteAll()
                    database.cashbookDao().deleteAll()

                    database.customerDao().insertAll(newCustomers)
                    database.transactionDao().insertAll(repairedTransactions)
                    database.inventoryDao().insertAll(newInventory)
                    database.invoiceDao().insertInvoices(newInvoices)
                    database.invoiceDao().insertItems(newInvoiceItems)
                    database.cashbookDao().insertAll(newCashbook)
                }
                _customers.value = database.customerDao().getAllList().map { it.toDomain() }
                _transactions.value = database.transactionDao().getAllList().map { it.toDomain() }
                _inventory.value = database.inventoryDao().getAllList().map { it.toDomain() }
                _invoices.value = database.invoiceDao().getAllWithItemsList().map { it.toDomain() }
                _cashbook.value = database.cashbookDao().getAllList().map { it.toDomain() }
            }
            validation.copy(
                customerCount = newCustomers.size,
                transactionCount = repairedTransactions.size,
                repairedCount = orphanCount
            )
        } catch (e: Exception) {
            Log.e("AppRepository", "Error applying backup to Room: ${e.message}", e)
            ImportResult(success = false, errorMessage = "Error applying backup: ${e.localizedMessage}")
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
            putExtra(Intent.EXTRA_SUBJECT, "Ghanshyam Kids Wear Plain JSON Data Backup")
            putExtra(
                Intent.EXTRA_TEXT,
                "Attached is the plain JSON database backup file for Ghanshyam Kids Wear & Traders."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Plain JSON Backup File"))
    }

    // ==========================================
    // EXCEL / CSV SPREADSHEET EXPORTS
    // ==========================================

    private fun escapeCsv(value: Any?): String {
        val str = value?.toString() ?: ""
        return if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            "\"" + str.replace("\"", "\"\"") + "\""
        } else {
            str
        }
    }

    fun exportKhataLedgerCsv(context: Context): File {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(reportsDir, "Khata_Ledger_$dateStr.csv")

        val sb = StringBuilder()
        sb.append("Customer Name,Phone,Net Balance,Last Transaction Date\n")

        customers.value.forEach { customer ->
            val custTxns = transactions.value.filter { it.customerId == customer.id }
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

    fun exportSalesReportCsv(context: Context): File {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(reportsDir, "Sales_Report_$dateStr.csv")

        val sb = StringBuilder()
        sb.append("Bill No,Date,Customer Name,Subtotal,GST,Discount,Total Amount,Payment Mode\n")

        invoices.value.forEach { invoice ->
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

    fun exportInventoryCsv(context: Context): File {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(reportsDir, "Inventory_Stock_$dateStr.csv")

        val sb = StringBuilder()
        sb.append("Item Code,Item Name,Category,Stock Quantity,Purchase Price,Sale Price\n")

        inventory.value.forEach { item ->
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
