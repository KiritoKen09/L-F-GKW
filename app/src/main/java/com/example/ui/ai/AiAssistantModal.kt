package com.example.ui.ai

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.example.model.Customer
import com.example.model.Invoice
import com.example.model.InvoiceItem
import com.example.ui.theme.*
import com.example.ui.util.LanguageStrings
import com.example.ui.util.PhoneUtil
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantModal(
    repository: AppRepository,
    initialReportContent: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val aiEngine = remember { GhanshyamAiEngine(repository, context) }
    val currentLang by repository.language.collectAsState()
    val persistedMessages by repository.chatMessages.collectAsState()

    // Local runtime actions map for ephemeral callbacks
    val runtimeActions = remember { mutableStateMapOf<String, () -> Unit>() }

    // TTS initialization
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val speechEngine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
            }
        }
        tts = speechEngine
        onDispose {
            speechEngine.stop()
            speechEngine.shutdown()
        }
    }

    fun speak(text: String) {
        if (isTtsReady && tts != null) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "UTTERANCE_${System.currentTimeMillis()}")
        }
    }

    var inputText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Handle initial report if modal was opened from notification
    LaunchedEffect(initialReportContent) {
        if (!initialReportContent.isNullOrBlank()) {
            val exists = persistedMessages.any { it.reportContent == initialReportContent }
            if (!exists) {
                repository.saveChatMessage(
                    sender = "AI",
                    text = "Aaj ka Daily Business Hisaab Report niche taiyyar hai:",
                    reportContent = initialReportContent
                )
            }
        }
    }

    // Scroll to bottom on new messages
    LaunchedEffect(persistedMessages.size) {
        if (persistedMessages.isNotEmpty()) {
            listState.animateScrollToItem(persistedMessages.size - 1)
        }
    }

    fun sendMessage(query: String) {
        if (query.isBlank()) return
        inputText = ""
        isProcessing = true

        coroutineScope.launch {
            val response = aiEngine.processQuery(query)
            isProcessing = false
            when (response) {
                is AiResponse.Success -> {
                    if (response.executeAction != null) {
                        // Store callback in runtime map keyed by actionTitle
                        val key = response.actionTitle ?: UUID.randomUUID().toString()
                        runtimeActions[key] = response.executeAction
                    }
                    speak(response.replyText)
                }
                is AiResponse.OutOfScope -> {
                    speak(response.replyText)
                }
            }
        }
    }

    // Dynamic voice recognition locale based on app language
    val voiceLocale = when (currentLang) {
        AppLanguage.GUJARATI -> "gu-IN"
        AppLanguage.HINDI -> "hi-IN"
        AppLanguage.HINGLISH -> "hi-IN"
        AppLanguage.ENGLISH -> "en-IN"
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                inputText = spokenText
                sendMessage(spokenText)
            }
        }
    }

    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceLocale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, voiceLocale)
            putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf(voiceLocale, "hi-IN", "en-IN"))
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Boliye (${currentLang.displayName})...")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            android.util.Log.w("AiAssistantModal", "Speech recognition unavailable: ${e.message}", e)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Ghanshyam Voice AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Gemini Function Calling • Voice in ${currentLang.displayName}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (persistedMessages.isNotEmpty()) {
                        IconButton(onClick = { coroutineScope.launch { repository.clearChatMessages() } }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Clear Chat", tint = TextTertiary, modifier = Modifier.size(20.dp))
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }
            }

            // Quick suggestion chips
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val suggestions = listOf(
                    "📊 Show today sales report",
                    "👥 Who owes me more than 5000?",
                    "💵 Total cash sales",
                    "📦 Check low stock items",
                    "➕ Ramesh Patel ko customer add karo",
                    "☕ Chai samosa kharcha 60",
                    "💵 Cash in hand kitna hai?"
                )
                items(suggestions) { s ->
                    Surface(
                        color = ChipInactiveBg,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable { sendMessage(s) }
                    ) {
                        Text(
                            text = s,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                    }
                }
            }

            // Chat Messages List (Persisted from Room Database)
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (persistedMessages.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = ChipActiveBg),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Namaste! Main Ghanshyam Voice AI hoon. Gemini Function Calling ke sath dukaan ka hisaab-kitaab sambhalne ke liye taiyyar.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "💡 Type karein ya Mic dabakar boliye — 'Who owes me?', 'Today sales report', ya 'Ramesh ko 500 udhar likho'.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PrimaryBlue
                                )
                            }
                        }
                    }
                }

                items(persistedMessages) { msg ->
                    val isAi = msg.sender == "AI"
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (isAi) Alignment.Start else Alignment.End
                    ) {
                        Card(
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isAi) 4.dp else 16.dp,
                                bottomEnd = if (isAi) 16.dp else 4.dp
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isAi) ChipActiveBg else PrimaryBlue
                            ),
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier.widthIn(max = 330.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isAi) TextPrimary else SurfaceWhite
                                )

                                // Proposed action confirmation card
                                if (!msg.actionTitle.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                                        shape = RoundedCornerShape(8.dp),
                                        border = CardDefaults.outlinedCardBorder(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.PendingActions, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Proposed Action", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(msg.actionTitle, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                            Spacer(modifier = Modifier.height(8.dp))

                                            Button(
                                                onClick = {
                                                    // Execute the proposal
                                                    coroutineScope.launch {
                                                        executeActionData(msg.actionType, msg.actionData, repository, runtimeActions[msg.actionTitle])
                                                        repository.markChatActionApplied(msg.id)
                                                    }
                                                },
                                                enabled = !msg.isApplied,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (msg.isApplied) JamaGreen else PrimaryBlue,
                                                    disabledContainerColor = JamaGreen.copy(alpha = 0.8f),
                                                    disabledContentColor = SurfaceWhite
                                                ),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(if (msg.isApplied) Icons.Default.Check else Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(if (msg.isApplied) "Applied ✓" else "Apply Action", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // Report content box with copy and WhatsApp share buttons
                                if (!msg.reportContent.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                                        shape = RoundedCornerShape(8.dp),
                                        border = CardDefaults.outlinedCardBorder(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = msg.reportContent,
                                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                                color = TextPrimary
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        clipboard.setPrimaryClip(ClipData.newPlainText("Store Report", msg.reportContent))
                                                    },
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Copy", fontSize = 11.sp)
                                                }

                                                Button(
                                                    onClick = {
                                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                                            type = "text/plain"
                                                            putExtra(Intent.EXTRA_TEXT, msg.reportContent)
                                                        }
                                                        context.startActivity(Intent.createChooser(intent, "Share Report via WhatsApp"))
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = JamaGreen),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.weight(1.3f),
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Share WhatsApp", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isProcessing) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = PrimaryBlue)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Ghanshyam AI thinking...", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }

            // Input Bar
            Surface(
                color = SurfaceWhite,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Boliye ya type kijiye...", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // Voice Mic Button
                    IconButton(
                        onClick = { startListening() },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(ChipActiveBg)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Send Button
                    IconButton(
                        onClick = { sendMessage(inputText) },
                        enabled = inputText.isNotBlank() && !isProcessing,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (inputText.isNotBlank() && !isProcessing) PrimaryBlue else ChipInactiveBg)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = SurfaceWhite, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

private suspend fun executeActionData(
    actionType: String?,
    actionData: String?,
    repository: AppRepository,
    runtimeLambda: (() -> Unit)?
) {
    if (runtimeLambda != null) {
        runtimeLambda.invoke()
        return
    }

    if (actionData.isNullOrBlank()) return

    try {
        val json = JSONObject(actionData)
        when (actionType) {
            "ADD_PARTY" -> {
                val name = json.optString("name", "").trim()
                val phone = json.optString("phone", "").trim()
                val type = json.optString("type", "CUSTOMER").uppercase()
                val opening = json.optDouble("openingBalance", 0.0)
                if (name.isNotBlank()) {
                    val p = Customer(name = name, phone = PhoneUtil.normalize(phone), type = type)
                    repository.addCustomer(p)
                    if (Math.abs(opening) > 0.009) {
                        repository.createTransaction(
                            customerId = p.id,
                            amount = Math.abs(opening),
                            type = if (opening > 0) "GAVE" else "GOT",
                            note = "Opening Balance"
                        )
                    }
                }
            }
            "ADD_TRANSACTION" -> {
                val partyName = json.optString("partyName", "").trim()
                val type = json.optString("type", "GAVE").uppercase()
                val amount = json.optDouble("amount", 0.0)
                val notes = json.optString("notes", "Via Ghanshyam AI")
                if (amount > 0) {
                    val cust = repository.customers.value.find { it.name.contains(partyName, true) }
                    val custId = cust?.id ?: run {
                        val newC = Customer(name = partyName, phone = "", type = "CUSTOMER")
                        repository.addCustomer(newC)
                        newC.id
                    }
                    repository.createTransaction(custId, amount, type, notes)
                }
            }
            "MARK_RECEIVED" -> {
                val partyName = json.optString("partyName", "").trim()
                val amount = json.optDouble("amount", 0.0)
                val mode = json.optString("paymentMode", "CASH")
                if (amount > 0) {
                    val cust = repository.customers.value.find { it.name.contains(partyName, true) }
                    val custId = cust?.id ?: run {
                        val newC = Customer(name = partyName, phone = "", type = "CUSTOMER")
                        repository.addCustomer(newC)
                        newC.id
                    }
                    repository.createTransaction(custId, amount, "GOT", "Payment Received", mode)
                }
            }
            "CREATE_BILL" -> {
                val custName = json.optString("customerName", "Customer")
                val custPhone = json.optString("customerPhone", "")
                val itemName = json.optString("itemName", "Item")
                val size = json.optString("size", "Free")
                val qty = json.optInt("quantity", 1)
                val price = json.optDouble("price", 0.0)
                val gst = json.optDouble("gstPercent", 5.0)
                val payMode = json.optString("paymentMode", "CASH").uppercase()

                val matched = repository.inventory.value.find { it.name.contains(itemName, true) }
                val item = InvoiceItem(
                    itemId = matched?.id,
                    name = itemName,
                    size = size,
                    hsnCode = matched?.hsnCode ?: "",
                    quantity = qty,
                    unitPrice = price,
                    gstPercent = gst
                )
                repository.createInvoice(Invoice(customerName = custName, customerPhone = custPhone, items = listOf(item), paymentMode = payMode))
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("AiAssistantModal", "Failed to deserialize action data: ${e.message}", e)
    }
}
