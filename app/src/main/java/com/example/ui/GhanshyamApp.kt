package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.example.model.AppLanguage
import com.example.model.Customer
import com.example.ui.ai.AiAssistantModal
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.util.LanguageStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GhanshyamApp(
    repository: AppRepository
) {
    val hasCompletedOnboarding by repository.hasCompletedOnboarding.collectAsState()
    val currentLanguage by repository.language.collectAsState()
    val storeProfile by repository.storeProfile.collectAsState()

    var showAiModal by remember { mutableStateOf(false) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf(0) } // 0: Khata, 1: Billing, 2: Stock, 3: Cashbook, 4: Shop
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var showGuideModal by remember { mutableStateOf(false) }

    // If onboarding guide is not completed yet, show OnboardingScreen!
    if (!hasCompletedOnboarding || showGuideModal) {
        OnboardingScreen(
            onFinish = {
                repository.completeOnboarding()
                showGuideModal = false
            }
        )
        return
    }

    // Detail Screen BackHandler
    if (selectedCustomer != null) {
        BackHandler { selectedCustomer = null }
        CustomerDetailScreen(
            customer = selectedCustomer!!,
            repository = repository,
            onBack = { selectedCustomer = null }
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
                                text = storeProfile.storeName,
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (currentTab) {
                0 -> KhataScreen(
                    repository = repository,
                    onCustomerClick = { selectedCustomer = it }
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

    if (showAiModal) {
        AiAssistantModal(
            repository = repository,
            onDismiss = { showAiModal = false }
        )
    }
}
