package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppRepository
import com.example.model.CsvCustomerPreview
import com.example.model.ImportResult
import com.example.ui.components.OperationProgressDialog
import com.example.ui.theme.*
import com.example.ui.util.LanguageStrings
import com.example.util.AppLockManager
import com.example.util.DailyReportScheduler
import com.example.util.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val INDIAN_STATES = listOf(
    "Gujarat", "Maharashtra", "Rajasthan", "Madhya Pradesh", "Delhi",
    "Uttar Pradesh", "Punjab", "Haryana", "Karnataka", "Tamil Nadu",
    "West Bengal", "Telangana", "Andhra Pradesh", "Kerala", "Bihar",
    "Odisha", "Assam", "Jharkhand", "Chhattisgarh", "Uttarakhand",
    "Goa", "Himachal Pradesh", "Jammu and Kashmir"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopProfileScreen(
    repository: AppRepository,
    onOpenGuide: () -> Unit
) {
    val context = LocalContext.current
    val currentLang by repository.language.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val profile by repository.storeProfile.collectAsState()

    var storeName by remember(profile) { mutableStateOf(profile.storeName) }
    var address by remember(profile) { mutableStateOf(profile.address) }
    var phone by remember(profile) { mutableStateOf(profile.phone) }
    var upiId by remember(profile) { mutableStateOf(profile.upiId) }
    var gstin by remember(profile) { mutableStateOf(profile.gstin) }
    var ownerName by remember(profile) { mutableStateOf(profile.ownerName) }
    var state by remember(profile) { mutableStateOf(profile.state.ifBlank { "Gujarat" }) }
    var logoUri by remember(profile) { mutableStateOf(profile.logoUri) }
    var stateExpanded by remember { mutableStateOf(false) }

    // Security & App Lock State
    var isPinSet by remember { mutableStateOf(SecurityManager.isPinSet(context)) }
    var isBiometricEnabled by remember { mutableStateOf(SecurityManager.isBiometricEnabled(context)) }
    val canUseBiometric = remember { SecurityManager.canAuthenticateBiometrics(context) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showDisablePinDialog by remember { mutableStateOf(false) }
    var showGeminiKeyDialog by remember { mutableStateOf(false) }
    var geminiApiKeyInput by remember { mutableStateOf(SecurityManager.getGeminiApiKey(context)) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            logoUri = uri.toString()
        }
    }

    var isSavedSnackbar by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var progressMessage by remember { mutableStateOf<String?>(null) }

    // CSV Import State
    var showCsvPreviewDialog by remember { mutableStateOf(false) }
    var csvPreviews by remember { mutableStateOf<List<CsvCustomerPreview>>(emptyList()) }

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                progressMessage = LanguageStrings.get("progress_loading", currentLang)
                try {
                    val content = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    }
                    val previews = withContext(Dispatchers.IO) {
                        repository.previewCustomersFromCsv(content)
                    }
                    if (previews.isEmpty()) {
                        Toast.makeText(context, "No valid customer rows found in CSV", Toast.LENGTH_LONG).show()
                    } else {
                        csvPreviews = previews
                        showCsvPreviewDialog = true
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ShopProfileScreen", "Failed to read CSV: ${e.message}", e)
                    Toast.makeText(context, "Failed to read CSV file", Toast.LENGTH_SHORT).show()
                } finally {
                    progressMessage = null
                }
            }
        }
    }

    // Export Backup File Launcher (CreateDocument)
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                progressMessage = LanguageStrings.get("progress_exporting", currentLang)
                try {
                    val json = withContext(Dispatchers.IO) { repository.exportDataAsJsonString() }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(json.toByteArray(Charsets.UTF_8))
                        }
                    }
                    statusMessage = "✅ Backup file saved successfully (Plain JSON)!"
                } catch (e: Exception) {
                    android.util.Log.e("ShopProfileScreen", "Failed to write backup: ${e.message}", e)
                    statusMessage = "❌ Error saving backup file: ${e.localizedMessage}"
                } finally {
                    progressMessage = null
                }
            }
        }
    }

    // Backup Before Delete Launcher
    val backupBeforeDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                progressMessage = LanguageStrings.get("progress_exporting", currentLang)
                try {
                    val json = withContext(Dispatchers.IO) { repository.exportDataAsJsonString() }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(json.toByteArray(Charsets.UTF_8))
                        }
                    }
                    withContext(Dispatchers.IO) { repository.clearAllData() }
                    statusMessage = "✅ Safety backup saved and all data cleared."
                } catch (e: Exception) {
                    android.util.Log.e("ShopProfileScreen", "Failed to save safety backup: ${e.message}", e)
                    statusMessage = "❌ Backup failed. Data was NOT cleared for safety."
                } finally {
                    progressMessage = null
                }
            }
        }
    }

    // Import Backup File Launcher (OpenDocument)
    var pendingImportJson by remember { mutableStateOf("") }
    var pendingImportValidation by remember { mutableStateOf<ImportResult?>(null) }
    var showImportSummaryDialog by remember { mutableStateOf(false) }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                progressMessage = LanguageStrings.get("progress_loading", currentLang)
                try {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    }
                    val validation = withContext(Dispatchers.IO) {
                        repository.validateBackupJson(json)
                    }
                    pendingImportJson = json
                    pendingImportValidation = validation
                    showImportSummaryDialog = true
                } catch (e: Exception) {
                    android.util.Log.e("ShopProfileScreen", "Failed to read backup: ${e.message}", e)
                    statusMessage = "❌ Error reading backup file: ${e.localizedMessage}"
                } finally {
                    progressMessage = null
                }
            }
        }
    }

    // Clear Data Dialog State
    var showClearDataDialog by remember { mutableStateOf(false) }
    var deleteInputText by remember { mutableStateOf("") }

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
                        Text(storeName.ifBlank { "Store Profile" }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        Text("Business & GST Configuration", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = CardBorder)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text(LanguageStrings.get("store_name_label")) },
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
                        label = { Text(LanguageStrings.get("store_phone")) },
                        placeholder = { Text("e.g. 9876543210") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = upiId,
                        onValueChange = { upiId = it },
                        label = { Text(LanguageStrings.get("upi_optional_label")) },
                        placeholder = { Text("e.g. shop@upi") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = gstin,
                        onValueChange = { gstin = it },
                        label = { Text(LanguageStrings.get("gstin_optional_label")) },
                        placeholder = { Text("e.g. 24AAAAA0000A1Z5") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = { ownerName = it },
                        label = { Text(LanguageStrings.get("owner_name_label")) },
                        placeholder = { Text("Proprietor Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // State Selector
                ExposedDropdownMenuBox(
                    expanded = stateExpanded,
                    onExpandedChange = { stateExpanded = it }
                ) {
                    OutlinedTextField(
                        value = state,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(LanguageStrings.get("state_label")) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stateExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = stateExpanded,
                        onDismissRequest = { stateExpanded = false }
                    ) {
                        INDIAN_STATES.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    state = item
                                    stateExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Optional Shop Logo Picker (Android Photo Picker)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ChipInactiveBg)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Invoice Shop Logo (Optional)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(if (!logoUri.isNullOrBlank()) "Custom logo selected for bills" else "No logo selected (Standard header)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!logoUri.isNullOrBlank()) {
                            IconButton(onClick = { logoUri = null }) {
                                Icon(Icons.Default.Clear, contentDescription = "Remove logo", tint = UdharRed)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (!logoUri.isNullOrBlank()) "Change" else "Choose", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        repository.updateStoreProfile(storeName, address, phone, upiId, gstin, ownerName, state, logoUri)
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

        // Real CSV Customer Import Card
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
                        Icon(Icons.Default.UploadFile, contentDescription = null, tint = JamaGreen, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(LanguageStrings.get("import_csv_title"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(LanguageStrings.get("import_csv_desc"), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        csvPickerLauncher.launch(arrayOf("text/comma-separated-values", "text/csv", "text/plain", "*/*"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JamaGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(LanguageStrings.get("btn_import_csv"), fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Excel / CSV Spreadsheet Export Card
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

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            progressMessage = LanguageStrings.get("progress_exporting", currentLang)
                            try {
                                val file = withContext(Dispatchers.IO) { repository.exportKhataLedgerCsv(context) }
                                repository.shareCsvFile(context, file, "Share Khata Ledger CSV")
                                statusMessage = "✅ Khata Ledger CSV exported: ${file.name}"
                            } catch (e: Exception) {
                                statusMessage = "❌ Export failed: ${e.message}"
                            } finally {
                                progressMessage = null
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp), tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Khata Ledger (CSV)", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            progressMessage = LanguageStrings.get("progress_exporting", currentLang)
                            try {
                                val file = withContext(Dispatchers.IO) { repository.exportSalesReportCsv(context) }
                                repository.shareCsvFile(context, file, "Share Sales Report CSV")
                                statusMessage = "✅ Sales Report CSV exported: ${file.name}"
                            } catch (e: Exception) {
                                statusMessage = "❌ Export failed: ${e.message}"
                            } finally {
                                progressMessage = null
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp), tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Sales Report (CSV)", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            progressMessage = LanguageStrings.get("progress_exporting", currentLang)
                            try {
                                val file = withContext(Dispatchers.IO) { repository.exportInventoryCsv(context) }
                                repository.shareCsvFile(context, file, "Share Inventory Stock CSV")
                                statusMessage = "✅ Inventory Stock CSV exported: ${file.name}"
                            } catch (e: Exception) {
                                statusMessage = "❌ Export failed: ${e.message}"
                            } finally {
                                progressMessage = null
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp), tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Inventory Stock (CSV)", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Honest Plain JSON Backup & Restore Card
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
                        Icon(Icons.Default.FolderZip, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(LanguageStrings.get("backup_file_title"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(LanguageStrings.get("backup_file_desc"), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            exportBackupLauncher.launch("ghanshyam_backup_$timeStamp.json")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(LanguageStrings.get("export_backup"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                progressMessage = LanguageStrings.get("progress_exporting", currentLang)
                                try {
                                    val backupFile = withContext(Dispatchers.IO) { repository.exportBackupFile(context) }
                                    repository.shareBackupFile(context, backupFile)
                                    statusMessage = "Backup shared: ${backupFile.name}"
                                } catch (e: Exception) {
                                    statusMessage = "❌ Backup share failed: ${e.message}"
                                } finally {
                                    progressMessage = null
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(LanguageStrings.get("share_backup"), fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        importBackupLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(LanguageStrings.get("restore_backup"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                if (statusMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(statusMessage!!, color = if (statusMessage!!.startsWith("✅")) JamaGreen else UdharRed, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // App Guide & Walkthrough Card
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

        // 1. App Security & 4-Digit PIN Lock Card
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
                            .background(if (isPinSet) JamaGreen.copy(alpha = 0.12f) else ChipActiveBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isPinSet) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (isPinSet) JamaGreen else PrimaryBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(LanguageStrings.get("security_settings_title", currentLang), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            text = if (isPinSet) LanguageStrings.get("pin_active", currentLang) else LanguageStrings.get("pin_disabled", currentLang),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isPinSet) JamaGreen else TextSecondary,
                            fontWeight = if (isPinSet) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isPinSet) {
                        Button(
                            onClick = { showSetPinDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Pin, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(LanguageStrings.get("set_pin_title", currentLang), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showChangePinDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(LanguageStrings.get("change_pin_title", currentLang), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { showDisablePinDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = UdharRed),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(LanguageStrings.get("disable_pin_title", currentLang), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (isPinSet) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    // Biometric Unlock Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(LanguageStrings.get("biometric_unlock_title", currentLang), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(LanguageStrings.get("biometric_unlock_desc", currentLang), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = { checked ->
                                SecurityManager.setBiometricEnabled(context, checked)
                                isBiometricEnabled = checked
                            },
                            enabled = canUseBiometric
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { AppLockManager.lockNow() },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(LanguageStrings.get("lock_now_btn", currentLang), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Evening Daily Report Card (WorkManager)
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
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(LanguageStrings.get("daily_report_title", currentLang), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(LanguageStrings.get("daily_report_desc", currentLang), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = LanguageStrings.get("daily_report_time", currentLang),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryBlue
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        DailyReportScheduler.triggerNow(context)
                        statusMessage = "✅ Test Daily Report notification dispatched!"
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(LanguageStrings.get("trigger_report_now", currentLang), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Gemini AI API Key Settings Card
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
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(LanguageStrings.get("gemini_api_key_title", currentLang), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(LanguageStrings.get("gemini_api_key_desc", currentLang), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                val currentKey = SecurityManager.getGeminiApiKey(context)
                Text(
                    text = if (currentKey.isNotBlank()) "Status: Key Active (Masked: ${currentKey.take(6)}...${currentKey.takeLast(4)})" else "Status: No custom key set (Using offline keyword engine fallback)",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (currentKey.isNotBlank()) JamaGreen else TextSecondary,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        geminiApiKeyInput = SecurityManager.getGeminiApiKey(context)
                        showGeminiKeyDialog = true
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Configure Gemini API Key", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Clear All Data Card (Strictly requires typing DELETE and exports safety backup)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(LanguageStrings.get("clear_data_title"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = UdharRed)
                Spacer(modifier = Modifier.height(4.dp))
                Text(LanguageStrings.get("clear_data_desc"), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        deleteInputText = ""
                        showClearDataDialog = true
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = UdharRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp), tint = UdharRed)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(LanguageStrings.get("clear_data_title"), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // CSV Preview Dialog
    if (showCsvPreviewDialog) {
        val newCount = csvPreviews.count { !it.isDuplicate }
        val dupCount = csvPreviews.count { it.isDuplicate }

        AlertDialog(
            onDismissRequest = { showCsvPreviewDialog = false },
            title = { Text(LanguageStrings.get("csv_preview_title"), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Total rows found: ${csvPreviews.size}")
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("New customers: $newCount", color = JamaGreen, fontWeight = FontWeight.Bold)
                        if (dupCount > 0) {
                            Text("Existing: $dupCount", color = TextSecondary)
                        }
                    }
                    HorizontalDivider()
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(csvPreviews) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    if (item.phone.isNotBlank()) {
                                        Text(item.phone, fontSize = 11.sp, color = TextSecondary)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = if (item.balance >= 0) "Lena: ₹${"%.2f".format(item.balance)}" else "Dena: ₹${"%.2f".format(-item.balance)}",
                                        color = if (item.balance >= 0) JamaGreen else UdharRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    if (item.isDuplicate) {
                                        Text("Exists (Balance updated)", fontSize = 10.sp, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            progressMessage = LanguageStrings.get("progress_importing_csv", currentLang)
                            try {
                                val imported = withContext(Dispatchers.IO) {
                                    repository.importCustomersFromCsv(csvPreviews)
                                }
                                statusMessage = "✅ Successfully imported $imported customers from CSV!"
                                showCsvPreviewDialog = false
                            } catch (e: Exception) {
                                statusMessage = "❌ Import failed: ${e.message}"
                            } finally {
                                progressMessage = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JamaGreen)
                ) {
                    Text("${LanguageStrings.get("btn_confirm_import")} ($newCount new)")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCsvPreviewDialog = false }) {
                    Text(LanguageStrings.get("btn_cancel"))
                }
            }
        )
    }

    // Import Backup Summary Dialog
    if (showImportSummaryDialog && pendingImportValidation != null) {
        val validation = pendingImportValidation!!
        AlertDialog(
            onDismissRequest = { showImportSummaryDialog = false },
            title = { Text(LanguageStrings.get("restore_summary_title"), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (validation.isValid) {
                        Text(LanguageStrings.get("restore_warning"), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("• Customers: ${validation.customerCount}", fontWeight = FontWeight.SemiBold)
                        Text("• Khata Transactions: ${validation.transactionCount}", fontWeight = FontWeight.SemiBold)
                        Text("• Inventory Items: ${validation.inventoryCount}", fontWeight = FontWeight.SemiBold)
                        Text("• Past Invoices: ${validation.invoiceCount}", fontWeight = FontWeight.SemiBold)
                        Text("• Cashbook Entries: ${validation.cashbookCount}", fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("❌ Invalid backup file:", color = UdharRed, fontWeight = FontWeight.Bold)
                        Text(validation.errorMessage ?: "Unknown error in backup file", color = UdharRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                if (validation.isValid) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                progressMessage = LanguageStrings.get("progress_restoring", currentLang)
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        repository.importDataFromJsonString(pendingImportJson)
                                    }
                                    if (result.success) {
                                        val extra = if (result.repairedCount > 0) " (${result.repairedCount} recovered entries attached to 'Recovered entries')" else ""
                                        statusMessage = "✅ ${result.message}$extra"
                                        showImportSummaryDialog = false
                                    } else {
                                        statusMessage = "❌ ${result.message}"
                                    }
                                } catch (e: Exception) {
                                    statusMessage = "❌ Restore failed: ${e.message}"
                                } finally {
                                    progressMessage = null
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text(LanguageStrings.get("btn_confirm_restore"), fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportSummaryDialog = false }) {
                    Text(LanguageStrings.get("btn_cancel"))
                }
            }
        )
    }

    // Clear Data Confirmation Dialog (Requires typing DELETE and triggers safety backup)
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text(LanguageStrings.get("clear_data_title"), fontWeight = FontWeight.Bold, color = UdharRed) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        LanguageStrings.get("clear_data_desc"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Text(
                        LanguageStrings.get("type_delete_hint"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = UdharRed
                    )
                    OutlinedTextField(
                        value = deleteInputText,
                        onValueChange = { deleteInputText = it },
                        placeholder = { Text("DELETE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDataDialog = false
                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        backupBeforeDeleteLauncher.launch("ghanshyam_backup_safety_$timeStamp.json")
                    },
                    enabled = deleteInputText.trim().equals("DELETE", ignoreCase = false),
                    colors = ButtonDefaults.buttonColors(containerColor = UdharRed)
                ) {
                    Text(LanguageStrings.get("btn_backup_and_clear"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text(LanguageStrings.get("btn_cancel"))
                }
            }
        )
    }

    // Set PIN Dialog
    if (showSetPinDialog) {
        var pin1 by remember { mutableStateOf("") }
        var pin2 by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showSetPinDialog = false },
            title = { Text(LanguageStrings.get("set_pin_title", currentLang), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter a 4-digit numeric PIN to secure your app.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    OutlinedTextField(
                        value = pin1,
                        onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) pin1 = it },
                        label = { Text(LanguageStrings.get("enter_new_pin", currentLang)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pin2,
                        onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) pin2 = it },
                        label = { Text(LanguageStrings.get("confirm_new_pin", currentLang)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinError != null) {
                        Text(pinError!!, color = UdharRed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pin1.length != 4) {
                            pinError = LanguageStrings.get("pin_must_be_4_digits", currentLang)
                            return@Button
                        }
                        if (pin1 != pin2) {
                            pinError = LanguageStrings.get("pin_mismatch", currentLang)
                            return@Button
                        }
                        SecurityManager.setPin(context, pin1)
                        isPinSet = true
                        showSetPinDialog = false
                        statusMessage = "✅ ${LanguageStrings.get("pin_saved_success", currentLang)}"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text(LanguageStrings.get("btn_save", currentLang), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetPinDialog = false }) {
                    Text(LanguageStrings.get("btn_cancel", currentLang))
                }
            }
        )
    }

    // Change PIN Dialog
    if (showChangePinDialog) {
        var currentPin by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showChangePinDialog = false },
            title = { Text(LanguageStrings.get("change_pin_title", currentLang), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) currentPin = it },
                        label = { Text(LanguageStrings.get("enter_current_pin", currentLang)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) newPin = it },
                        label = { Text(LanguageStrings.get("enter_new_pin", currentLang)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) confirmPin = it },
                        label = { Text(LanguageStrings.get("confirm_new_pin", currentLang)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinError != null) {
                        Text(pinError!!, color = UdharRed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!SecurityManager.verifyPin(context, currentPin)) {
                            pinError = LanguageStrings.get("incorrect_pin", currentLang)
                            return@Button
                        }
                        if (newPin.length != 4) {
                            pinError = LanguageStrings.get("pin_must_be_4_digits", currentLang)
                            return@Button
                        }
                        if (newPin != confirmPin) {
                            pinError = LanguageStrings.get("pin_mismatch", currentLang)
                            return@Button
                        }
                        SecurityManager.setPin(context, newPin)
                        showChangePinDialog = false
                        statusMessage = "✅ ${LanguageStrings.get("pin_saved_success", currentLang)}"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text(LanguageStrings.get("btn_save", currentLang), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePinDialog = false }) {
                    Text(LanguageStrings.get("btn_cancel", currentLang))
                }
            }
        )
    }

    // Disable PIN Dialog
    if (showDisablePinDialog) {
        var currentPin by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showDisablePinDialog = false },
            title = { Text(LanguageStrings.get("disable_pin_title", currentLang), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter your current PIN to disable app locking.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) currentPin = it },
                        label = { Text(LanguageStrings.get("enter_current_pin", currentLang)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinError != null) {
                        Text(pinError!!, color = UdharRed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!SecurityManager.verifyPin(context, currentPin)) {
                            pinError = LanguageStrings.get("incorrect_pin", currentLang)
                            return@Button
                        }
                        SecurityManager.removePin(context)
                        isPinSet = false
                        isBiometricEnabled = false
                        showDisablePinDialog = false
                        statusMessage = "✅ ${LanguageStrings.get("pin_disabled_success", currentLang)}"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = UdharRed)
                ) {
                    Text(LanguageStrings.get("btn_delete", currentLang), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisablePinDialog = false }) {
                    Text(LanguageStrings.get("btn_cancel", currentLang))
                }
            }
        )
    }

    // Gemini API Key Dialog
    if (showGeminiKeyDialog) {
        var keyInput by remember { mutableStateOf(geminiApiKeyInput) }
        var isVisible by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showGeminiKeyDialog = false },
            title = { Text(LanguageStrings.get("gemini_api_key_title", currentLang), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Paste your Gemini API key from Google AI Studio. It will be stored securely in EncryptedSharedPreferences and never compiled into the APK.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("AIzaSy...") },
                        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isVisible = !isVisible }) {
                                Icon(if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (keyInput.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                SecurityManager.clearGeminiApiKey(context)
                                geminiApiKeyInput = ""
                                showGeminiKeyDialog = false
                                statusMessage = "✅ ${LanguageStrings.get("gemini_key_cleared", currentLang)}"
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = UdharRed)
                        ) {
                            Text("Clear", color = UdharRed)
                        }
                    }
                    Button(
                        onClick = {
                            SecurityManager.setGeminiApiKey(context, keyInput)
                            geminiApiKeyInput = keyInput.trim()
                            showGeminiKeyDialog = false
                            statusMessage = "✅ ${LanguageStrings.get("gemini_key_saved", currentLang)}"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text(LanguageStrings.get("btn_save", currentLang), fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showGeminiKeyDialog = false }) {
                    Text(LanguageStrings.get("btn_cancel", currentLang))
                }
            }
        )
    }

    if (progressMessage != null) {
        OperationProgressDialog(message = progressMessage!!)
    }
}
