package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppRepository
import com.example.model.Customer
import com.example.model.InventoryItem
import com.example.model.Invoice
import com.example.model.InvoiceItem
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.util.LanguageStrings
import com.example.ui.util.NumberToWords
import com.example.ui.util.PdfInvoiceGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BillingScreen(
    repository: AppRepository
) {
    val context = LocalContext.current
    val currentLang by repository.language.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var isGeneratingPdf by remember { mutableStateOf(false) }

    val invoices by repository.invoices.collectAsState()
    val customers by repository.customers.collectAsState()
    val inventory by repository.inventory.collectAsState()
    val selectedTheme by repository.selectedInvoiceTheme.collectAsState()
    val storeProfile by repository.storeProfile.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: New Bill, 1: Past Invoices, 2: Theme & Design

    // State for New Bill
    var billDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var nextBillNumberPreview by remember(billDate, invoices.size) {
        mutableStateOf(repository.getNextBillNumberPreview(billDate))
    }
    var paymentMode by remember { mutableStateOf("CASH") }
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var customerGstin by remember { mutableStateOf("") }
    val items = remember { mutableStateListOf<InvoiceItem>() }
    var additionalCharges by remember { mutableStateOf(0.0) }
    var discount by remember { mutableStateOf(0.0) }
    var stateOfSupply by remember(storeProfile.state) { mutableStateOf(storeProfile.state.ifBlank { "Gujarat" }) }
    var notes by remember { mutableStateOf("Thank you for your business!") }

    val isInterState = !storeProfile.state.trim().equals(stateOfSupply.trim(), ignoreCase = true)

    // Dynamic calculations
    val processedItems = items.map { it.withGstSplit(isInterState) }
    val subtotal = processedItems.sumOf { it.taxableAmount }
    val totalCgst = processedItems.sumOf { it.cgstAmount }
    val totalSgst = processedItems.sumOf { it.sgstAmount }
    val totalIgst = processedItems.sumOf { it.igstAmount }
    val totalGst = totalCgst + totalSgst + totalIgst
    val exactTotal = (subtotal + totalGst + additionalCharges - discount).coerceAtLeast(0.0)
    val roundedTotal = Math.round(exactTotal).toDouble()
    val roundOff = roundedTotal - exactTotal

    var showSuccessDialog by remember { mutableStateOf(false) }
    var createdInvoiceForDialog by remember { mutableStateOf<Invoice?>(null) }
    var invoiceToCancel by remember { mutableStateOf<Invoice?>(null) }
    var cancelReasonInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = SoftBackground,
        topBar = {
            Column(modifier = Modifier.background(SurfaceWhite)) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SurfaceWhite,
                    contentColor = PrimaryBlue,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = PrimaryBlue
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text("New Bill Form", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
                        },
                        icon = { Icon(Icons.Default.PostAdd, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text("Past Bills (${invoices.size})", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
                        },
                        icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Text("Theme Design", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
                        },
                        icon = { Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        },
        bottomBar = {
            if (selectedTab == 0) {
                BillSummaryBottomLayout(
                    totalAmount = roundedTotal,
                    subtotal = subtotal,
                    itemCount = items.size,
                    roundOff = roundOff,
                    onSaveAndShare = {
                        try {
                            val inv = Invoice(
                                billNumber = "", // Empty triggers consecutive sequence allocation in repository transaction
                                date = billDate,
                                customerId = selectedCustomer?.id,
                                customerName = if (customerName.isNotBlank()) customerName else "Cash Customer",
                                customerPhone = customerPhone,
                                customerGstin = customerGstin,
                                items = processedItems,
                                subtotal = subtotal,
                                gstAmount = totalGst,
                                cgstAmount = totalCgst,
                                sgstAmount = totalSgst,
                                igstAmount = totalIgst,
                                isInterState = isInterState,
                                discount = discount,
                                additionalCharges = additionalCharges,
                                roundOff = roundOff,
                                totalAmount = roundedTotal,
                                paymentMode = paymentMode,
                                stateOfSupply = stateOfSupply,
                                notes = notes,
                                themeName = selectedTheme
                            )
                            val created = repository.createInvoice(inv)
                            createdInvoiceForDialog = created
                            showSuccessDialog = true

                            // Reset form fields
                            items.clear()
                            selectedCustomer = null
                            customerName = ""
                            customerPhone = ""
                            customerGstin = ""
                            additionalCharges = 0.0
                            discount = 0.0
                            nextBillNumberPreview = repository.getNextBillNumberPreview()
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Failed to generate bill"
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> {
                    // TAB 0: New Bill Form
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 20.dp)
                    ) {
                        BillInvoiceDateLayout(
                            dateMillis = billDate,
                            billNumber = nextBillNumberPreview,
                            onDateChange = {
                                billDate = it
                                nextBillNumberPreview = repository.getNextBillNumberPreview(it)
                            }
                        )

                        BillInvoiceDetailLayout(
                            paymentMode = paymentMode,
                            onPaymentModeChange = { paymentMode = it }
                        )

                        BillAddPartyLayout(
                            customers = customers,
                            selectedCustomer = selectedCustomer,
                            customerName = customerName,
                            customerPhone = customerPhone,
                            customerGstin = customerGstin,
                            onSelectCustomer = { cust ->
                                selectedCustomer = cust
                                if (cust != null) {
                                    customerName = cust.name
                                    customerPhone = cust.phone
                                    customerGstin = cust.gstin
                                }
                            },
                            onNameChange = { customerName = it },
                            onPhoneChange = { customerPhone = it },
                            onGstinChange = { customerGstin = it }
                        )

                        BillAddItemsLayout(
                            items = items,
                            inventory = inventory,
                            currentLang = currentLang,
                            onAddItem = { items.add(it) },
                            onRemoveItem = { items.removeAt(it) }
                        )

                        BillAdditionalChargesLayout(
                            charges = additionalCharges,
                            discount = discount,
                            onChargesChange = { additionalCharges = it },
                            onDiscountChange = { discount = it }
                        )

                        BillStateOfSupplyLayout(
                            storeState = storeProfile.state.ifBlank { "Gujarat" },
                            state = stateOfSupply,
                            onStateChange = { stateOfSupply = it }
                        )

                        // GST Breakdown Table & Round off display
                        TaxSummaryTableLayout(
                            items = processedItems,
                            isInterState = isInterState,
                            roundOff = roundOff,
                            totalAmount = roundedTotal,
                            currentLang = currentLang
                        )

                        BillAddOptionalInfoLayout(
                            notes = notes,
                            onNotesChange = { notes = it }
                        )

                        BillPrivateToYouLayout()
                    }
                }
                1 -> {
                    // TAB 1: Past Invoices
                    if (invoices.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(56.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No invoices created yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextSecondary)
                                Text("Create consecutive bills using the 'New Bill Form' tab", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(invoices) { inv ->
                                val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(inv.date))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                                    border = CardDefaults.outlinedCardBorder()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(inv.billNumber, fontWeight = FontWeight.Bold, color = PrimaryBlue, style = MaterialTheme.typography.titleSmall)
                                                    if (inv.isCancelled) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(UdharRed.copy(alpha = 0.15f))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                LanguageStrings.get("invoice_cancelled_badge", currentLang),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = UdharRed
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(dateStr, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (inv.paymentMode == "CREDIT") UdharRed.copy(alpha = 0.15f) else JamaGreen.copy(alpha = 0.15f))
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = inv.paymentMode,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (inv.paymentMode == "CREDIT") UdharRed else JamaGreen
                                                    )
                                                }

                                                // CANCEL action replaces delete (never delete a numbered invoice)
                                                if (!inv.isCancelled) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            invoiceToCancel = inv
                                                            cancelReasonInput = ""
                                                        },
                                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = UdharRed),
                                                        shape = RoundedCornerShape(6.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                        modifier = Modifier.height(28.dp)
                                                    ) {
                                                        Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Party: ${inv.customerName}", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                        val supplyNotice = if (inv.isInterState) "Inter-State (IGST)" else "Intra-State (CGST+SGST)"
                                        Text("${inv.items.size} Items • Mode: ${inv.paymentMode} • $supplyNotice", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

                                        if (inv.isCancelled && !inv.cancelReason.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Reason: ${inv.cancelReason}", style = MaterialTheme.typography.bodySmall, color = UdharRed, fontWeight = FontWeight.Medium)
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider(color = CardBorder)
                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Total: ₹${"%.2f".format(inv.totalAmount)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                                if (Math.abs(inv.roundOff) > 0.001) {
                                                    val sign = if (inv.roundOff >= 0) "+" else ""
                                                    Text("Round off: $sign₹${"%.2f".format(inv.roundOff)}", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                                                }
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            isGeneratingPdf = true
                                                            withContext(Dispatchers.IO) {
                                                                PdfInvoiceGenerator.shareInvoicePdf(context, storeProfile, inv)
                                                            }
                                                            isGeneratingPdf = false
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryBlue)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("PDF Bill", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                                                }

                                                Button(
                                                    onClick = {
                                                        val cancelText = if (inv.isCancelled) " [CANCELLED]" else ""
                                                        val shareText = "Invoice #${inv.billNumber}$cancelText\nParty: ${inv.customerName}\nTotal Amount: ₹${"%.2f".format(inv.totalAmount)}\nStatus: ${inv.paymentMode}\nThank you for shopping with ${storeProfile.storeName}!"
                                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                                            type = "text/plain"
                                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                                        }
                                                        context.startActivity(Intent.createChooser(intent, "Share Invoice via WhatsApp"))
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = JamaGreen),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // TAB 2: Design Selection
                    InvoiceDesignSelection(
                        currentTheme = selectedTheme,
                        onThemeSelected = { repository.setInvoiceTheme(it) },
                        onBack = { selectedTab = 0 }
                    )
                }
            }
        }
    }

    // Success Dialog after Saving Bill
    if (showSuccessDialog && createdInvoiceForDialog != null) {
        val inv = createdInvoiceForDialog!!
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                selectedTab = 1
            },
            title = { Text("Bill Saved: #${inv.billNumber} 🎉", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Invoice #${inv.billNumber} for ₹${"%.2f".format(inv.totalAmount)} has been consecutively saved. Stock per size and Khata/Cashbook have been synced.")
                    Text("Party: ${inv.customerName} | Mode: ${inv.paymentMode}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("Total in Words: ${NumberToWords.convert(inv.totalAmount)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = PrimaryBlue)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isGeneratingPdf = true
                                withContext(Dispatchers.IO) {
                                    PdfInvoiceGenerator.shareInvoicePdf(context, storeProfile, inv)
                                }
                                isGeneratingPdf = false
                            }
                            showSuccessDialog = false
                            selectedTab = 1
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PDF Bill", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val shareText = "Tax Invoice #${inv.billNumber}\nParty: ${inv.customerName}\nTotal: ₹${"%.2f".format(inv.totalAmount)}\nPayment: ${inv.paymentMode}\n${storeProfile.storeName}"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Bill"))
                            showSuccessDialog = false
                            selectedTab = 1
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = JamaGreen)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", fontWeight = FontWeight.Bold, color = JamaGreen)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                        selectedTab = 1
                    }
                ) {
                    Text("View Past Bills")
                }
            }
        )
    }

    // Cancel Invoice Dialog with Reason Prompt (Preserves Number, Reverses Stock & Money)
    if (invoiceToCancel != null) {
        val inv = invoiceToCancel!!
        AlertDialog(
            onDismissRequest = { invoiceToCancel = null },
            title = { Text(LanguageStrings.get("cancel_invoice_title", currentLang), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Invoice #${inv.billNumber} will be marked CANCELLED. The bill number stays reserved in the sequence.")
                    Text(LanguageStrings.get("cancel_invoice_desc", currentLang), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    OutlinedTextField(
                        value = cancelReasonInput,
                        onValueChange = { cancelReasonInput = it },
                        label = { Text(LanguageStrings.get("cancel_reason_label", currentLang)) },
                        placeholder = { Text("e.g. Goods returned / wrong party") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.cancelInvoice(inv.id, cancelReasonInput)
                        invoiceToCancel = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = UdharRed)
                ) {
                    Text(LanguageStrings.get("btn_confirm_cancel", currentLang), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { invoiceToCancel = null }) {
                    Text("Back")
                }
            }
        )
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Stock / Billing Error", fontWeight = FontWeight.Bold, color = UdharRed) },
            text = { Text(errorMessage!!) },
            confirmButton = {
                Button(onClick = { errorMessage = null }) {
                    Text("OK")
                }
            }
        )
    }

    if (isGeneratingPdf) {
        OperationProgressDialog(
            message = LanguageStrings.get("progress_generating_pdf", currentLang),
            subMessage = "Rendering multi-page GST layout..."
        )
    }
}

// ==========================================
// BILL MODULAR LAYOUT COMPONENTS
// ==========================================

@Composable
fun BillInvoiceDateLayout(
    dateMillis: Long,
    billNumber: String,
    onDateChange: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Next Consecutive Series",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = billNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = PrimaryBlue
                )
                Text(
                    text = "Auto-allocated inside database transaction",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    fontSize = 10.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Bill Date",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(dateMillis))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ChipInactiveBg)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = "Date", modifier = Modifier.size(16.dp), tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(dateStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
            }
        }
    }
}

