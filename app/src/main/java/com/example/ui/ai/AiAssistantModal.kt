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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.*

data class ChatMessage(
    val sender: String, // "USER" or "AI"
    val text: String,
    val actionTitle: String? = null,
    val reportContent: String? = null,
    val onExecute: (() -> Unit)? = null,
    val isOutOfScope: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantModal(
    repository: AppRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val aiEngine = remember { GhanshyamAiEngine(repository) }

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

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                sender = "AI",
                text = "Namaste! Main aapka Voice AI Assistant hoon. Hindi, Hinglish ya English me boliye — khata, bill, stock ya report sab handle karunga."
            )
        )
    }

    fun sendMessage(query: String) {
        if (query.isBlank()) return
        messages.add(ChatMessage(sender = "USER", text = query))
        isProcessing = true

        coroutineScope.launch {
            val response = aiEngine.processQuery(query)
            isProcessing = false
            when (response) {
                is AiResponse.Success -> {
                    messages.add(
                        ChatMessage(
                            sender = "AI",
                            text = response.replyText,
                            actionTitle = response.actionTitle,
                            reportContent = response.reportContent,
                            onExecute = response.executeAction
                        )
                    )
                    speak(response.replyText)
                }
                is AiResponse.OutOfScope -> {
                    messages.add(
                        ChatMessage(
                            sender = "AI",
                            text = response.replyText,
                            isOutOfScope = true
                        )
                    )
                    speak(response.replyText)
                }
            }
        }
    }

    // Voice recognition launcher
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("hi-IN", "en-IN"))
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Boliye (Hindi / Hinglish / English)...")
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            messages.add(ChatMessage(sender = "AI", text = "Mic open nahi ho saka. Kripya type karein."))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxHeight(0.88f)
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Ghanshyam Voice AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Speaks Hindi, Hinglish & English", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            // Quick suggestion chips
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val suggestions = listOf(
                    "📊 Show today sales report",
                    "❌ Rajesh ka 1850 udhar cancel karo",
                    "☕ Chai samosa kharcha 60",
                    "➕ Ramesh ko 500 udhar likho",
                    "🧾 Generate bill for 950"
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

            // Chat Messages List
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { msg ->
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
                                containerColor = if (isAi) {
                                    if (msg.isOutOfScope) ChipInactiveBg else ChipActiveBg
                                } else PrimaryBlue
                            ),
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier.widthIn(max = 320.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isAi) TextPrimary else SurfaceWhite
                                )

                                // Action card execution button
                                if (msg.onExecute != null && msg.actionTitle != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    var executed by remember { mutableStateOf(false) }
                                    Button(
                                        onClick = {
                                            msg.onExecute.invoke()
                                            executed = true
                                        },
                                        enabled = !executed,
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(if (executed) Icons.Default.Check else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (executed) "Done!" else "Apply: ${msg.actionTitle}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Report content box with copy and share buttons
                                if (!msg.reportContent.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                                        shape = RoundedCornerShape(8.dp),
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
                                                    modifier = Modifier.weight(1f)
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
                                                        context.startActivity(Intent.createChooser(intent, "Share Report"))
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = JamaGreen),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Share", fontSize = 11.sp)
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = PrimaryBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI soch raha hai...", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }

            // Input Bar with Voice Mic & Send Buttons
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 8.dp),
                color = SurfaceWhite
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { startListening() },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ChipActiveBg)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = PrimaryBlue, modifier = Modifier.size(24.dp))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Boliye ya type kijiye...", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val t = inputText
                                inputText = ""
                                sendMessage(t)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = SurfaceWhite, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
