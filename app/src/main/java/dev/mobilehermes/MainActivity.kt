package dev.mobilehermes

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MobileHermesApp(
                onStartService = {
                    startForegroundService(Intent(this, HermesForegroundService::class.java))
                },
                onStopService = {
                    stopService(Intent(this, HermesForegroundService::class.java))
                },
                onOpenTermux = {
                    TermuxCommand.openTermux(this)
                },
                onRunTermuxScript = { script, argument ->
                    TermuxCommand.runMobileHermesScript(this, script, argument)
                }
            )
        }
    }
}

private data class ChatLine(val author: String, val body: String)

@Composable
private fun MobileHermesApp(
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onOpenTermux: () -> Unit,
    onRunTermuxScript: (String, String?) -> Unit
) {
    val bridgeClient = remember { BridgeClient() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember {
        mutableStateListOf(
            ChatLine("Hermes", "Local chat is ready once the Termux bridge is running. No Telegram token needed.")
        )
    }

    var bridgeStatus by remember { mutableStateOf("Checking bridge...") }
    var adbStatus by remember { mutableStateOf("ADB skipped for now") }
    var providerStatus by remember { mutableStateOf("Provider config not checked yet") }
    var chatText by remember { mutableStateOf("") }
    var backendMode by remember { mutableStateOf("pure-termux") }
    var setupExpanded by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            bridgeStatus = bridgeClient.health().getOrElse { "Bridge offline: ${it.message}" }
            providerStatus = bridgeClient.providers().getOrElse { "Provider check unavailable: ${it.message}" }
            adbStatus = bridgeClient.adbDevices().getOrElse { "ADB skipped/unavailable: ${it.message}" }
        }
    }

    fun sendChat() {
        val prompt = chatText.trim()
        if (prompt.isEmpty()) return
        chatText = ""
        messages.add(ChatLine("You", prompt))
        busy = true
        scope.launch {
            val response = bridgeClient.chat(prompt).getOrElse { "Bridge chat failed: ${it.message}" }
            messages.add(ChatLine("Hermes", response))
            busy = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HermesBackground)
        ) {
            AmbientGlow()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Header(bridgeStatus)

                RuntimeControls(
                    onStartService = {
                        onStartService()
                        refresh()
                    },
                    onStopService = {
                        onStopService()
                        refresh()
                    },
                    onRefresh = ::refresh,
                    onOpenTermux = onOpenTermux
                )

                SetupWizard(
                    expanded = setupExpanded,
                    onToggle = { setupExpanded = !setupExpanded },
                    backendMode = backendMode,
                    onBackendMode = { backendMode = it },
                    onRunSetup = {
                        onRunTermuxScript("mobile-hermes-bootstrap.sh", backendMode)
                    },
                    onCopySetup = {
                        copyToClipboard(
                            context,
                            "Mobile Hermes setup",
                            "cd ~/Mobile_Hermes/termux && sh mobile-hermes-bootstrap.sh $backendMode"
                        )
                    }
                )

                StatusGrid(
                    bridgeStatus = bridgeStatus,
                    adbStatus = adbStatus,
                    providerStatus = providerStatus
                )

                ChatPanel(
                    messages = messages,
                    value = chatText,
                    busy = busy,
                    onValueChange = { chatText = it },
                    onSend = ::sendChat
                )
            }
        }
    }
}

