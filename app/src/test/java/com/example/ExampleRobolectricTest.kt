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
import com.example.util.AppLockManager
import com.example.util.SecurityManager
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
        val secPrefs = context.getSharedPreferences("app_security_prefs", Context.MODE_PRIVATE)
        secPrefs.edit().clear().commit()
        AppLockManager.init(context)
        AppLockManager.unlock()
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

    @Test
    fun testConsecutiveSequentialBillNumbering() {
        val fy = repository.getFinancialYear()
        val expected1 = "GKW/$fy/0001"
        val expected2 = "GKW/$fy/0002"
        val expected3 = "GKW/$fy/0003"

        val item = repository.addInventoryItem(
            name = "Party Wear Frock",
            salePrice = 500.0,
            stock = 50,
            gstPercent = 5.0
        )

        // Bill 1
        val bill1 = repository.createInvoice(
            Invoice(
                billNumber = "",
                customerName = "Customer One",
                items = listOf(InvoiceItem(name = item.name, quantity = 2, unitPrice = 500.0, gstPercent = 5.0))
            )
        )
        assertEquals("First bill number must be consecutive 0001", expected1, bill1.billNumber)

        // Bill 2
        val bill2 = repository.createInvoice(
            Invoice(
                billNumber = "",
                customerName = "Customer Two",
                items = listOf(InvoiceItem(name = item.name, quantity = 1, unitPrice = 500.0, gstPercent = 5.0))
            )
        )
        assertEquals("Second bill number must be consecutive 0002", expected2, bill2.billNumber)

        // Bill 3
        val bill3 = repository.createInvoice(
            Invoice(
                billNumber = "",
                customerName = "Customer Three",
                items = listOf(InvoiceItem(name = item.name, quantity = 3, unitPrice = 500.0, gstPercent = 5.0))
            )
        )
        assertEquals("Third bill number must be consecutive 0003", expected3, bill3.billNumber)

        // Verify all 3 bills are in repository with unique consecutive numbers <= 16 characters
        val allInvoices = repository.invoices.value
        assertEquals(3, allInvoices.size)
        assertTrue("Bill number must be <= 16 chars", bill1.billNumber.length <= 16)
        assertTrue("Bill number must be <= 16 chars", bill2.billNumber.length <= 16)
        assertTrue("Bill number must be <= 16 chars", bill3.billNumber.length <= 16)
    }

    @Test
    fun testGstSplitIntraStateVsInterState() {
        // Set shop state to Gujarat
        repository.updateStoreProfile(
            storeName = "Ghanshyam Kids Wear",
            address = "Surat Textile Market",
            phone = "9820011111",
            upiId = "ghanshyam@upi",
            gstin = "24AAAAA0000A1Z5",
            ownerName = "Ghanshyamdas",
            state = "Gujarat"
        )

        val item = repository.addInventoryItem(
            name = "Baby Romper",
            salePrice = 200.0,
            stock = 100,
            gstPercent = 5.0
        )

        // 1. Intra-State (Same State: Gujarat to Gujarat) -> CGST 2.5% + SGST 2.5%
        val intraInvoice = repository.createInvoice(
            Invoice(
                billNumber = "",
                customerName = "Ahmedabad Retailer",
                stateOfSupply = "Gujarat",
                items = listOf(InvoiceItem(itemId = item.id, name = item.name, quantity = 10, unitPrice = 200.0, gstPercent = 5.0))
            )
        )
        assertFalse("Must be intra-state", intraInvoice.isInterState)
        assertEquals("Subtotal 2000.0", 2000.0, intraInvoice.subtotal, 0.01)
        assertEquals("CGST amount 50.0", 50.0, intraInvoice.cgstAmount, 0.01)
        assertEquals("SGST amount 50.0", 50.0, intraInvoice.sgstAmount, 0.01)
        assertEquals("IGST amount 0.0", 0.0, intraInvoice.igstAmount, 0.01)
        assertEquals("Total tax 100.0", 100.0, intraInvoice.gstAmount, 0.01)
        assertEquals("Total amount 2100.0", 2100.0, intraInvoice.totalAmount, 0.01)

        // 2. Inter-State (Different State: Gujarat to Maharashtra) -> IGST 5.0%
        val interInvoice = repository.createInvoice(
            Invoice(
                billNumber = "",
                customerName = "Mumbai Wholesaler",
                stateOfSupply = "Maharashtra",
                items = listOf(InvoiceItem(itemId = item.id, name = item.name, quantity = 10, unitPrice = 200.0, gstPercent = 5.0))
            )
        )
        assertTrue("Must be inter-state", interInvoice.isInterState)
        assertEquals("Subtotal 2000.0", 2000.0, interInvoice.subtotal, 0.01)
        assertEquals("CGST amount 0.0", 0.0, interInvoice.cgstAmount, 0.01)
        assertEquals("SGST amount 0.0", 0.0, interInvoice.sgstAmount, 0.01)
        assertEquals("IGST amount 100.0", 100.0, interInvoice.igstAmount, 0.01)
        assertEquals("Total tax 100.0", 100.0, interInvoice.gstAmount, 0.01)
        assertEquals("Total amount 2100.0", 2100.0, interInvoice.totalAmount, 0.01)
    }

    @Test
    fun testWholesaleSizeStockDeductionAndReversibleCancellation() {
        val item = repository.addInventoryItem(
            name = "Boys Cotton Kurta",
            salePrice = 300.0,
            sizeStock = mapOf("2-3Y" to 10, "4-5Y" to 15, "6-7Y" to 8),
            lowStockThreshold = 5,
            gstPercent = 5.0
        )
        assertEquals("Total stock is 33", 33, item.stock)
        assertEquals(15, item.sizeStock["4-5Y"])

        val party = repository.addCustomer("Surat Garments", "9820033333")

        // Sell 3 pieces of size 4-5Y on CREDIT
        val invoice = repository.createInvoice(
            Invoice(
                billNumber = "",
                customerId = party.id,
                customerName = party.name,
                paymentMode = "CREDIT",
                items = listOf(
                    InvoiceItem(
                        itemId = item.id,
                        name = item.name,
                        size = "4-5Y",
                        quantity = 3,
                        unitPrice = 300.0,
                        gstPercent = 5.0
                    )
                )
            )
        )

        // Verify stock drops specifically for size 4-5Y
        val updatedItem = repository.inventory.value.find { it.id == item.id }!!
        assertEquals("Size 4-5Y stock dropped by 3 to 12", 12, updatedItem.sizeStock["4-5Y"])
        assertEquals("Total stock dropped to 30", 30, updatedItem.stock)

        // Verify credit udhar entry in Khata
        val customerBalance = repository.getCustomerBalance(party.id)
        assertTrue("Party has receivable balance", customerBalance > 0)
        val linkedTx = repository.transactions.value.find { it.billNumber == invoice.billNumber }
        assertNotNull("Credit transaction created with bill number", linkedTx)

        // CANCEL the invoice with a reason (never delete numbered invoice)
        repository.cancelInvoice(invoice.id, "Customer returned order")

        val cancelledInvoice = repository.invoices.value.find { it.id == invoice.id }!!
        assertTrue("Invoice must be marked CANCELLED", cancelledInvoice.isCancelled)
        assertEquals("Customer returned order", cancelledInvoice.cancelReason)
        assertEquals("Bill number stays preserved on cancelled bill", invoice.billNumber, cancelledInvoice.billNumber)

        // Verify stock is restored
        val restoredItem = repository.inventory.value.find { it.id == item.id }!!
        assertEquals("Size 4-5Y stock restored back to 15", 15, restoredItem.sizeStock["4-5Y"])
        assertEquals("Total stock restored to 33", 33, restoredItem.stock)

        // Verify credit entry in Khata is reversed
        val balanceAfterCancel = repository.getCustomerBalance(party.id)
        assertEquals("Khata balance restored to 0", 0.0, balanceAfterCancel, 0.01)
    }

    @Test
    fun testInsufficientSizeStockBlocksInvoice() {
        val item = repository.addInventoryItem(
            name = "Silk Sherwani Set",
            salePrice = 1200.0,
            sizeStock = mapOf("2-3Y" to 5, "4-5Y" to 3),
            gstPercent = 12.0
        )

        try {
            // Attempt to sell 10 pieces when only 3 available in size 4-5Y
            repository.createInvoice(
                Invoice(
                    billNumber = "",
                    customerName = "Test Buyer",
                    items = listOf(
                        InvoiceItem(
                            itemId = item.id,
                            name = item.name,
                            size = "4-5Y",
                            quantity = 10,
                            unitPrice = 1200.0,
                            gstPercent = 12.0
                        )
                    )
                )
            )
            fail("Should have thrown IllegalStateException for insufficient stock")
        } catch (e: IllegalStateException) {
            assertTrue("Exception message mentions insufficient stock", e.message?.contains("Insufficient stock") == true)
        }

        // Verify stock remained untouched
        val itemAfter = repository.inventory.value.find { it.id == item.id }!!
        assertEquals("Stock remained 3", 3, itemAfter.sizeStock["4-5Y"])
    }

    @Test
    fun testNumberToWordsIndianSystem() {
        val words1 = com.example.ui.util.NumberToWords.convert(125450.0)
        assertEquals("One Lakh Twenty-Five Thousand Four Hundred Fifty Rupees Only", words1)

        val words2 = com.example.ui.util.NumberToWords.convert(2500.50)
        assertEquals("Two Thousand Five Hundred Rupees and Fifty Paise Only", words2)

        val words3 = com.example.ui.util.NumberToWords.convert(0.0)
        assertEquals("Zero Rupees Only", words3)
    }

    @Test
    fun testSaltedPinHashingAndVerification() {
        assertFalse("PIN initially not set", SecurityManager.isPinSet(context))

        // Invalid formats rejected
        assertFalse(SecurityManager.setPin(context, "123")) // 3 digits
        assertFalse(SecurityManager.setPin(context, "12345")) // 5 digits
        assertFalse(SecurityManager.setPin(context, "12a4")) // non-digit

        // Valid 4-digit PIN
        assertTrue(SecurityManager.setPin(context, "1234"))
        assertTrue(SecurityManager.isPinSet(context))

        // Verification
        assertTrue("Correct PIN verified", SecurityManager.verifyPin(context, "1234"))
        assertFalse("Wrong PIN rejected", SecurityManager.verifyPin(context, "0000"))
        assertFalse("Incomplete PIN rejected", SecurityManager.verifyPin(context, "123"))

        // Security checks: salted hash, not plaintext
        val secPrefs = context.getSharedPreferences("app_security_prefs", Context.MODE_PRIVATE)
        val hash = secPrefs.getString("pin_hash", null)
        assertNotNull(hash)
        assertNotEquals("Plaintext PIN must NEVER be stored", "1234", hash)
        val salt = secPrefs.getString("pin_salt", null)
        assertNotNull("Salt must be stored", salt)

        // Excluded from backup
        val backupJson = repository.exportDataAsJsonString()
        assertFalse("Salt must not be in backup", backupJson.contains("pin_salt"))
        assertFalse("Hash must not be in backup", backupJson.contains("pin_hash"))
        assertFalse("Plaintext PIN must not be in backup", backupJson.contains("1234"))

        // Removal
        SecurityManager.removePin(context)
        assertFalse("PIN successfully removed", SecurityManager.isPinSet(context))
    }

    @Test
    fun testAppLockBackgroundTimeout() {
        SecurityManager.setPin(context, "9876")
        AppLockManager.init(context)
        AppLockManager.unlock()
        assertFalse("App starts unlocked after explicit unlock", AppLockManager.isLocked.value)

        // Backgrounded for 30 seconds (< 60 seconds threshold) -> stays unlocked
        AppLockManager.simulateBackgroundElapsed(30_000L, context)
        assertFalse("Should remain unlocked after 30 seconds", AppLockManager.isLocked.value)

        // Backgrounded for 60 seconds (>= 60 seconds threshold) -> locks
        AppLockManager.simulateBackgroundElapsed(60_000L, context)
        assertTrue("Should lock after 1 minute in background", AppLockManager.isLocked.value)

        // Unlock
        AppLockManager.unlock()
        assertFalse("Unlocked again", AppLockManager.isLocked.value)
    }

    @Test
    fun testGeminiFunctionCallingAndOfflineFallback() = runBlocking {
        val ai = GhanshyamAiEngine(repository, context)
        val cust = repository.addCustomer("Harish Bhai", "9820044444")
        repository.addTransaction(cust.id, 6500.0, "GAVE", "Festival delivery")

        // 1. Query: who owes me more than 5000 (deterministic financial accuracy)
        val queryRes = ai.processQuery("who owes me more than 5000")
        assertTrue("Query should succeed", queryRes is AiResponse.Success)
        val reply = (queryRes as AiResponse.Success).replyText
        assertTrue("Reply lists Harish Bhai", reply.contains("Harish Bhai"))
        assertTrue("Reply includes exact outstanding amount 6500", reply.contains("6500"))

        // 2. Write action proposal (NEVER execute directly until user taps Apply)
        val proposalRes = ai.processQuery("Ramesh ko 500 udhar likho")
        assertTrue("Proposal should succeed", proposalRes is AiResponse.Success)
        val proposal = proposalRes as AiResponse.Success
        assertEquals("ADD_TRANSACTION", proposal.actionType)
        assertNotNull("Proposal card title generated", proposal.actionTitle)

        // Crucial safety check: Transaction NOT added yet in repository
        assertFalse("Direct write is blocked before confirmation", repository.transactions.value.any { it.amount == 500.0 })

        // User taps "Apply" -> triggers executeAction
        proposal.executeAction?.invoke()
        assertTrue("Transaction added after user confirmation", repository.transactions.value.any { it.amount == 500.0 })
    }

    @Test
    fun testChatPersistenceInRoom() = runBlocking {
        repository.clearChatMessages()
        assertEquals(0, repository.chatMessages.value.size)

        val m1 = repository.saveChatMessage(sender = "USER", text = "Kitna baaki hai?")
        val m2 = repository.saveChatMessage(
            sender = "AI",
            text = "Harish Bhai par 6500 baaki hai.",
            actionType = "ADD_PARTY",
            actionTitle = "Add New Party"
        )

        val msgs = repository.chatMessages.value
        assertEquals(2, msgs.size)
        val aiMsg = msgs.find { it.id == m2.id }
        assertNotNull(aiMsg)
        assertFalse("Proposal initially unapplied", aiMsg!!.isApplied)

        // Mark applied
        repository.markChatActionApplied(m2.id)
        val updatedAiMsg = repository.chatMessages.value.find { it.id == m2.id }
        assertTrue("Proposal marked as applied", updatedAiMsg!!.isApplied)

        // Test pruning to max 50
        for (i in 1..55) {
            repository.saveChatMessage("USER", "Test msg #$i")
        }
        assertTrue("Chat history must be capped at 50 messages", repository.chatMessages.value.size <= 50)
    }

    @Test
    fun testGeminiApiKeyEncryptedStorage() {
        SecurityManager.setGeminiApiKey(context, "AIzaSyTestApiKeySample123")
        val stored = SecurityManager.getGeminiApiKey(context)
        assertEquals("AIzaSyTestApiKeySample123", stored)

        val jsonBackup = repository.exportDataAsJsonString()
        assertFalse("API key must not leak into backups", jsonBackup.contains("AIzaSyTestApiKeySample123"))

        SecurityManager.clearGeminiApiKey(context)
        assertEquals("", SecurityManager.getGeminiApiKey(context))
    }
}

