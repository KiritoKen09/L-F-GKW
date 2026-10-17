package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.InvoiceThemeConfig
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDesignSelection(
    currentTheme: String,
    onThemeSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    val themes = remember {
        listOf(
            InvoiceThemeConfig("PREMIUM_BLUE", "Premium Blue", "#1E56A0", "#00ADB5", isPremium = true, badgeText = "POPULAR"),
            InvoiceThemeConfig("MODERN_GST", "Modern GST", "#163172", "#E2E8F0", isPremium = false, badgeText = "FREE"),
            InvoiceThemeConfig("ROYAL_GOLD", "Royal Gold", "#D4AF37", "#0F172A", isPremium = true, badgeText = "VIP"),
            InvoiceThemeConfig("MINIMAL_CLEAN", "Minimal Clean", "#2D3748", "#EDF2F7", isPremium = false, badgeText = "LIGHT"),
            InvoiceThemeConfig("EMERALD_GREEN", "Emerald Green", "#2E7D32", "#A5D6A7", isPremium = true, badgeText = "NEW")
        )
    }

    var selectedIndex by remember {
        mutableStateOf(themes.indexOfFirst { it.id == currentTheme }.coerceAtLeast(0))
    }

    var fontSize by remember { mutableStateOf("Medium") }
    var showWatermark by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invoice Customization", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(SoftBackground)
                .verticalScroll(rememberScrollState())
        ) {
            // Central Invoice Preview Carousel Box (Representing ViewPager with left/right buttons)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(290.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val activeTheme = themes[selectedIndex]

                    // Central Preview Representation
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Header simulated in the theme color
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(android.graphics.Color.parseColor(activeTheme.primaryColorHex)))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("TAX INVOICE", color = SurfaceWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Ghanshyam Traders", color = SurfaceWhite.copy(alpha = 0.9f), fontSize = 11.sp)
                            }
                            Text("#INV-2026-001", color = SurfaceWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        // Simulated Item Rows
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Item", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.Bold)
                                Text("Qty", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.Bold)
                                Text("Price", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.Bold)
                                Text("Total", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider(color = CardBorder)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Basmati Rice 5kg", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                Text("2", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                Text("₹450", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                Text("₹900", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Refined Oil 1L", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                Text("1", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                Text("₹145", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                Text("₹145", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        }

                        // Bottom simulated footer
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(ChipInactiveBg)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Grand Total", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                            Text("₹1,045.00", fontWeight = FontWeight.Bold, color = Color(android.graphics.Color.parseColor(activeTheme.primaryColorHex)), fontSize = 15.sp)
                        }
                    }

                    // Left Carousel Button
                    IconButton(
                        onClick = {
                            if (selectedIndex > 0) {
                                selectedIndex--
                                onThemeSelected(themes[selectedIndex].id)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 4.dp)
                            .background(SurfaceWhite.copy(alpha = 0.85f), CircleShape),
                        enabled = selectedIndex > 0
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous theme", tint = if (selectedIndex > 0) PrimaryBlue else TextTertiary)
                    }

                    // Right Carousel Button
                    IconButton(
                        onClick = {
                            if (selectedIndex < themes.size - 1) {
                                selectedIndex++
                                onThemeSelected(themes[selectedIndex].id)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp)
                            .background(SurfaceWhite.copy(alpha = 0.85f), CircleShape),
                        enabled = selectedIndex < themes.size - 1
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next theme", tint = if (selectedIndex < themes.size - 1) PrimaryBlue else TextTertiary)
                    }
                }
            }

            // Stylized Theme Settings / Premium Banner (as in bill_invoice_design_selection.xml)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = PremiumBannerBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(GoldAccent)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    themes[selectedIndex].badgeText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                themes[selectedIndex].name.uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = SurfaceWhite,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "GST compliant • Multi-page ready • Thermal & A4",
                            style = MaterialTheme.typography.bodySmall,
                            color = SurfaceWhite.copy(alpha = 0.7f)
                        )
                    }
                    Button(
                        onClick = { onThemeSelected(themes[selectedIndex].id) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Apply", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Horizontal Selector RecyclerView representation
            Text(
                "Choose Invoice Style",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(themes) { index, t ->
                    val isSelected = index == selectedIndex
                    Card(
                        modifier = Modifier
                            .width(130.dp)
                            .clickable {
                                selectedIndex = index
                                onThemeSelected(t.id)
                            },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) ChipActiveBg else SurfaceWhite),
                        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(PrimaryBlue)) else CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(t.primaryColorHex)))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                t.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                color = if (isSelected) PrimaryBlue else TextPrimary
                            )
                            if (t.isPremium) {
                                Text("PREMIUM", color = GoldAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("FREE", color = JamaGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Customization View (font size & watermark options)
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("Print Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Invoice Font Size", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Small", "Medium", "Large").forEach { size ->
                                val active = fontSize == size
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (active) PrimaryBlue else ChipInactiveBg)
                                        .clickable { fontSize = size }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(size, color = if (active) SurfaceWhite else TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = CardBorder)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Show UPI QR Code on Bill", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Switch(
                            checked = showWatermark,
                            onCheckedChange = { showWatermark = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryBlue)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
