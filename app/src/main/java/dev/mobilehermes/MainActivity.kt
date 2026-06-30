package dev.mobilehermes

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
                }
            )
        }
    }
}

@Composable
private fun MobileHermesApp(
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onOpenTermux: () -> Unit
) {
    val bridgeClient = remember { BridgeClient() }
    val scope = rememberCoroutineScope()
    var bridgeStatus by remember { mutableStateOf("Checking bridge...") }
    var adbStatus by remember { mutableStateOf("Unknown") }
    var chatText by remember { mutableStateOf("") }
    var lastResponse by remember { mutableStateOf("No command sent yet.") }

    fun refresh() {
        scope.launch {
            bridgeStatus = bridgeClient.health().getOrElse { "Bridge offline: ${it.message}" }
            adbStatus = bridgeClient.adbDevices().getOrElse { "ADB unavailable: ${it.message}" }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(HermesGradient),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Mobile Hermes",
                    color = HermesGold,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Local-first agent control for Termux, Chrome, WhatsApp, and YouTube.",
                    color = Color(0xFFC7C9D6)
                )

                StatusCard(title = "Hermes Bridge", body = bridgeStatus)
                StatusCard(title = "Wireless ADB", body = adbStatus)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HermesButton("Start") {
                        onStartService()
                        refresh()
                    }
                    HermesButton("Stop") {
                        onStopService()
                        refresh()
                    }
                    HermesButton("Termux") {
                        onOpenTermux()
                    }
                    HermesButton("Refresh") {
                        refresh()
                    }
                }

                OutlinedTextField(
                    value = chatText,
                    onValueChange = { chatText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Command for Hermes bridge") },
                    placeholder = { Text("Open Chrome and search for Nous Hermes Agent") },
                    minLines = 3
                )

                HermesButton("Send to local bridge") {
                    val command = chatText.trim()
                    if (command.isEmpty()) {
                        lastResponse = "Type a command first."
                        return@HermesButton
                    }
                    scope.launch {
                        lastResponse = bridgeClient.command(command).getOrElse {
                            "Command failed: ${it.message}"
                        }
                    }
                }

                StatusCard(title = "Last Response", body = lastResponse)

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Risky actions must ask first: sending messages, deleting data, purchases, public posts, settings changes, and sharing personal files.",
                    color = Color(0xFF9EA3B8),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xDD11131D))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = HermesGold, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(body, color = Color(0xFFE4E6F1), fontSize = 14.sp)
        }
    }
}

@Composable
private fun HermesButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = HermesGold)
    ) {
        Text(label, color = Color(0xFF101114), fontWeight = FontWeight.Bold)
    }
}

private val HermesGold = Color(0xFFD6B25E)
private val HermesGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF090A0F), Color(0xFF151827), Color(0xFF090A0F))
)

