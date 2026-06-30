package dev.mobilehermes

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.delay
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
                }
            )
        }
    }
}

private data class ChatLine(val author: String, val body: String)

private data class SetupState(
    val termuxInstalled: Boolean,
    val bridgeStatus: String,
    val setupStatus: String,
    val providerStatus: String,
    val adbStatus: String
) {
    val bridgeOnline: Boolean = bridgeStatus.contains("\"ok\": true") ||
        bridgeStatus.contains("mobile-hermes-bridge")
    val providersLoaded: Boolean = providerStatus.contains("\"providers\"") &&
        !providerStatus.contains("\"providers\": {}")
}

@Composable
private fun MobileHermesApp(
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val bridgeClient = remember { BridgeClient() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember {
        mutableStateListOf(
            ChatLine("Hermes", "Local chat is first-class here. Start the Termux bridge and I can route through the phone-local backend without Telegram.")
        )
    }

    var selectedMode by remember { mutableStateOf("pure-termux") }
    var chatText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var setupExpanded by remember { mutableStateOf(true) }
    var cleanupConfirm by remember { mutableStateOf(false) }
    var setupState by remember {
        mutableStateOf(
            SetupState(
                termuxInstalled = TermuxCommand.isTermuxInstalled(context),
                bridgeStatus = "Checking bridge...",
                setupStatus = "Setup status becomes detailed after the bridge starts.",
                providerStatus = "Provider config not checked yet.",
                adbStatus = "ADB optional for chat; pair later for phone automation."
            )
        )
    }

    fun refresh() {
        scope.launch {
            val bridge = bridgeClient.health().getOrElse { "Bridge offline: ${it.message}" }
            val setup = bridgeClient.setupStatus().getOrElse {
                "Bridge offline. Termux installed: ${if (TermuxCommand.isTermuxInstalled(context)) "yes" else "no"}. External command permission is unknown until Termux accepts RUN_COMMAND."
            }
            val providers = bridgeClient.providers().getOrElse { "Provider check unavailable: ${it.message}" }
            val adb = bridgeClient.adbDevices().getOrElse { "ADB skipped/unavailable: ${it.message}" }
            setupState = SetupState(
                termuxInstalled = TermuxCommand.isTermuxInstalled(context),
                bridgeStatus = bridge,
                setupStatus = setup,
                providerStatus = providers,
                adbStatus = adb
            )
        }
    }

    fun sendChat() {
        val prompt = chatText.trim()
        if (prompt.isEmpty() || busy) return
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
        while (true) {
            delay(7000)
            refresh()
        }
    }

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HermesBackground)
        ) {
            AmbientField()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ConsoleHeader(setupState.bridgeOnline, setupState.providersLoaded)

                SetupConsole(
                    expanded = setupExpanded,
                    state = setupState,
                    selectedMode = selectedMode,
                    onToggle = { setupExpanded = !setupExpanded },
                    onMode = { selectedMode = it },
                    onRunSetup = { TermuxCommand.runSetup(context, selectedMode) },
                    onCopySetup = { TermuxCommand.copySetupCommand(context, selectedMode) },
                    onCopyPermission = { TermuxCommand.copyExternalAppsFix(context) },
                    onOpenTermux = { TermuxCommand.openTermux(context) }
                )

                RuntimeConsole(
                    state = setupState,
                    onStartService = {
                        onStartService()
                        refresh()
                    },
                    onStopService = {
                        onStopService()
                        refresh()
                    },
                    onRefresh = ::refresh,
                    onStartBridge = { TermuxCommand.runScript(context, "mobile-hermes-start.sh") },
                    onStopBridge = { TermuxCommand.runScript(context, "mobile-hermes-stop.sh") }
                )

                ChatConsole(
                    messages = messages,
                    value = chatText,
                    busy = busy,
                    bridgeOnline = setupState.bridgeOnline,
                    onValueChange = { chatText = it },
                    onSend = ::sendChat
                )

                LogsAndCleanup(
                    setupStatus = setupState.setupStatus,
                    providerStatus = setupState.providerStatus,
                    adbStatus = setupState.adbStatus,
                    onCleanup = { cleanupConfirm = true },
                    onCopyCleanup = { TermuxCommand.copyCleanupCommand(context) }
                )
            }

            if (cleanupConfirm) {
                CleanupDialog(
                    onDismiss = { cleanupConfirm = false },
                    onConfirm = {
                        cleanupConfirm = false
                        TermuxCommand.runScript(context, "mobile-hermes-cleanup.sh")
                    }
                )
            }
        }
    }
}

