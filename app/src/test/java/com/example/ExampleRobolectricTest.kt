package com.example

import android.content.Context
import com.example.data.AppRepository
import com.example.model.AppLanguage
import com.example.ui.ai.AiResponse
import com.example.ui.ai.GhanshyamAiEngine
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
        repository = AppRepository(context)
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
    fun testTransactionCancelOutFeature() {
        val cust = repository.addCustomer("Rajesh Sharma", "+91 98200 12345")
        val txGave = repository.addTransaction(cust.id, 1850.0, "GAVE", "Old Udhar")
        val txGot = repository.addTransaction(cust.id, 400.0, "GOT", "Cash Payment")

        // Initial balance: 1850 - 400 = 1450
        assertEquals(1450.0, repository.getCustomerBalance(cust.id), 0.01)

        // Cancel out the 1850 udhar entry using the tick box feature
        repository.toggleTransactionCancel(txGave.id)

        // Balance must recalculate: 0 - 400 = -400 (shop owes customer or advance)
        assertEquals(-400.0, repository.getCustomerBalance(cust.id), 0.01)

        // Un-cancel
        repository.toggleTransactionCancel(txGave.id)
        assertEquals(1450.0, repository.getCustomerBalance(cust.id), 0.01)
    }

    @Test
    fun testKhatabookImport() {
        val count = repository.importFromKhatabook("+91 98200 12345")
        assertTrue("Must import customers", count > 0)
        val rajesh = repository.customers.value.find { it.name == "Rajesh Sharma" }
        assertNotNull("Rajesh Sharma must be imported", rajesh)
        val rajeshTx = repository.transactions.value.filter { it.customerId == rajesh!!.id }
        assertEquals("Rajesh must have 2 transactions (1850 gave and 400 got)", 2, rajeshTx.size)
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
        val invoice = com.example.model.Invoice(
            billNumber = "INV-TEST-001",
            date = System.currentTimeMillis(),
            customerName = "Priya Sharma",
            customerPhone = "+91 98765 00000",
            items = listOf(
                com.example.model.InvoiceItem(
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
    fun testCsvSpreadsheetExports() {
        // Add sample data
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
    fun testBackupAndRestore() {
        repository.addCustomer("Deepak Patel", "+91 98765 43210")
        val jsonBackup = repository.exportDataAsJsonString()
        assertTrue("Backup contains customers", jsonBackup.contains("Deepak Patel"))

        val backupFile = repository.exportBackupFile(context)
        assertTrue("Backup file created", backupFile.exists())
        assertTrue("Backup file has content", backupFile.length() > 0)

        // Clear and restore
        repository.clearAllData()
        assertTrue("Customers cleared", repository.customers.value.isEmpty())

        val restored = repository.importDataFromJsonString(jsonBackup)
        assertTrue("Restore succeeded", restored)
        assertEquals("Customer restored", 1, repository.customers.value.size)
        assertEquals("Deepak Patel", repository.customers.value[0].name)
    }

    @Test
    fun testCloudSyncToggleAndTrigger() {
        assertFalse("Cloud sync initially disabled", repository.isCloudSyncEnabled.value)
        repository.setCloudSyncEnabled(true)
        assertTrue("Cloud sync enabled", repository.isCloudSyncEnabled.value)
        assertTrue("Last cloud sync time updated", repository.lastCloudSyncTime.value > 0)
    }
}
