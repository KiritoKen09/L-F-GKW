package com.example.ui.screens

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
import com.example.ui.util.LanguageStrings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StockScreen(
    repository: AppRepository
) {
    val currentLang by repository.language.collectAsState()
    val items by repository.inventory.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAddItemDialog by remember { mutableStateOf(false) }

    val filteredItems = items.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
        it.itemCode.contains(searchQuery, ignoreCase = true) ||
        it.hsnCode.contains(searchQuery, ignoreCase = true)
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
                placeholder = { Text("Search item name, code or HSN...", fontSize = 13.sp) },
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
                Text(
                    "Stock Items (${items.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                val lowStockCount = items.count { it.stock < it.lowStockThreshold }
                if (lowStockCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = UdharRed, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "$lowStockCount items low in stock",
                            style = MaterialTheme.typography.labelSmall,
                            color = UdharRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
                            if (items.isEmpty()) "Tap '+ Add Item' to stock your wholesale shelves" else "Try searching another keyword",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredItems) { item ->
                        val isLowStock = item.stock < item.lowStockThreshold
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            item.name,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        val subInfo = buildString {
                                            if (item.itemCode.isNotBlank()) append("Code: ${item.itemCode} • ")
                                            if (item.hsnCode.isNotBlank()) append("HSN: ${item.hsnCode} • ")
                                            append("GST: ${item.gstPercent}%")
                                        }
                                        Text(subInfo, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "₹${"%.2f".format(item.salePrice)}",
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        if (isLowStock) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(UdharRed.copy(alpha = 0.12f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    "Low Stock (${item.stock} left)",
                                                    color = UdharRed,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else {
                                            Text(
                                                "Total: ${item.stock} pcs",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = JamaGreen,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = CardBorder.copy(alpha = 0.6f))
                                Spacer(modifier = Modifier.height(10.dp))

                                // Size breakdown pills with quick increment / decrement
                                Text(
                                    "Stock per Size:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    item.sizeStock.forEach { (sz, qty) ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (qty < 3) UdharRed.copy(alpha = 0.08f) else ChipInactiveBg)
                                                .border(1.dp, if (qty < 3) UdharRed.copy(alpha = 0.3f) else CardBorder, RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "$sz: ",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "$qty",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 12.sp,
                                                color = if (qty < 3) UdharRed else PrimaryBlue
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(SurfaceWhite)
                                                    .clickable { repository.updateInventorySizeStock(item.id, sz, -1) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(12.dp), tint = TextSecondary)
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(PrimaryBlue)
                                                    .clickable { repository.updateInventorySizeStock(item.id, sz, 1) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(12.dp), tint = SurfaceWhite)
                                            }
                                        }
                                    }
                                }

                                if (item.packRatio.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Pack Size: ${item.packSize} pcs (${item.packRatio.map { "${it.key}:${it.value}" }.joinToString(", ")})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextTertiary
                                    )
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
        var hsn by remember { mutableStateOf("") }
        var salePrice by remember { mutableStateOf("") }
        var purchasePrice by remember { mutableStateOf("") }
        var gstText by remember { mutableStateOf("5") }
        var thresholdText by remember { mutableStateOf("5") }
        var sizesInput by remember { mutableStateOf("2-3Y, 4-5Y, 6-7Y") }
        var initialQtyPerSize by remember { mutableStateOf("12") }

        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            title = { Text("Add Wholesale Garment Item", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Item Name *") },
                        placeholder = { Text("e.g. Cotton Baba Suit Lot") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            label = { Text("Item Code") },
                            placeholder = { Text("BS-101") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = hsn,
                            onValueChange = { hsn = it },
                            label = { Text("HSN Code") },
                            placeholder = { Text("6111") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = salePrice,
                            onValueChange = { salePrice = it },
                            label = { Text("Sale Price (₹) *") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = purchasePrice,
                            onValueChange = { purchasePrice = it },
                            label = { Text("Cost Price (₹)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    // GST Rate & Legal Disclaimer
                    Column {
                        OutlinedTextField(
                            value = gstText,
                            onValueChange = { gstText = it },
                            label = { Text("GST Rate % * (0, 5, 12, 18)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Text(
                            text = LanguageStrings.get("gst_ca_note", currentLang),
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryBlue,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }

                    OutlinedTextField(
                        value = sizesInput,
                        onValueChange = { sizesInput = it },
                        label = { Text("Sizes (comma separated)") },
                        placeholder = { Text("e.g. 2-3Y, 4-5Y, 6-7Y or Free") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = initialQtyPerSize,
                            onValueChange = { initialQtyPerSize = it },
                            label = { Text("Qty Per Size") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = thresholdText,
                            onValueChange = { thresholdText = it },
                            label = { Text("Low Stock Alert") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = salePrice.toDoubleOrNull() ?: 0.0
                        val cost = purchasePrice.toDoubleOrNull() ?: 0.0
                        val g = gstText.toDoubleOrNull() ?: 0.0
                        val qtyPerSize = initialQtyPerSize.toIntOrNull() ?: 10
                        val threshold = thresholdText.toIntOrNull() ?: 5

                        val sizesList = sizesInput.split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .ifEmpty { listOf("Free") }

                        val sizeStockMap = sizesList.associateWith { qtyPerSize }

                        if (name.isNotBlank() && p > 0) {
                            repository.addInventoryItem(
                                name = name,
                                itemCode = code,
                                hsnCode = hsn,
                                salePrice = p,
                                purchasePrice = cost,
                                stock = sizeStockMap.values.sum(),
                                sizeStock = sizeStockMap,
                                sizes = sizesList,
                                gstPercent = g,
                                lowStockThreshold = threshold
                            )
                            showAddItemDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Save Wholesale Item", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddItemDialog = false }) { Text("Cancel") }
            }
        )
    }
}
