package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppRepository
import com.example.model.InventoryItem
import com.example.ui.theme.*

@Composable
fun StockScreen(
    repository: AppRepository
) {
    val items by repository.inventory.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAddItemDialog by remember { mutableStateOf(false) }

    val filteredItems = items.filter {
        it.name.contains(searchQuery, ignoreCase = true) || it.itemCode.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        containerColor = SoftBackground,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddItemDialog = true },
                containerColor = PrimaryBlue,
                contentColor = SurfaceWhite,
                icon = { Icon(Icons.Default.AddBox, contentDescription = "Add Item") },
                text = { Text("Add Item", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search item name or code...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite
                )
            )

            // Items Count and Low Stock Warning
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Stock Items (${items.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                val lowStockCount = items.count { it.stock < 5 }
                if (lowStockCount > 0) {
                    Text("$lowStockCount low in stock", style = MaterialTheme.typography.labelSmall, color = UdharRed, fontWeight = FontWeight.Bold)
                }
            }

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (items.isEmpty()) "Inventory is empty" else "No matching items",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (items.isEmpty()) "Tap '+ Add Item' to stock your shelves" else "Try searching another keyword",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredItems) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.Bold, color = TextPrimary, style = MaterialTheme.typography.titleSmall)
                                    Text("Code: ${item.itemCode.ifBlank { "N/A" }} • GST: ${item.gstPercent}%", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    if (item.sizes.isNotEmpty()) {
                                        Text("Sizes: ${item.sizes.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Sale: ₹${"%.2f".format(item.salePrice)}", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { repository.updateInventoryStock(item.id, -1) },
                                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).background(ChipInactiveBg)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease stock", tint = TextPrimary, modifier = Modifier.size(16.dp))
                                    }

                                    Text(
                                        text = "${item.stock}",
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp),
                                        color = if (item.stock < 5) UdharRed else TextPrimary,
                                        fontSize = 15.sp
                                    )

                                    IconButton(
                                        onClick = { repository.updateInventoryStock(item.id, 1) },
                                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).background(PrimaryBlue)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase stock", tint = SurfaceWhite, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddItemDialog) {
        var name by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }
        var price by remember { mutableStateOf("") }
        var purchasePrice by remember { mutableStateOf("") }
        var stockQty by remember { mutableStateOf("10") }
        var sizesText by remember { mutableStateOf("") }
        var gstText by remember { mutableStateOf("5") }

        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            title = { Text("Add Inventory Item", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Item Name *") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Code/Barcode") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = stockQty, onValueChange = { stockQty = it }, label = { Text("Initial Qty") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Sale Price (₹)") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = purchasePrice, onValueChange = { purchasePrice = it }, label = { Text("Purchase (₹)") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    OutlinedTextField(value = sizesText, onValueChange = { sizesText = it }, label = { Text("Sizes (comma separated: S, M, L)") }, singleLine = true)
                    OutlinedTextField(value = gstText, onValueChange = { gstText = it }, label = { Text("GST % (0, 5, 12, 18)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = price.toDoubleOrNull() ?: 0.0
                        val cost = purchasePrice.toDoubleOrNull() ?: 0.0
                        val qty = stockQty.toIntOrNull() ?: 0
                        val g = gstText.toDoubleOrNull() ?: 0.0
                        val sizesList = sizesText.split(",").map { it.trim() }.filter { it.isNotBlank() }

                        if (name.isNotBlank() && p > 0) {
                            repository.addInventoryItem(
                                name = name,
                                itemCode = code,
                                salePrice = p,
                                purchasePrice = cost,
                                stock = qty,
                                sizes = sizesList,
                                gstPercent = g
                            )
                            showAddItemDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Save Item", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddItemDialog = false }) { Text("Cancel") }
            }
        )
    }
}