@Composable
private fun Header(bridgeStatus: String) {
    val online = bridgeStatus.contains("\"ok\": true") || bridgeStatus.contains("mobile-hermes-bridge")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.9f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulseScale"
    )
    val statusColor by animateColorAsState(
        targetValue = if (online) HermesGreen else HermesRed,
        animationSpec = tween(500),
        label = "statusColor"
    )

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Mobile Hermes", color = HermesGold, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Phone-local agent runtime, chat, setup, and automation bridge.",
                    color = HermesTextMuted,
                    fontSize = 14.sp
                )
            }
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .scale(pulse)
                    .border(1.dp, statusColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("H", color = statusColor, fontSize = 24.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun RuntimeControls(
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onRefresh: () -> Unit,
    onOpenTermux: () -> Unit
) {
    GlassCard {
        Text("Runtime", color = HermesGold, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HermesButton("Start app service", Modifier.weight(1f), onStartService)
                HermesButton("Refresh", Modifier.weight(1f), onRefresh)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Stop service", Modifier.weight(1f), onStopService)
                SecondaryButton("Open Termux", Modifier.weight(1f), onOpenTermux)
            }
        }
    }
}

@Composable
private fun SetupWizard(
    expanded: Boolean,
    onToggle: () -> Unit,
    backendMode: String,
    onBackendMode: (String) -> Unit,
    onRunSetup: () -> Unit,
    onCopySetup: () -> Unit
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Backend setup", color = HermesGold, fontWeight = FontWeight.SemiBold)
                Text("Install Hermes in Termux, load local keys, then start the bridge.", color = HermesTextMuted, fontSize = 13.sp)
            }
            SecondaryButton(if (expanded) "Hide" else "Show", Modifier, onToggle)
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 14.dp)) {
                StepPill("1", "Keep config.local.json in ~/Mobile_Hermes/termux or Download/mobile-hermes-config.json")
                StepPill("2", "Choose backend. Pure Termux is lighter; Ubuntu/proot is heavier but more Linux-like.")
                StepPill("3", "Run setup. If Termux blocks direct launch, copy the command and run it in Termux.")

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectableMode("Pure Termux", backendMode == "pure-termux", Modifier.weight(1f)) {
                        onBackendMode("pure-termux")
                    }
                    SelectableMode("Ubuntu", backendMode == "ubuntu", Modifier.weight(1f)) {
                        onBackendMode("ubuntu")
                    }
                }

                HermesButton("Run setup in Termux", Modifier.fillMaxWidth(), onRunSetup)
                SecondaryButton("Copy setup command", Modifier.fillMaxWidth(), onCopySetup)
            }
        }
    }
}

@Composable
private fun StatusGrid(bridgeStatus: String, adbStatus: String, providerStatus: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusCard("Bridge", bridgeStatus)
        StatusCard("Providers", providerStatus)
        StatusCard("ADB", adbStatus)
    }
}

@Composable
private fun ChatPanel(
    messages: List<ChatLine>,
    value: String,
    busy: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    GlassCard {
        Text("Local chat", color = HermesGold, fontWeight = FontWeight.SemiBold)
        Text("Talk through the app. Telegram is optional now.", color = HermesTextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            messages.takeLast(6).forEach { message ->
                ChatBubble(message)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ask Hermes") },
            placeholder = { Text("Find something online, summarize it, or open Chrome") },
            minLines = 2
        )
        Spacer(modifier = Modifier.height(10.dp))
        HermesButton(if (busy) "Thinking..." else "Send", Modifier.fillMaxWidth(), onSend)
    }
}

@Composable
private fun ChatBubble(message: ChatLine) {
    val isUser = message.author == "You"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val color = if (isUser) Color(0xFF2A2E3F) else Color(0xFF171A25)
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = color),
            modifier = Modifier.fillMaxWidth(if (isUser) 0.88f else 1f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(message.author, color = HermesGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(message.body, color = HermesText, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, body: String) {
    GlassCard {
        Text(title, color = HermesGold, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(body, color = HermesText, fontSize = 13.sp)
    }
}

@Composable
private fun StepPill(number: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(HermesGold.copy(alpha = 0.18f), CircleShape)
                .border(1.dp, HermesGold.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = HermesGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Text(label, color = HermesText, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SelectableMode(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val alpha by animateFloatAsState(if (selected) 1f else 0.72f, tween(250), label = "modeAlpha")
    val borderColor = if (selected) HermesGold else Color(0xFF34384B)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.alpha(alpha),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = HermesText)
    ) {
        Text(label, color = borderColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xD911131D))
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun HermesButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = HermesGold)
    ) {
        Text(label, color = Color(0xFF101114), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SecondaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = HermesText)
    ) {
        Text(label, color = HermesText, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AmbientGlow() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(HermesGold.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(120f, 80f),
                    radius = 520f
                )
            )
    )
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "Copied setup command", Toast.LENGTH_SHORT).show()
}

private val HermesGold = Color(0xFFD6B25E)
private val HermesText = Color(0xFFE9E6D7)
private val HermesTextMuted = Color(0xFFA7A596)
private val HermesGreen = Color(0xFF7DDE92)
private val HermesRed = Color(0xFFE77777)
private val HermesBackground = Brush.linearGradient(
    colors = listOf(Color(0xFF08090D), Color(0xFF11131D), Color(0xFF08090D))
)
