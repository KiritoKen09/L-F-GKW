package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppRepository
import com.example.model.AppLanguage
import com.example.model.Customer
import com.example.ui.ai.AiAssistantModal
import com.example.ui.components.AppLoadingScreen
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.util.LanguageStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.example.util.AppLockManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GhanshyamApp(
    repository: AppRepository,
    initialDailyReportContent: String? = null,
    openAiOnLaunch: Boolean = false
) {
    val isAppLocked by AppLockManager.isLocked.collectAsState()
    val isInitialLoading by repository.isInitialLoading.collectAsState()
    val isMigrationBlocked by repository.isMigrationBlocked.collectAsState()
    val migrationError by repository.migrationError.collectAsState()
    val migrationNotice by repository.migrationNotice.collectAsState()
    val hasCompletedOnboarding by repository.hasCompletedOnboarding.collectAsState()
    val currentLanguage by repository.language.collectAsState()
    val storeProfile by repository.storeProfile.collectAsState()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showStartFreshDialog by rememberSaveable { mutableStateOf(false) }
    var startFreshInputText by rememberSaveable { mutableStateOf("") }

    val exportDamagedLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val rawJson = repository.getDamagedLegacyJson()
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(rawJson.toByteArray(Charsets.UTF_8))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("GhanshyamApp", "Export damaged data error: ${e.message}", e)
                }
            }
        }
    }

    // App Startup Loading Screen: prevents flashing OnboardingScreen for existing users
    if (isInitialLoading) {
        AppLoadingScreen(message = LanguageStrings.get("progress_loading", currentLanguage))
        return
    }

    // App Lock Gate: prompts 4-digit PIN or biometrics if app is locked
    if (isAppLocked) {
        LockScreen(
            currentLang = currentLanguage,
            onUnlock = { AppLockManager.unlock() }
        )
        return
    }

    var showAiModal by rememberSaveable { mutableStateOf(openAiOnLaunch) }
    var showLanguageMenu by rememberSaveable { mutableStateOf(false) }
    var currentTab by rememberSaveable { mutableStateOf(0) } // 0: Khata, 1: Billing, 2: Stock, 3: Cashbook, 4: Shop
    var selectedCustomerId by rememberSaveable { mutableStateOf<String?>(null) }
    var showGuideModal by rememberSaveable { mutableStateOf(false) }

    val customers by repository.customers.collectAsState()
    val selectedCustomer = customers.find { it.id == selectedCustomerId }

    // If onboarding is not completed yet, show OnboardingScreen (unless migration is blocked)!
    if (!isMigrationBlocked && (!hasCompletedOnboarding || showGuideModal)) {
        OnboardingScreen(
            repository = repository,
            isTutorialMode = showGuideModal,
            onFinish = {
                repository.completeOnboarding()
                showGuideModal = false
            }
        )
        return
    }

    // Detail Screen BackHandler
    if (selectedCustomer != null) {
        BackHandler { selectedCustomerId = null }
        CustomerDetailScreen(
            customer = selectedCustomer,
            repository = repository,
            onBack = { selectedCustomerId = null }
        )
        return
    }

    Scaffold(
        containerColor = SoftBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Store, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (storeProfile.storeName.isNotBlank()) storeProfile.storeName else "Ghanshyam Kids Wear",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Ghanshyam Smart Billing",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                },
                actions = {
                    // Universal Language Selection Button (Hinglish default, Hindi, Gujarati, English)
                    Box {
                        FilledTonalButton(
                            onClick = { showLanguageMenu = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = ChipInactiveBg,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = "Language", modifier = Modifier.size(16.dp), tint = PrimaryBlue)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(currentLanguage.displayName, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }

                        DropdownMenu(
                            expanded = showLanguageMenu,
                            onDismissRequest = { showLanguageMenu = false },
                            modifier = Modifier.background(SurfaceWhite)
                        ) {
                            AppLanguage.entries.forEach { lang ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${lang.displayName} (${lang.nativeName})",
                                                fontWeight = if (lang == currentLanguage) FontWeight.Bold else FontWeight.Normal,
                                                color = if (lang == currentLanguage) PrimaryBlue else TextPrimary
                                            )
                                            if (lang == AppLanguage.HINGLISH) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(ChipActiveBg)
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Default", fontSize = 9.sp, color = PrimaryBlue, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    },
                                    onClick = {
                                        repository.setLanguage(lang)
                                        showLanguageMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Voice AI Assistant Button (placed directly next to Language button)
                    IconButton(
                        onClick = { showAiModal = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue)
                    ) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = "Voice AI Assistant",
                            tint = SurfaceWhite,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
            )
        },
        bottomBar = {
            // High contrast, spacious 80.dp bottom bar ensuring full word visibility without clipping
            NavigationBar(
                containerColor = SurfaceWhite,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .border(width = 1.dp, color = CardBorder)
            ) {
                val navItems = listOf(
                    Triple(0, LanguageStrings.get("tab_khata", currentLanguage), Icons.Default.AccountBalanceWallet),
                    Triple(1, LanguageStrings.get("tab_billing", currentLanguage), Icons.Default.ReceiptLong),
                    Triple(2, LanguageStrings.get("tab_stock", currentLanguage), Icons.Default.Inventory2),
                    Triple(3, LanguageStrings.get("tab_cashbook", currentLanguage), Icons.Default.Payments),
                    Triple(4, LanguageStrings.get("tab_profile", currentLanguage), Icons.Default.Storefront)
                )

                navItems.forEach { (index, label, icon) ->
                    val isSelected = currentTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = index },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                maxLines = 1,
                                color = if (isSelected) PrimaryBlue else TextSecondary
                            )
                        },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            indicatorColor = ChipActiveBg,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Persistent Error Banner when migration failed or writes are blocked (cannot be simply dismissed)
            if (isMigrationBlocked || migrationError != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = UdharRed.copy(alpha = 0.12f)),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = migrationError ?: LanguageStrings.get("corrupt_data_banner", currentLanguage),
                            color = UdharRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { repository.retryMigration() },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(LanguageStrings.get("btn_retry", currentLanguage), fontSize = 11.sp, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    exportDamagedLauncher.launch("ghanshyam_damaged_data_${System.currentTimeMillis()}.json")
                                },
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Text(LanguageStrings.get("btn_export_damaged", currentLanguage), fontSize = 11.sp, maxLines = 1)
                            }
                            Button(
                                onClick = {
                                    startFreshInputText = ""
                                    showStartFreshDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = UdharRed),
                                modifier = Modifier.weight(1.1f)
                            ) {
                                Text(LanguageStrings.get("btn_start_fresh", currentLanguage), fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Informational Notice Banner (e.g. orphan transactions recovered)
            if (migrationNotice != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(alpha = 0.12f)),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = migrationNotice ?: "",
                            color = PrimaryBlue,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { repository.dismissMigrationNotice() }) {
                            Text(LanguageStrings.get("btn_dismiss", currentLanguage), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (currentTab) {
                    0 -> KhataScreen(
                        repository = repository,
                        onCustomerClick = { selectedCustomerId = it.id }
                    )
                    1 -> BillingScreen(repository = repository)
                    2 -> StockScreen(repository = repository)
                    3 -> CashbookScreen(repository = repository)
                    4 -> ShopProfileScreen(
                        repository = repository,
                        onOpenGuide = { showGuideModal = true }
                    )
                }
            }
        }
    }

    if (showAiModal) {
        AiAssistantModal(
            repository = repository,
            initialReportContent = initialDailyReportContent,
            onDismiss = { showAiModal = false }
        )
    }

    if (showStartFreshDialog) {
        AlertDialog(
            onDismissRequest = {
                showStartFreshDialog = false
                startFreshInputText = ""
            },
            title = {
                Text(
                    text = LanguageStrings.get("start_fresh_dialog_title", currentLanguage),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = LanguageStrings.get("start_fresh_dialog_desc", currentLanguage),
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = startFreshInputText,
                        onValueChange = { startFreshInputText = it },
                        label = { Text(LanguageStrings.get("type_delete_hint", currentLanguage)) },
                        placeholder = { Text("DELETE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.startFreshFromDamagedData()
                        showStartFreshDialog = false
                        startFreshInputText = ""
                    },
                    enabled = startFreshInputText.trim() == "DELETE",
                    colors = ButtonDefaults.buttonColors(containerColor = UdharRed)
                ) {
                    Text(LanguageStrings.get("btn_start_fresh", currentLanguage))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showStartFreshDialog = false
                        startFreshInputText = ""
                    }
                ) {
                    Text(LanguageStrings.get("btn_cancel", currentLanguage))
                }
            }
        )
    }
}
