package com.example

import android.content.Context
import com.example.data.AppRepository
import com.example.model.Customer
import com.example.model.Invoice
import com.example.model.InvoiceItem
import com.example.model.PartyType
import com.example.model.StoreProfile
import com.example.ui.ai.AiResponse
import com.example.ui.ai.GhanshyamAiEngine
import com.example.ui.util.PhoneUtil
import com.example.ui.util.StatementDateRange
import com.example.ui.util.StatementFilter
import com.example.ui.util.StatementGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var repository: AppRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("ghanshyam_billing_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repository = AppRepository(context, asyncInit = false)
        repository.clearAllData()
    }

    @Test
    fun testInitialEmptyState() {
        assertTrue("Customers must be initially empty", repository.customers.value.isEmpty())
        assertTrue("Transactions must be initially empty", repository.transactions.value.isEmpty())
        assertTrue("Inventory must be initially empty", repository.inventory.value.isEmpty())
        assertTrue("Invoices must be initially empty", repository.invoices.value.isEmpty())
        assertTrue("Cashbook must be initially empty", repository.cashbook.value.isEmpty())
        assertFalse("Onboarding should be incomplete initially", repository.hasCompletedOnboarding.value)
    }

    @Test
    fun testPhoneUtilNormalization() {
        // 10 digits gets 91 prefix
        assertEquals("919876543210", PhoneUtil.normalize("9876543210"))
        assertEquals("919876543210", PhoneUtil.normalize("98765 43210"))
        assertEquals("919876543210", PhoneUtil.normalize("98765-43210"))

        // Leading 0 stripped and 91 added
        assertEquals("919876543210", PhoneUtil.normalize("09876543210"))

        // Existing country code preserved
        assertEquals("919876543210", PhoneUtil.normalize("+91 98765 43210"))
        assertEquals("919876543210", PhoneUtil.normalize("+919876543210"))
        assertEquals("14155552671", PhoneUtil.normalize("+1 415 555 2671"))

        // WhatsApp number
        assertEquals("919876543210", PhoneUtil.toWhatsAppNumber("9876543210"))
        assertEquals("919876543210", PhoneUtil.toWhatsAppNumber("+91 98765 43210"))

        // Display formatting
        assertEquals("+91 98765 43210", PhoneUtil.toDisplay("919876543210"))
        assertEquals("+91 98765 43210", PhoneUtil.toDisplay("+919876543210"))
        assertEquals("", PhoneUtil.toDisplay(""))
    }

    @Test
    fun testPartyTypesAndUniversalBalanceConvention() {
        // 1. Customer: GAVE increases balance (Lena Hai > 0), GOT reduces balance toward 0
        val cust = repository.addCustomer("Ramesh Cloth Store", "9820011111", type = PartyType.CUSTOMER.raw)
        assertEquals(PartyType.CUSTOMER.raw, cust.type)

        val custBill = repository.addTransaction(cust.id, 5000.0, "GAVE", "Festival wholesale")
        assertEquals(5000.0, repository.getCustomerBalance(cust.id), 0.01)
        assertEquals(AppRepository.BillStatus.PENDING, repository.getBillStatus(custBill))

        // 2. Supplier: GOT is credit goods received (Dena Hai < 0), GAVE is payment to supplier
        val supp = repository.addCustomer("Surat Textile Mill", "9820022222", type = PartyType.SUPPLIER.raw)
        assertEquals(PartyType.SUPPLIER.raw, supp.type)

        val suppBill = repository.addTransaction(supp.id, 8000.0, "GOT", "Fabric roll lot")
        // Sign convention: balance = gave - got = 0 - 8000 = -8000 (You will give / Dena Hai)
        assertEquals(-8000.0, repository.getCustomerBalance(supp.id), 0.01)
        assertEquals(AppRepository.BillStatus.PENDING, repository.getBillStatus(suppBill))
    }

    @Test
    fun testMarkReceivedAndReversibleUndoPayment() {
        val cust = repository.addCustomer("Rajesh Sharma", "9820012345")
        val bill = repository.addTransaction(cust.id, 2000.0, "GAVE", "Shirt lot")

        assertEquals(2000.0, repository.getCustomerBalance(cust.id), 0.01)
        assertEquals(AppRepository.BillStatus.PENDING, repository.getBillStatus(bill))
        assertEquals(2000.0, repository.getBillOutstandingAmount(bill), 0.01)

        // Partial payment: 800 received with cashbook sync
        val partialPayment = repository.markBillReceived(
            billId = bill.id,
            amount = 800.0,
            paymentMode = "UPI",
            addToCashbook = true
        )
        assertNotNull("Payment transaction created", partialPayment)
        assertEquals("GOT", partialPayment!!.type)
        assertEquals(800.0, partialPayment.amount, 0.01)
        assertEquals("UPI", partialPayment.paymentMode)
        assertEquals(bill.id, partialPayment.linkedToTxId)

        // Check balance and bill status
        assertEquals(1200.0, repository.getCustomerBalance(cust.id), 0.01)
        assertEquals(800.0, repository.getBillReceivedAmount(bill.id), 0.01)
        assertEquals(1200.0, repository.getBillOutstandingAmount(bill), 0.01)
        assertEquals(AppRepository.BillStatus.PARTLY_RECEIVED, repository.getBillStatus(bill))

        // Check cashbook entry created and linked
        assertEquals(1, repository.cashbook.value.size)
        val cashEntry = repository.cashbook.value.first()
        assertEquals("IN", cashEntry.type)
        assertEquals(800.0, cashEntry.amount, 0.01)
        assertEquals("UPI", cashEntry.paymentMode)
        assertEquals(partialPayment.id, cashEntry.linkedId)

        // Full remaining payment: 1200 received
        val fullPayment = repository.markBillReceived(
            billId = bill.id,
            amount = 1200.0,
            paymentMode = "CASH",
            addToCashbook = true
        )
        assertNotNull(fullPayment)
        assertEquals(0.0, repository.getCustomerBalance(cust.id), 0.01)
        assertEquals(AppRepository.BillStatus.RECEIVED, repository.getBillStatus(bill))
        assertEquals(0.0, repository.getBillOutstandingAmount(bill), 0.01)
        assertEquals(2, repository.cashbook.value.size)

        // Plain Completed checkbox (Delivered/Packed) toggle - NO MONEY EFFECT
        repository.toggleBillCompleted(bill.id)
        val updatedBill = repository.transactions.value.find { it.id == bill.id }!!
        assertTrue("Bill should be marked completed", updatedBill.isCompleted)
        assertEquals("Completing bill has 0 money effect", 0.0, repository.getCustomerBalance(cust.id), 0.01)

        // Undo payment: undoing the partial payment (800)
        repository.undoPayment(partialPayment.id)
        assertEquals("Balance recomputed after undo", 800.0, repository.getCustomerBalance(cust.id), 0.01)
        assertEquals(AppRepository.BillStatus.PARTLY_RECEIVED, repository.getBillStatus(bill))
        // Linked cashbook entry removed
        assertNull("Undone payment removed from cashbook", repository.cashbook.value.find { it.linkedId == partialPayment.id })
        assertEquals(1, repository.cashbook.value.size)
    }

    @Test
    fun testMultiSelectMarkAllReceived() {
        val cust = repository.addCustomer("Vijay Patel", "9898011223")
        val bill1 = repository.addTransaction(cust.id, 1000.0, "GAVE", "Bill 1")
        val bill2 = repository.addTransaction(cust.id, 1500.0, "GAVE", "Bill 2")
        val bill3 = repository.addTransaction(cust.id, 500.0, "GAVE", "Bill 3")

        assertEquals(3000.0, repository.getCustomerBalance(cust.id), 0.01)

        // Mark bill1 and bill2 received in bulk
        repository.markBillsAllReceived(
            billIds = listOf(bill1.id, bill2.id),
            paymentMode = "BANK",
            addToCashbook = true
        )

        // Bill 1 and 2 received, bill 3 still pending
        assertEquals(AppRepository.BillStatus.RECEIVED, repository.getBillStatus(bill1))
        assertEquals(AppRepository.BillStatus.RECEIVED, repository.getBillStatus(bill2))
        assertEquals(AppRepository.BillStatus.PENDING, repository.getBillStatus(bill3))
        assertEquals(500.0, repository.getCustomerBalance(cust.id), 0.01)

        // Cashbook got 2 entries linked to the 2 payments
        assertEquals(2, repository.cashbook.value.size)
        assertTrue(repository.cashbook.value.all { it.paymentMode == "BANK" && it.type == "IN" })
    }

    @Test
    fun testEditTransactionWithCashbookSync() {
        val cust = repository.addCustomer("Dinesh Shah", "9825000111")
        val tx = repository.addTransaction(cust.id, 1500.0, "GOT", "Payment", paymentMode = "CASH")
        repository.addCashEntry(
            type = "IN",
            amount = 1500.0,
            category = "Khata Settlement",
            remark = "From Dinesh",
            paymentMode = "CASH",
            linkedId = tx.id,
            source = "khata"
        )

        assertEquals(1, repository.cashbook.value.size)
        assertEquals(1500.0, repository.cashbook.value.first().amount, 0.01)

        // Edit amount and mode
        repository.editTransaction(
            txId = tx.id,
            amount = 1800.0,
            note = "Updated Payment",
            date = System.currentTimeMillis(),
            paymentMode = "UPI"
        )

        val updatedTx = repository.transactions.value.find { it.id == tx.id }!!
        assertEquals(1800.0, updatedTx.amount, 0.01)
        assertEquals("UPI", updatedTx.paymentMode)
        assertEquals("Updated Payment", updatedTx.note)

        // Cashbook entry updated accordingly
        val updatedCash = repository.cashbook.value.find { it.linkedId == tx.id }!!
        assertEquals(1800.0, updatedCash.amount, 0.01)
        assertEquals("UPI", updatedCash.paymentMode)
    }

    @Test
    fun testStatementPureComputation() {
        val cust = repository.addCustomer("Prakash Bhai", "9876543210")
        val t1 = repository.addTransaction(cust.id, 2000.0, "GAVE", "Opening Balance", date = 1000L)
        val t2 = repository.addTransaction(cust.id, 3000.0, "GAVE", "Festival Lot", date = 2000L)
        val t3 = repository.markBillReceived(t2.id, 1000.0, "CASH", date = 3000L, addToCashbook = false)!!

        val allTx = listOf(t1, t2, t3)
        val statementData = StatementGenerator.computeStatement(
            customer = cust,
            allTransactions = allTx,
            dateRange = com.example.ui.util.StatementDateRange.ALL,
            filter = com.example.ui.util.StatementFilter.ALL_ENTRIES
        )

        assertEquals(3, statementData.entries.size)
        // Row 1: t1 (GAVE 2000) -> running balance = 2000
        assertEquals(2000.0, statementData.entries[0].runningBalance, 0.01)
        // Row 2: t2 (GAVE 3000) -> running balance = 5000
        assertEquals(5000.0, statementData.entries[1].runningBalance, 0.01)
        // Row 3: t3 (GOT 1000) -> running balance = 4000
        assertEquals(4000.0, statementData.entries[2].runningBalance, 0.01)
        assertEquals(4000.0, statementData.closingBalance, 0.01)

        // Pending bills filter returns unfulfilled credit bills and their linked partial payments
        val pendingData = StatementGenerator.computeStatement(
            customer = cust,
            allTransactions = allTx,
            dateRange = com.example.ui.util.StatementDateRange.ALL,
            filter = com.example.ui.util.StatementFilter.PENDING_BILLS_ONLY
        )
        assertEquals(3, pendingData.entries.size)
    }

    @Test
    fun testRealCsvCustomerImport() {
        val csvData = """
            name,phone,balance
            Ramesh Kumar,9820011111,1500
            Suresh Shah,9820022222,-450
            ,9800000000,100
        """.trimIndent()

        val previews = repository.previewCustomersFromCsv(csvData)
        assertEquals("Should parse 2 valid rows and skip empty name", 2, previews.size)
        assertEquals("Ramesh Kumar", previews[0].name)
        assertEquals(1500.0, previews[0].balance, 0.01)

        val importedCount = repository.importCustomersFromCsv(previews)
        assertEquals("Should import 2 customers", 2, importedCount)
        assertEquals(2, repository.customers.value.size)

        val ramesh = repository.customers.value.find { it.name == "Ramesh Kumar" }
        assertNotNull(ramesh)
        assertEquals(1500.0, repository.getCustomerBalance(ramesh!!.id), 0.01)

        val suresh = repository.customers.value.find { it.name == "Suresh Shah" }
        assertNotNull(suresh)
        assertEquals(-450.0, repository.getCustomerBalance(suresh!!.id), 0.01)
    }

    @Test
    fun testAiEngineOutOfScopeAndReport() = runBlocking {
        val ai = GhanshyamAiEngine(repository)

        // Test out of scope
        val outOfScopeRes = ai.processQuery("What is the weather in Paris?")
        assertTrue("Should detect out of scope", outOfScopeRes is AiResponse.OutOfScope)

        // Test report generation
        val reportRes = ai.processQuery("Show me today sales report and statement")
        assertTrue("Should generate business report", reportRes is AiResponse.Success)
        val succ = reportRes as AiResponse.Success
        assertNotNull(succ.reportContent)
        assertTrue(succ.reportContent!!.contains("GHANSHYAM BILLING BUSINESS REPORT"))
    }

    @Test
    fun testPdfInvoiceGeneration() {
        val invoice = Invoice(
            billNumber = "INV-TEST-001",
            date = System.currentTimeMillis(),
            customerName = "Priya Sharma",
            customerPhone = "+91 98765 00000",
            items = listOf(
                InvoiceItem(
                    name = "Kids Cotton Frock",
                    quantity = 2,
                    unitPrice = 450.0,
                    gstPercent = 5.0,
                    total = 945.0
                )
            ),
            subtotal = 900.0,
            gstAmount = 45.0,
            discount = 45.0,
            additionalCharges = 0.0,
            totalAmount = 900.0,
            paymentMode = "CASH"
        )

        val pdfFile = com.example.ui.util.PdfInvoiceGenerator.generateInvoicePdf(
            context = context,
            storeProfile = repository.storeProfile.value,
            invoice = invoice
        )

        assertTrue("Generated PDF file should exist", pdfFile.exists())
        assertTrue("Generated PDF file should not be empty", pdfFile.length() > 0)
    }

    @Test
    fun testMultiPagePdfStatementGeneration() {
        val cust = repository.addCustomer("Mohan Lal", "9876500000")
        for (i in 1..40) {
            repository.addTransaction(cust.id, 100.0 * i, "GAVE", "Wholesale Lot #$i")
        }
        val statementData = StatementGenerator.computeStatement(
            customer = cust,
            allTransactions = repository.transactions.value,
            dateRange = com.example.ui.util.StatementDateRange.ALL,
            filter = com.example.ui.util.StatementFilter.ALL_ENTRIES
        )

        val pdfFile = StatementGenerator.generateStatementPdf(
            context = context,
            statement = statementData,
            storeProfile = StoreProfile(storeName = "Ghanshyam Kids Wear")
        )

        assertTrue("Multi-page PDF file should exist", pdfFile.exists())
        assertTrue("Multi-page PDF file should not be empty", pdfFile.length() > 0)
    }

    @Test
    fun testCsvSpreadsheetExports() {
        val cust = repository.addCustomer("Anita Verma", "+91 99999 11111")
        repository.addTransaction(cust.id, 1200.0, "GAVE", "Clothes")
        repository.addInventoryItem(
            name = "Boys Denim Jeans",
            itemCode = "BD-01",
            salePrice = 650.0,
            purchasePrice = 400.0,
            stock = 15,
            category = "Boys Wear",
            sizes = listOf("24", "26", "28"),
            gstPercent = 5.0
        )

        // 1. Khata Ledger CSV
        val khataCsv = repository.exportKhataLedgerCsv(context)
        assertTrue("Khata CSV exists", khataCsv.exists())
        val khataContent = khataCsv.readText()
        assertTrue("Header present in Khata CSV", khataContent.contains("Customer Name,Phone,Net Balance,Last Transaction Date"))
        assertTrue("Anita Verma included in CSV", khataContent.contains("Anita Verma"))

        // 2. Sales Report CSV
        val salesCsv = repository.exportSalesReportCsv(context)
        assertTrue("Sales CSV exists", salesCsv.exists())
        val salesContent = salesCsv.readText()
        assertTrue("Header present in Sales CSV", salesContent.contains("Bill No,Date,Customer Name,Subtotal,GST,Discount,Total Amount,Payment Mode"))

        // 3. Inventory Stock CSV
        val invCsv = repository.exportInventoryCsv(context)
        assertTrue("Inventory CSV exists", invCsv.exists())
        val invContent = invCsv.readText()
        assertTrue("Header present in Inventory CSV", invContent.contains("Item Code,Item Name,Category,Stock Quantity,Purchase Price,Sale Price"))
        assertTrue("Boys Denim Jeans present in CSV", invContent.contains("Boys Denim Jeans"))
    }

    @Test
    fun testBackupAndRestoreWithValidation() {
        repository.addCustomer("Deepak Patel", "+91 98765 43210")
        val jsonBackup = repository.exportDataAsJsonString()
        assertTrue("Backup contains customers", jsonBackup.contains("Deepak Patel"))

        val validation = repository.validateBackupJson(jsonBackup)
        assertTrue("Backup validation passes", validation.isValid)
        assertEquals(1, validation.customerCount)

        val backupFile = repository.exportBackupFile(context)
        assertTrue("Backup file created", backupFile.exists())
        assertTrue("Backup file has content", backupFile.length() > 0)

        // Clear and restore
        repository.clearAllData()
        assertTrue("Customers cleared", repository.customers.value.isEmpty())

        val importResult = repository.importDataFromJsonString(jsonBackup)
        assertTrue("Restore succeeded", importResult.success)
        assertEquals("Customer restored", 1, repository.customers.value.size)
        assertEquals("Deepak Patel", repository.customers.value[0].name)

        // Corrupted JSON test
        val corruptResult = repository.importDataFromJsonString("{ corrupted json data")
        assertFalse("Corrupted JSON restore must fail safely", corruptResult.success)
    }

    @Test
    fun testTolerantBackupImportAttachesOrphansToRecoveredEntries() {
        val backupWithOrphan = """
            {
                "customers": [
                    {"id": "c-backup-1", "name": "Existing Party", "phone": "9999900000", "address": "", "gstin": ""}
                ],
                "transactions": [
                    {"id": "tx-orphan-2", "customerId": "missing-party-id", "amount": 750.0, "type": "GOT", "note": "Orphan Jama", "date": 1600000000000}
                ],
                "inventory": [],
                "invoices": [],
                "cashbook": []
            }
        """.trimIndent()

        val result = repository.importDataFromJsonString(backupWithOrphan)
        assertTrue("Import must succeed tolerantly", result.success)
        assertEquals("Should report 1 repaired orphan transaction", 1, result.repairedCount)

        val recoveredCust = repository.customers.value.find { it.name == "Recovered entries" }
        assertNotNull("Recovered entries party must exist", recoveredCust)
        val orphanTx = repository.transactions.value.find { it.id == "tx-orphan-2" }
        assertNotNull("Orphan transaction must exist in repository", orphanTx)
        assertEquals(recoveredCust!!.id, orphanTx!!.customerId)
    }
}
