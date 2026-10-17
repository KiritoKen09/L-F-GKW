package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppRepository
import com.example.model.Customer
import com.example.model.Transaction
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customer: Customer,
    repository: AppRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val transactions by repository.transactions.collectAsState()
    val customerTx = transactions.filter { it.customerId == customer.id }
    val balance = repository.getCustomerBalance(customer.id)

    var showAddTxDialog by remember { mutableStateOf(false) }
    var txTypeToAdd by remember { mutableStateOf("GAVE") } // GAVE or GOT

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(customer.name, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
                        Text(customer.phone, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val uri = Uri.parse("tel:${customer.phone}")
                        context.startActivity(Intent(Intent.ACTION_DIAL, uri))
                    }) {
                        Icon(Icons.Default.Call, contentDescription = "Call", tint = PrimaryBlue)
                    }
                    IconButton(onClick = {
                        val shareText = "Namaste ${customer.name}, Ghanshyam Traders ki taraf se aapka pending hisaab: ₹${"%.2f".format(balance)}. Kripya UPI id: ghanshyam@upi par bhugtan karein."
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share WhatsApp Reminder"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = JamaGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceWhite,
                shadowElevation = 8.dp,
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            txTypeToAdd = "GAVE"
                            showAddTxDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = UdharRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("You Gave (Udhar)", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            txTypeToAdd = "GOT"
                            showAddTxDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = JamaGreen),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("You Got (Jama)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(SoftBackground)
        ) {
            // Net Balance Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("CURRENT NET BALANCE", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "₹${"%.2f".format(Math.abs(balance))}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            balance > 0 -> UdharRed
                            balance < 0 -> JamaGreen
                            else -> TextPrimary
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            balance > 0 -> "You'll Receive (Aapko Lena Hai)"
                            balance < 0 -> "You'll Pay (Aapko Dena Hai)"
                            else -> "Account Settled (Hisaab Barabar)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ChipInactiveBg)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp), tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tick any entry below to cancel it out live", fontSize = 11.sp, color = TextPrimary)
                    }
                }
            }

            // Transactions Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transaction History (${customerTx.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Tap checkbox to cancel out",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            if (customerTx.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Receipt, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(54.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No entries yet for this customer", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                        Text("Add Udhar or Jama using buttons below", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(customerTx) { tx ->
                        val isGave = tx.type == "GAVE"
                        val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(tx.date))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (tx.isCancelled) ChipInactiveBg.copy(alpha = 0.5f) else SurfaceWhite
                            ),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Manual Tick Box (as requested)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Checkbox(
                                        checked = tx.isCancelled,
                                        onCheckedChange = {
                                            repository.toggleTransactionCancel(tx.id)
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = PrimaryBlue,
                                            uncheckedColor = TextSecondary
                                        )
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (isGave) "You Gave (Udhar)" else "You Got (Jama)",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (tx.isCancelled) TextSecondary else TextPrimary
                                            )
                                            if (tx.isCancelled) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(UdharRed.copy(alpha = 0.15f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        "CANCELLED OUT",
                                                        color = UdharRed,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (tx.note.isNotBlank()) "${tx.note} • $dateStr" else dateStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                // Amount with strikethrough if cancelled
                                Text(
                                    text = "${if (isGave) "-" else "+"}₹${"%.2f".format(tx.amount)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textDecoration = if (tx.isCancelled) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (tx.isCancelled) {
                                        TextTertiary
                                    } else {
                                        if (isGave) UdharRed else JamaGreen
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddTxDialog) {
        var amountText by remember { mutableStateOf("") }
        var noteText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddTxDialog = false },
            title = {
                Text(if (txTypeToAdd == "GAVE") "You Gave (Udhar)" else "You Got (Jama)", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Customer: ${customer.name}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount (₹)") },
                        placeholder = { Text("e.g. 500") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Note / Bill Description") },
                        placeholder = { Text("e.g. Grocery items") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            repository.addTransaction(
                                customerId = customer.id,
                                amount = amt,
                                type = txTypeToAdd,
                                note = noteText
                            )
                            showAddTxDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (txTypeToAdd == "GAVE") UdharRed else JamaGreen
                    )
                ) {
                    Text("Save Entry", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTxDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