@Composable
private fun ConsoleHeader(bridgeOnline: Boolean, providersLoaded: Boolean) {
    val pulse by rememberInfiniteTransition(label = "headerPulse").animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "headerPulseScale"
    )
    val statusColor by animateColorAsState(
        targetValue = when {
            bridgeOnline && providersLoaded -> HermesGreen
            bridgeOnline -> HermesAmber
            else -> HermesRed
        },
        animationSpec = tween(450),
        label = "headerStatusColor"
    )

    ConsolePanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Hermes Agent Console", color = HermesGold, fontSize = 29.sp, fontWeight = FontWeight.Black)
                Text("A phone-local Nous-style control room for setup, chat, and safe automation.", color = HermesTextMuted, fontSize = 13.sp)
            }
            SignalOrb(color = statusColor, scale = pulse)
        }
    }
}

@Composable
private fun SetupConsole(
    expanded: Boolean,
    state: SetupState,
    selectedMode: String,
    onToggle: () -> Unit,
    onMode: (String) -> Unit,
    onRunSetup: () -> Unit,
    onCopySetup: () -> Unit,
    onCopyPermission: () -> Unit,
    onOpenTermux: () -> Unit
) {
    ConsolePanel {
        SectionTitle("First-run backend", "Pure Termux is the default. Ubuntu/proot stays optional and cleanup-safe.")
        Spacer(modifier = Modifier.height(10.dp))
        ProgressRail(
            steps = listOf(
                "Termux installed" to state.termuxInstalled,
                "External commands enabled" to state.setupStatus.contains("\"external_commands\": true"),
                "Config present" to state.setupStatus.contains("\"config_present\": true"),
                "Backend installed" to state.setupStatus.contains("\"hermes_cli\": true"),
                "Bridge running" to state.bridgeOnline,
                "Providers loaded" to state.providersLoaded
            )
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip("Pure Termux", selectedMode == "pure-termux", Modifier.weight(1f)) { onMode("pure-termux") }
            ModeChip("Ubuntu/proot", selectedMode == "ubuntu", Modifier.weight(1f)) { onMode("ubuntu") }
        }
        Spacer(modifier = Modifier.height(10.dp))
        AnimatedVisibility(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StepLine("1", "Install Termux from F-Droid if the first checkpoint is dark.")
                StepLine("2", "Enable RUN_COMMAND once: allow-external-apps=true in ~/.termux/termux.properties, then restart Termux.")
                StepLine("3", "Put your ignored config at ~/Mobile_Hermes/termux/config.local.json or /sdcard/Download/mobile-hermes-config.json.")
                StepLine("4", "Run setup. The app clones/updates Mobile_Hermes in Termux, installs packages, installs Hermes Agent, copies config, and starts the bridge.")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Run setup", Modifier.weight(1f), onRunSetup)
            GhostButton(if (expanded) "Less" else "Steps", Modifier.width(96.dp), onToggle)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Copy permission fix", Modifier.weight(1f), onCopyPermission)
            GhostButton("Copy setup", Modifier.weight(1f), onCopySetup)
        }
        Spacer(modifier = Modifier.height(8.dp))
        GhostButton("Open Termux", Modifier.fillMaxWidth(), onOpenTermux)
    }
}

@Composable
private fun RuntimeConsole(
    state: SetupState,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onRefresh: () -> Unit,
    onStartBridge: () -> Unit,
    onStopBridge: () -> Unit
) {
    ConsolePanel {
        SectionTitle("Runtime", "Control the Android foreground service and the Termux bridge separately.")
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusTile("Bridge", if (state.bridgeOnline) "online" else "offline", state.bridgeOnline, Modifier.weight(1f))
            StatusTile("Providers", if (state.providersLoaded) "loaded" else "waiting", state.providersLoaded, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Start service", Modifier.weight(1f), onStartService)
            GhostButton("Refresh", Modifier.weight(1f), onRefresh)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Start bridge", Modifier.weight(1f), onStartBridge)
            GhostButton("Stop bridge", Modifier.weight(1f), onStopBridge)
        }
        Spacer(modifier = Modifier.height(8.dp))
        GhostButton("Stop app service", Modifier.fillMaxWidth(), onStopService)
    }
}

@Composable
private fun ChatConsole(
    messages: List<ChatLine>,
    value: String,
    busy: Boolean,
    bridgeOnline: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    ConsolePanel {
        SectionTitle("Local chat", if (bridgeOnline) "Ready through the Termux bridge." else "Start the bridge to chat locally.")
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            messages.takeLast(7).forEach { message ->
                ChatBubble(message)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ask Hermes", color = HermesTextMuted) },
            placeholder = { Text("Summarize, plan, or control a local workflow", color = HermesFaint) },
            minLines = 2
        )
        Spacer(modifier = Modifier.height(10.dp))
        PrimaryButton(if (busy) "Thinking..." else "Send local chat", Modifier.fillMaxWidth(), onSend)
    }
}