@Composable
fun BillInvoiceDetailLayout(
    paymentMode: String,
    onPaymentModeChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text("Transaction Type / Payment Mode", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val modes = listOf("CASH", "UPI", "CREDIT")
                modes.forEach { mode ->
                    val isSelected = paymentMode == mode
                    val bg = if (isSelected) PrimaryBlue else ChipInactiveBg
                    val fg = if (isSelected) SurfaceWhite else TextPrimary
                    val label = if (mode == "CREDIT") "CREDIT (Udhar)" else mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .clickable { onPaymentModeChange(mode) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun BillAddPartyLayout(
    customers: List<Customer>,
    selectedCustomer: Customer?,
    customerName: String,
    customerPhone: String,
    customerGstin: String,
    onSelectCustomer: (Customer?) -> Unit,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onGstinChange: (String) -> Unit
) {
    var showPartyPicker by remember { mutableStateOf(false) }
    var partySearchQuery by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = "Party", tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Party / Customer Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                }

                FilledTonalButton(
                    onClick = { showPartyPicker = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pick From Khata", fontSize = 12.sp)
                }
            }

            if (selectedCustomer != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(PrimaryBlue.copy(alpha = 0.1f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Linked to Khata: ${selectedCustomer.name}", fontWeight = FontWeight.Bold, color = PrimaryBlue, fontSize = 12.sp)
                        Text("Phone: ${selectedCustomer.phone}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    TextButton(onClick = { onSelectCustomer(null) }) {
                        Text("Clear", color = UdharRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = customerName,
                onValueChange = onNameChange,
                label = { Text("Customer / Party Name *") },
                placeholder = { Text("e.g. Ramesh Cloth Store") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = customerPhone,
                    onValueChange = onPhoneChange,
                    label = { Text("Phone Number") },
                    placeholder = { Text("98765 43210") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = customerGstin,
                    onValueChange = onGstinChange,
                    label = { Text("Customer GSTIN") },
                    placeholder = { Text("24AAAAA0000A1Z5") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }
    }

    if (showPartyPicker) {
        val filteredParties = customers.filter {
            it.name.contains(partySearchQuery, ignoreCase = true) || it.phone.contains(partySearchQuery)
        }
        AlertDialog(
            onDismissRequest = { showPartyPicker = false },
            title = { Text("Select Party From Khata", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                    OutlinedTextField(
                        value = partySearchQuery,
                        onValueChange = { partySearchQuery = it },
                        placeholder = { Text("Search party by name or phone...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (filteredParties.isEmpty()) {
                        Text("No parties found", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(filteredParties) { party ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            onSelectCustomer(party)
                                            showPartyPicker = false
                                        }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(party.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text(party.phone, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    }
                                    Text(party.type, style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPartyPicker = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BillAddItemsLayout(
    items: List<InvoiceItem>,
    inventory: List<InventoryItem>,
    currentLang: com.example.model.AppLanguage,
    onAddItem: (InvoiceItem) -> Unit,
    onRemoveItem: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Items (${items.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                FilledTonalButton(
                    onClick = { showDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Item")
                }
            }

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No items added yet. Tap '+ Add Item'", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ChipInactiveBg.copy(alpha = 0.5f))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val sizeTag = if (item.size.isNotBlank() && item.size != "Free") " [Size: ${item.size}]" else ""
                            Text("${item.name}$sizeTag", fontWeight = FontWeight.SemiBold, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            val hsnPart = if (item.hsnCode.isNotBlank()) "HSN: ${item.hsnCode} • " else ""
                            Text("${item.quantity} x ₹${"%.2f".format(item.unitPrice)} | ${hsnPart}GST: ${item.gstPercent}%", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Text("₹${"%.2f".format(item.total)}", fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        IconButton(onClick = { onRemoveItem(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete item", tint = UdharRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        var selectedInventoryItem by remember { mutableStateOf<InventoryItem?>(null) }
        var name by remember { mutableStateOf("") }
        var price by remember { mutableStateOf("") }
        var qty by remember { mutableStateOf("1") }
        var hsn by remember { mutableStateOf("") }
        var gst by remember { mutableStateOf("") }
        var selectedSize by remember { mutableStateOf("Free") }
        var validationError by remember { mutableStateOf<String?>(null) }

        val availableStock = remember(selectedInventoryItem, selectedSize) {
            val item = selectedInventoryItem
            if (item != null) {
                item.sizeStock[selectedSize] ?: item.stock
            } else 9999
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Add Wholesale Item") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Quick Pick from existing inventory
                    if (inventory.isNotEmpty()) {
                        Text("Select From Inventory:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 110.dp).verticalScroll(rememberScrollState())
                        ) {
                            inventory.forEach { invItem ->
                                val isSel = selectedInventoryItem?.id == invItem.id
                                FilterChip(
                                    selected = isSel,
                                    onClick = {
                                        selectedInventoryItem = invItem
                                        name = invItem.name
                                        price = "%.2f".format(invItem.salePrice)
                                        hsn = invItem.hsnCode
                                        gst = "${invItem.gstPercent}"
                                        selectedSize = invItem.sizeStock.keys.firstOrNull() ?: "Free"
                                    },
                                    label = { Text(invItem.name, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Item Name *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Available Sizes Selector if inventory item picked

                    if (selectedInventoryItem != null && selectedInventoryItem!!.sizeStock.isNotEmpty()) {
                        Text("Select Size (Stock Available):", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            selectedInventoryItem!!.sizeStock.forEach { (sz, stockQty) ->
                                FilterChip(
                                    selected = selectedSize == sz,
                                    onClick = { selectedSize = sz },
                                    label = { Text("$sz ($stockQty avail)", fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = price,
                            onValueChange = { price = it },
                            label = { Text("Price (₹) *") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = qty,
                            onValueChange = { qty = it },
                            label = { Text("Qty *") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = hsn,
                            onValueChange = { hsn = it },
                            label = { Text("HSN Code") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = gst,
                            onValueChange = { gst = it },
                            label = { Text("GST Rate % *") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    // CA disclaimer required
                    Text(
                        text = LanguageStrings.get("gst_ca_note", currentLang),
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Medium
                    )

                    if (validationError != null) {
                        Text(validationError!!, color = UdharRed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = price.toDoubleOrNull() ?: 0.0
                        val q = qty.toIntOrNull() ?: 1
                        val g = gst.toDoubleOrNull() ?: 0.0

                        if (name.isBlank() || p <= 0) {
                            validationError = "Please enter valid item name and price"
                            return@Button
                        }

                        // Block selling more than stock
                        if (selectedInventoryItem != null && q > availableStock) {
                            validationError = "Insufficient stock for $selectedSize! Only $availableStock available."
                            return@Button
                        }

                        val lineItem = InvoiceItem(
                            itemId = selectedInventoryItem?.id,
                            name = name,
                            size = selectedSize,
                            hsnCode = hsn,
                            quantity = q,
                            unitPrice = p,
                            gstPercent = g
                        )
                        onAddItem(lineItem)
                        showDialog = false
                    }
                ) {
                    Text("Add Item")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun BillAdditionalChargesLayout(
    charges: Double,
    discount: Double,
    onChargesChange: (Double) -> Unit,
    onDiscountChange: (Double) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text("Additional Charges & Discounts", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = if (charges == 0.0) "" else charges.toString(),
                    onValueChange = { onChargesChange(it.toDoubleOrNull() ?: 0.0) },
                    label = { Text("Delivery/Packaging (₹)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = if (discount == 0.0) "" else discount.toString(),
                    onValueChange = { onDiscountChange(it.toDoubleOrNull() ?: 0.0) },
                    label = { Text("Discount (₹)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }
    }
}

@Composable
fun BillStateOfSupplyLayout(
    storeState: String,
    state: String,
    onStateChange: (String) -> Unit
) {
    val isSameState = storeState.trim().equals(state.trim(), ignoreCase = true)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Place of Supply", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSameState) PrimaryBlue.copy(alpha = 0.12f) else JamaGreen.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isSameState) "Intra-State: CGST + SGST (50% each)" else "Inter-State: IGST (100%)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSameState) PrimaryBlue else JamaGreen
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = state,
                onValueChange = onStateChange,
                placeholder = { Text("e.g. Gujarat, Maharashtra, Rajasthan") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
fun TaxSummaryTableLayout(
    items: List<InvoiceItem>,
    isInterState: Boolean,
    roundOff: Double,
    totalAmount: Double,
    currentLang: com.example.model.AppLanguage
) {
    if (items.isEmpty()) return

    val breakdown = items.groupBy { it.gstPercent }.map { (rate, lineItems) ->
        val taxable = lineItems.sumOf { it.taxableAmount }
        val cgst = lineItems.sumOf { it.cgstAmount }
        val sgst = lineItems.sumOf { it.sgstAmount }
        val igst = lineItems.sumOf { it.igstAmount }
        com.example.model.GstRateBreakdown(
            rate = rate,
            taxableAmount = taxable,
            cgstAmount = cgst,
            sgstAmount = sgst,
            igstAmount = igst,
            totalTax = cgst + sgst + igst
        )
    }.sortedBy { it.rate }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text("Tax Summary Breakdown By Rate", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))

            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(ChipInactiveBg)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Rate", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                Text("Taxable", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextSecondary, modifier = Modifier.weight(1.5f))
                if (isInterState) {
                    Text("IGST", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextSecondary, modifier = Modifier.weight(1.5f))
                } else {
                    Text("CGST", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextSecondary, modifier = Modifier.weight(1.2f))
                    Text("SGST", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextSecondary, modifier = Modifier.weight(1.2f))
                }
                Text("Total Tax", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextSecondary, modifier = Modifier.weight(1.5f))
            }

            Spacer(modifier = Modifier.height(4.dp))
            breakdown.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${row.rate}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
                    Text("₹${"%.2f".format(row.taxableAmount)}", fontSize = 11.sp, color = TextPrimary, modifier = Modifier.weight(1.5f))
                    if (isInterState) {
                        Text("₹${"%.2f".format(row.igstAmount)}", fontSize = 11.sp, color = TextPrimary, modifier = Modifier.weight(1.5f))
                    } else {
                        Text("₹${"%.2f".format(row.cgstAmount)}", fontSize = 11.sp, color = TextPrimary, modifier = Modifier.weight(1.2f))
                        Text("₹${"%.2f".format(row.sgstAmount)}", fontSize = 11.sp, color = TextPrimary, modifier = Modifier.weight(1.2f))
                    }
                    Text("₹${"%.2f".format(row.totalTax)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue, modifier = Modifier.weight(1.5f))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = CardBorder)
            Spacer(modifier = Modifier.height(8.dp))

            // Round-off line
            if (Math.abs(roundOff) > 0.001) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(LanguageStrings.get("round_off", currentLang), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    val sign = if (roundOff >= 0) "+" else ""
                    Text("$sign₹${"%.2f".format(roundOff)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Total in Words
            val words = NumberToWords.convert(totalAmount)
            Text(
                text = "${LanguageStrings.get("amount_in_words", currentLang)}: $words",
                style = MaterialTheme.typography.labelSmall,
                color = PrimaryBlue,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun BillAddOptionalInfoLayout(
    notes: String,
    onNotesChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text("Optional Terms & Notes", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChange,
                placeholder = { Text("e.g. Goods once sold will not be returned") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )
        }
    }
}

@Composable
fun BillPrivateToYouLayout() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = ChipInactiveBg.copy(alpha = 0.4f)),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Lock, contentDescription = "Private", tint = TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Private to you: Bills are securely encrypted and kept private.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
fun BillSummaryBottomLayout(
    totalAmount: Double,
    subtotal: Double,
    itemCount: Int,
    roundOff: Double,
    onSaveAndShare: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceWhite,
        shadowElevation = 12.dp,
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                val roundText = if (Math.abs(roundOff) > 0.001) " • Rnd: ${if (roundOff >= 0) "+" else ""}₹${"%.2f".format(roundOff)}" else ""
                Text("$itemCount Items | Sub: ₹${"%.2f".format(subtotal)}$roundText", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text("Total: ₹${"%.2f".format(totalAmount)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PrimaryBlue)
            }
            Button(
                onClick = onSaveAndShare,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Bill", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
