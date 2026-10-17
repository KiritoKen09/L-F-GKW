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
import com.example.model.Invoice
import com.example.model.InvoiceItem
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.util.PdfInvoiceGenerator
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BillingScreen(
    repository: AppRepository
) {
    val context = LocalContext.current
    val invoices by repository.invoices.collectAsState()
    val selectedTheme by repository.selectedInvoiceTheme.collectAsState()
    val storeProfile by repository.storeProfile.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: New Bill, 1: Past Invoices, 2: Theme & Design

    // State for New Bill (Document 1 form)
    var billNumber by remember { mutableStateOf("INV-${System.currentTimeMillis() % 100000}") }
    var billDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var paymentMode by remember { mutableStateOf("CASH") }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    val items = remember { mutableStateListOf<InvoiceItem>() }
    var additionalCharges by remember { mutableStateOf(0.0) }
    var discount by remember { mutableStateOf(0.0) }
    var customerGstin by remember { mutableStateOf("") }
    var stateOfSupply by remember { mutableStateOf("Delhi") }
    var notes by remember { mutableStateOf("Thank you for your business!") }

    val subtotal = items.sumOf { it.quantity * it.unitPrice }
    val gstAmount = items.sumOf { (it.quantity * it.unitPrice) * (it.gstPercent / 100.0) }
    val totalAmount = (subtotal + gstAmount + additionalCharges - discount).coerceAtLeast(0.0)

    var showSuccessDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = SoftBackground,
        topBar = {
            Column(modifier = Modifier.background(SurfaceWhite)) {
                // Secondary Tab Navigation (New Bill, History, Customization)
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
                    totalAmount = totalAmount,
                    subtotal = subtotal,
                    itemCount = items.size,
                    onSaveAndShare = {
                        val invoice = Invoice(
                            billNumber = billNumber,
                            date = billDate,
                            customerName = if (customerName.isNotBlank()) customerName else "Cash Customer",
                            customerPhone = customerPhone,
                            items = items.toList(),
                            subtotal = subtotal,
                            gstAmount = gstAmount,
                            discount = discount,
                            additionalCharges = additionalCharges,
                            totalAmount = totalAmount,
                            paymentMode = paymentMode,
                            stateOfSupply = stateOfSupply,
                            gstin = customerGstin,
                            notes = notes,
                            themeName = selectedTheme
                        )
                        repository.createInvoice(invoice)
                        showSuccessDialog = true
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
                    // TAB 0: Recreating fragment_add_bill.xml modular inclusions
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 20.dp)
                    ) {
                        BillInvoiceDateLayout(
                            dateMillis = billDate,
                            billNumber = billNumber,
                            onDateChange = { billDate = it },
                            onBillNumberChange = { billNumber = it }
                        )

                        BillInvoiceDetailLayout(
                            paymentMode = paymentMode,
                            onPaymentModeChange = { paymentMode = it }
                        )

                        BillAddPartyLayout(
                            customerName = customerName,
                            customerPhone = customerPhone,
                            onNameChange = { customerName = it },
                            onPhoneChange = { customerPhone = it }
                        )

                        BillAddItemsLayout(
                            items = items,
                            onAddItem = { items.add(it) },
                            onRemoveItem = { items.removeAt(it) }
                        )

                        BillAdditionalChargesLayout(
                            charges = additionalCharges,
                            discount = discount,
                            onChargesChange = { additionalCharges = it },
                            onDiscountChange = { discount = it }
                        )

                        BillGstinLayout(
                            gstin = customerGstin,
                            onGstinChange = { customerGstin = it }
                        )

                        BillStateOfSupplyLayout(
                            state = stateOfSupply,
                            onStateChange = { stateOfSupply = it }
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
                                Text("Create your first bill using the 'New Bill Form' tab", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
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
                                                Text(inv.billNumber, fontWeight = FontWeight.Bold, color = PrimaryBlue, style = MaterialTheme.typography.titleSmall)
                                                Text(dateStr, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                            }
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
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Customer: ${inv.customerName}", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                        Text("${inv.items.size} Items • Mode: ${inv.paymentMode}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider(color = CardBorder)
                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Total: ₹${"%.2f".format(inv.totalAmount)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = TextPrimary)

                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        PdfInvoiceGenerator.shareInvoicePdf(context, storeProfile, inv)
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
                                                        val shareText = "Tax Invoice #${inv.billNumber}\nCustomer: ${inv.customerName}\nTotal Amount: ₹${"%.2f".format(inv.totalAmount)}\nStatus: ${inv.paymentMode}\nThank you for shopping with ${storeProfile.storeName}!"
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
                    // TAB 2: Document 2 - bill_invoice_design_selection.xml
                    InvoiceDesignSelection(
                        currentTheme = selectedTheme,
                        onThemeSelected = { repository.setInvoiceTheme(it) },
                        onBack = { selectedTab = 0 }
                    )
                }
            }
        }
    }

    if (showSuccessDialog) {
        val currentInvoice = Invoice(
            billNumber = billNumber,
            date = billDate,
            customerName = if (customerName.isNotBlank()) customerName else "Cash Customer",
            customerPhone = customerPhone,
            items = items.toList(),
            subtotal = subtotal,
            gstAmount = gstAmount,
            discount = discount,
            additionalCharges = additionalCharges,
            totalAmount = totalAmount,
            paymentMode = paymentMode,
            stateOfSupply = stateOfSupply,
            gstin = customerGstin,
            notes = notes,
            themeName = selectedTheme
        )

        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                selectedTab = 1 // Switch to past invoices
            },
            title = { Text("Bill Saved Successfully! 🎉", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Invoice #$billNumber for ₹${"%.2f".format(totalAmount)} has been generated and saved. Stock and Khata have been updated.")
                    Text("Customer: ${if (customerName.isNotBlank()) customerName else "Cash Customer"} | Payment: $paymentMode", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            PdfInvoiceGenerator.shareInvoicePdf(context, storeProfile, currentInvoice)
                            showSuccessDialog = false
                            selectedTab = 1
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download / Share PDF", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val shareText = "Tax Invoice #$billNumber\nParty: ${if (customerName.isNotBlank()) customerName else "Customer"}\nTotal: ₹${"%.2f".format(totalAmount)}\nPayment: $paymentMode\n${storeProfile.storeName}"
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
                    Text("View Invoices")
                }
            }
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
    onDateChange: (Long) -> Unit,
    onBillNumberChange: (String) -> Unit
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
                    text = "Invoice #",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = billNumber,
                    onValueChange = onBillNumberChange,
                    modifier = Modifier.fillMaxWidth(0.9f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
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
                val modes = listOf("CASH", "UPI", "CREDIT (Udhar)")
                modes.forEach { mode ->
                    val isSelected = paymentMode == mode || (mode.startsWith("CREDIT") && paymentMode == "CREDIT")
                    val bg = if (isSelected) PrimaryBlue else ChipInactiveBg
                    val fg = if (isSelected) SurfaceWhite else TextPrimary
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .clickable {
                                onPaymentModeChange(if (mode.startsWith("CREDIT")) "CREDIT" else mode)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(mode, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun BillAddPartyLayout(
    customerName: String,
    customerPhone: String,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = "Party", tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Party / Customer Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = customerName,
                onValueChange = onNameChange,
                label = { Text("Customer Name") },
                placeholder = { Text("e.g. Rajesh Sharma") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = customerPhone,
                onValueChange = onPhoneChange,
                label = { Text("Phone Number") },
                placeholder = { Text("+91 98765 43210") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
fun BillAddItemsLayout(
    items: List<InvoiceItem>,
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
                            Text(item.name, fontWeight = FontWeight.SemiBold, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            Text("${item.quantity} x ₹${"%.2f".format(item.unitPrice)} | GST: ${item.gstPercent}%", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
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
        var name by remember { mutableStateOf("") }
        var price by remember { mutableStateOf("") }
        var qty by remember { mutableStateOf("1") }
        var gst by remember { mutableStateOf("5") }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Add Bill Item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Item Name") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price (₹)") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Qty") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    OutlinedTextField(value = gst, onValueChange = { gst = it }, label = { Text("GST %") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = price.toDoubleOrNull() ?: 0.0
                        val q = qty.toIntOrNull() ?: 1
                        val g = gst.toDoubleOrNull() ?: 0.0
                        if (name.isNotBlank() && p > 0) {
                            val total = (p * q) + (p * q * g / 100.0)
                            onAddItem(InvoiceItem(name = name, unitPrice = p, quantity = q, gstPercent = g, total = total))
                            showDialog = false
                        }
                    }
                ) {
                    Text("Add")
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
fun BillGstinLayout(
    gstin: String,
    onGstinChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text("GSTIN / Tax Identification", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = gstin,
                onValueChange = onGstinChange,
                placeholder = { Text("Enter Customer GSTIN (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
fun BillStateOfSupplyLayout(
    state: String,
    onStateChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text("State of Supply", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = state,
                onValueChange = onStateChange,
                placeholder = { Text("e.g. Delhi, Gujarat, Maharashtra") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
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
                Text("$itemCount Items | Subtotal: ₹${"%.2f".format(subtotal)}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
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