@Composable
private fun LogsAndCleanup(
    setupStatus: String,
    providerStatus: String,
    adbStatus: String,
    onCleanup: () -> Unit,
    onCopyCleanup: () -> Unit
) {
    ConsolePanel {
        SectionTitle("Status and cleanup", "Redacted status only. API keys stay inside Termux config.")
        Spacer(modifier = Modifier.height(10.dp))
        StatusText("Setup", setupStatus)
        StatusText("Providers", providerStatus)
        StatusText("ADB", adbStatus)
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DangerButton("Cleanup install", Modifier.weight(1f), onCleanup)
            GhostButton("Copy cleanup", Modifier.weight(1f), onCopyCleanup)
        }
    }
}

@Composable
private fun CleanupDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF11131D),
        title = { Text("Remove Mobile Hermes backend?", color = HermesGold) },
        text = {
            Text(
                "Cleanup uses ~/.mobile-hermes/install-manifest.json and only removes Mobile Hermes-owned runtime files, generated logs/env/rotation state, bridge pid, copied config, cloned repo artifacts, and Mobile Hermes-created proot Ubuntu when recorded. It will not remove Termux, unrelated packages, user home files, unrelated proot distros, or source API-key files.",
                color = HermesText
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Run cleanup", color = HermesRed) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = HermesTextMuted) }
        }
    )
}

@Composable
private fun ProgressRail(steps: List<Pair<String, Boolean>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEach { (label, done) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = if (done) HermesGreen else HermesFaint
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .background(color.copy(alpha = if (done) 0.92f else 0.25f), CircleShape)
                        .border(1.dp, color, CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(label, color = if (done) HermesText else HermesTextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StepLine(number: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(25.dp)
                .background(HermesGold.copy(alpha = 0.14f), CircleShape)
                .border(1.dp, HermesGold.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = HermesGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, color = HermesText, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatusTile(title: String, value: String, ok: Boolean, modifier: Modifier = Modifier) {
    val pulse by animateFloatAsState(if (ok) 1f else 0.74f, tween(300), label = "$title pulse")
    Column(
        modifier = modifier
            .border(1.dp, if (ok) HermesGreen.copy(alpha = 0.45f) else HermesFaint.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .background(Color(0x660D0F16), RoundedCornerShape(8.dp))
            .padding(10.dp)
            .alpha(pulse)
    ) {
        Text(title, color = HermesTextMuted, fontSize = 11.sp)
        Text(value, color = if (ok) HermesGreen else HermesText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun StatusText(label: String, value: String) {
    Text(label, color = HermesGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    Text(value, color = HermesTextMuted, fontSize = 12.sp, maxLines = 8)
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun ChatBubble(message: ChatLine) {
    val isUser = message.author == "You"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val fill = if (isUser) 0.88f else 1f
    val bubbleColor = if (isUser) Color(0xFF252A38) else Color(0xFF151821)
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            modifier = Modifier.fillMaxWidth(fill),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(message.author, color = HermesGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(message.body, color = HermesText, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Text(title, color = HermesGold, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    Text(subtitle, color = HermesTextMuted, fontSize = 12.sp)
}

@Composable
private fun ModeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = if (selected) HermesGold else HermesTextMuted
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = if (selected) 0.85f else 0.35f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color)
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun PrimaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = HermesGold)
    ) {
        Text(label, color = Color(0xFF11110F), fontWeight = FontWeight.Black, fontSize = 13.sp)
    }
}

@Composable
private fun GhostButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.13f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = HermesText)
    ) {
        Text(label, color = HermesText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun DangerButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, HermesRed.copy(alpha = 0.55f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = HermesRed)
    ) {
        Text(label, color = HermesRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun ConsolePanel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xE60E1018))
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun SignalOrb(color: Color, scale: Float) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .scale(scale)
            .background(color.copy(alpha = 0.08f), CircleShape)
            .border(1.dp, color.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("H", color = color, fontSize = 24.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun AmbientField() {
    val drift by rememberInfiniteTransition(label = "ambient").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000), RepeatMode.Reverse),
        label = "ambientDrift"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(HermesGold.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(80f + drift * 180f, 90f + drift * 120f),
                    radius = 560f
                )
            )
    )
}

private val HermesGold = Color(0xFFD9B76A)
private val HermesAmber = Color(0xFFFFC857)
private val HermesText = Color(0xFFEDE8D5)
private val HermesTextMuted = Color(0xFFA9A38E)
private val HermesFaint = Color(0xFF5E6475)
private val HermesGreen = Color(0xFF78E09A)
private val HermesRed = Color(0xFFFF6B6B)
private val HermesBackground = Brush.linearGradient(
    colors = listOf(Color(0xFF07080C), Color(0xFF11131B), Color(0xFF090A0F)),
    start = Offset.Zero,
    end = Offset(900f, 1500f)
)
