package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppRepository
import com.example.model.AppLanguage
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ShopProfileScreen(
    repository: AppRepository,
    onOpenGuide: () -> Unit
) {
    val context = LocalContext.current
    val profile by repository.storeProfile.collectAsState()
    val currentLang by repository.language.collectAsState()
    val isCloudSyncEnabled by repository.isCloudSyncEnabled.collectAsState()
    val lastCloudSyncTime by repository.lastCloudSyncTime.collectAsState()

    var storeName by remember(profile) { mutableStateOf(profile.storeName) }
    var address by remember(profile) { mutableStateOf(profile.address) }
    var phone by remember(profile) { mutableStateOf(profile.phone) }
    var upiId by remember(profile) { mutableStateOf(profile.upiId) }
    var gstin by remember(profile) { mutableStateOf(profile.gstin) }
    var ownerName by remember(profile) { mutableStateOf(profile.ownerName) }

    var isSavedSnackbar by remember { mutableStateOf(false) }

    // Khatabook Import Dialog state
    var showKhatabookDialog by remember { mutableStateOf(false) }
    var khatabookPhone by remember { mutableStateOf(profile.phone) }
    var importSuccessMsg by remember { mutableStateOf<String?>(null) }

    // Local JSON Restore Dialog state
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreJsonText by remember { mutableStateOf("") }
    var backupStatusMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 24.dp)
    ) {
        // Shop Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(ChipActiveBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Storefront, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(storeName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        Text("Business & GST Configuration", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = CardBorder)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text("Store / Shop Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Shop Address") },
                    placeholder = { Text("Full physical store address") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Store Phone Number") },
                        placeholder = { Text("+91 98765 43210") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = upiId,
                        onValueChange = { upiId = it },
                        label = { Text("Store UPI ID") },
                        placeholder = { Text("shop@upi") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = gstin,
                        onValueChange = { gstin = it },
                        label = { Text("GSTIN") },
                        placeholder = { Text("07AAAAA0000A1Z5") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = { ownerName = it },
                        label = { Text("Owner Name") },
                        placeholder = { Text("Proprietor Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        repository.updateStoreProfile(storeName, address, phone, upiId, gstin, ownerName)
                        isSavedSnackbar = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Store Information", fontWeight = FontWeight.Bold)
                }

                if (isSavedSnackbar) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("✅ Store details saved successfully!", color = JamaGreen, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Khatabook Import Card (as requested)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(JamaGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = JamaGreen, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Import from Khatabook", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Restore your customers & udhar via phone number backup", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { showKhatabookDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = JamaGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Khatabook Backup", fontWeight = FontWeight.Bold)
                }

                if (importSuccessMsg != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(importSuccessMsg!!, color = JamaGreen, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ==========================================
        // 1. Excel / CSV Spreadsheet Export Card
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ChipActiveBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Excel / CSV Spreadsheet Reports", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Export clean tabular data for Excel & Google Sheets", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Khata Ledger CSV
                OutlinedButton(
                    onClick = {
                        val file = repository.exportKhataLedgerCsv(context)
                        repository.shareCsvFile(context, file, "Open with Google Sheets / Excel or Share")
                        backupStatusMsg = "✅ Khata Ledger CSV exported successfully!"
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp), tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Khata Ledger (CSV / Excel)", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Sales Report CSV
                OutlinedButton(
                    onClick = {
                        val file = repository.exportSalesReportCsv(context)
                        repository.shareCsvFile(context, file, "Open with Google Sheets / Excel or Share")
                        backupStatusMsg = "✅ Sales Report CSV exported successfully!"
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp), tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Sales Report (CSV / Excel)", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Inventory Stock CSV
                OutlinedButton(
                    onClick = {
                        val file = repository.exportInventoryCsv(context)
                        repository.shareCsvFile(context, file, "Open with Google Sheets / Excel or Share")
                        backupStatusMsg = "✅ Inventory Stock CSV exported successfully!"
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp), tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Inventory Stock (CSV / Excel)", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ==========================================
        // 2. Automated Cloud Backup & Local File Backup Card
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isCloudSyncEnabled) JamaGreen.copy(alpha = 0.15f) else ChipInactiveBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = if (isCloudSyncEnabled) JamaGreen else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Automated Cloud Backup", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            if (isCloudSyncEnabled) "Firebase Cloud Sync: Active" else "Cloud Sync: Offline Only",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCloudSyncEnabled) JamaGreen else TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Switch(
                        checked = isCloudSyncEnabled,
                        onCheckedChange = { enabled ->
                            repository.setCloudSyncEnabled(enabled)
                            backupStatusMsg = if (enabled) "✅ Cloud backup enabled! Auto-sync active." else "Cloud sync paused. Using local storage."
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (isCloudSyncEnabled) {
                        "All customer ledgers, daily transactions, stock inventory, and invoices are automatically synced to secure cloud storage."
                    } else {
                        "Enable automated cloud backup to sync records seamlessly across devices and prevent data loss."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val lastSyncStr = if (lastCloudSyncTime > 0) {
                        "Last synced: " + SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(lastCloudSyncTime))
                    } else {
                        "Not synced yet"
                    }
                    Text(lastSyncStr, style = MaterialTheme.typography.labelSmall, color = TextSecondary)

                    FilledTonalButton(
                        onClick = {
                            repository.triggerCloudSync { success, msg ->
                                backupStatusMsg = msg
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = CardBorder)
                Spacer(modifier = Modifier.height(14.dp))

                Text("Local File Backup & Restore", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val backupFile = repository.exportBackupFile(context)
                            repository.shareBackupFile(context, backupFile)
                            backupStatusMsg = "✅ Full backup file generated: ${backupFile.name}"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export Backup", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            restoreJsonText = repository.exportDataAsJsonString()
                            showRestoreDialog = true
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restore Data", fontSize = 12.sp)
                    }
                }

                if (backupStatusMsg != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(backupStatusMsg!!, color = JamaGreen, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // App Guide & Walkthrough Card (as requested)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ChipActiveBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.School, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("App Guide & Tutorial", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("Learn how to use Smart Khata & AI", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenGuide,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Open Guide", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Data Management (Sample Data or Clear Data)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Demo & Clean Slate Options", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { repository.loadSampleData() },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Load Sample Data", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { repository.clearAllData() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = UdharRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear All Data", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showKhatabookDialog) {
        AlertDialog(
            onDismissRequest = { showKhatabookDialog = false },
            title = { Text("Import Khatabook Phone Backup", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter the phone number registered with Khatabook on Play Store to restore your customers and ledger:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = khatabookPhone,
                        onValueChange = { khatabookPhone = it },
                        label = { Text("Registered Phone Number") },
                        placeholder = { Text("+91 98200 12345") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val count = repository.importFromKhatabook(khatabookPhone)
                        importSuccessMsg = "✅ Successfully imported $count customers (including Rajesh Sharma with cancelled-out tick feature)!"
                        showKhatabookDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JamaGreen)
                ) {
                    Text("Import Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showKhatabookDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore Data Backup", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste or edit the JSON backup text below to restore your Khata, stock, and billing data:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = restoreJsonText,
                        onValueChange = { restoreJsonText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 220.dp),
                        maxLines = 10,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val success = repository.importDataFromJsonString(restoreJsonText)
                        if (success) {
                            backupStatusMsg = "✅ Full database restored successfully!"
                            showRestoreDialog = false
                        } else {
                            backupStatusMsg = "❌ Error: Invalid backup format. Please check JSON."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Restore Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text("Cancel") }
            }
        )
    }
}
