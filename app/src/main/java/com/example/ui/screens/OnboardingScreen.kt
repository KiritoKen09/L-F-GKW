package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

data class OnboardingStep(
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val tip: String
)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    val steps = remember {
        listOf(
            OnboardingStep(
                title = "Welcome to Ghanshyam Billing",
                subtitle = "Aapki Dukaan Ka Digital Assistant",
                description = "Manage your entire shop easily: Khata, GST Billing, Stock Inventory, and Cashbook in one clean app.",
                icon = Icons.Default.Storefront,
                tip = "Everything starts fresh and completely empty for your business."
            ),
            OnboardingStep(
                title = "Smart Khata & Udhar Ledger",
                subtitle = "Customer Udhar & Jama Track Karein",
                description = "Add customers from contacts or WhatsApp. View detailed history. If an udhar is settled or cancelled, use the manual tick box to cancel out immediately!",
                icon = Icons.Default.AccountBalanceWallet,
                tip = "Tap the tick box on any transaction to strike it through and update balance."
            ),
            OnboardingStep(
                title = "GST Billing & Invoice Themes",
                subtitle = "Professional Invoices with Custom Styles",
                description = "Fast billing counter with party details, GST breakdown, item rows, delivery charges, and custom print themes (Premium Blue, Royal Gold).",
                icon = Icons.Default.ReceiptLong,
                tip = "Share digital bills directly with customers via WhatsApp."
            ),
            OnboardingStep(
                title = "Voice AI Assistant",
                subtitle = "Boliye Hinglish ya Hindi Me",
                description = "Tap the AI button next to Language at the top! Speak commands like 'Ramesh ko 500 udhar likho', 'Show today sales report', or 'Generate bill'. AI operates the app for you.",
                icon = Icons.Default.Mic,
                tip = "AI can generate reports, update stock, and operate your entire shop."
            )
        )
    }

    var currentStepIndex by remember { mutableStateOf(0) }
    val step = steps[currentStepIndex]

    Scaffold(
        containerColor = SoftBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (currentStepIndex < steps.size - 1) {
                    TextButton(onClick = onFinish) {
                        Text("Skip Guide", color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStepIndex > 0) {
                        OutlinedButton(
                            onClick = { currentStepIndex-- },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Back")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        steps.forEachIndexed { idx, _ ->
                            Box(
                                modifier = Modifier
                                    .size(if (idx == currentStepIndex) 20.dp else 8.dp, 8.dp)
                                    .clip(CircleShape)
                                    .background(if (idx == currentStepIndex) PrimaryBlue else CardBorder)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (currentStepIndex < steps.size - 1) {
                                currentStepIndex++
                            } else {
                                onFinish()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            if (currentStepIndex == steps.size - 1) "Get Started" else "Next",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = ChipActiveBg),
                modifier = Modifier.size(110.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = step.icon,
                        contentDescription = step.title,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(54.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = step.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = step.subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = PrimaryBlue,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = step.description,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = CardDefaults.outlinedCardBorder(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = step.tip,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
