package com.gabriel.srtmonitor.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabriel.srtmonitor.model.SrtMode
import com.gabriel.srtmonitor.model.StreamConfig
import com.gabriel.srtmonitor.net.WifiMonitor
import com.gabriel.srtmonitor.ui.components.BackRow
import com.gabriel.srtmonitor.ui.components.FieldLabel
import com.gabriel.srtmonitor.ui.components.LabeledField
import com.gabriel.srtmonitor.ui.components.PrimaryButton
import com.gabriel.srtmonitor.ui.components.SecondaryButton
import com.gabriel.srtmonitor.ui.components.SegmentedControl
import com.gabriel.srtmonitor.ui.components.WifiRequiredBanner
import com.gabriel.srtmonitor.ui.theme.Palette

@Composable
fun SetupScreen(
    initial: StreamConfig,
    wifi: WifiMonitor.State,
    onBack: () -> Unit,
    onSave: (StreamConfig) -> Unit,
    onPlay: (StreamConfig) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var mode by rememberSaveable { mutableStateOf(initial.mode) }
    var host by rememberSaveable { mutableStateOf(initial.host) }
    var port by rememberSaveable { mutableStateOf(initial.port.toString()) }
    var latency by rememberSaveable { mutableStateOf(initial.latencyMs.toString()) }
    var passphrase by rememberSaveable { mutableStateOf(initial.passphrase) }
    var keyLength by rememberSaveable { mutableIntStateOf(initial.keyLength) }
    var streamId by rememberSaveable { mutableStateOf(initial.streamId) }
    var timeout by rememberSaveable { mutableStateOf(initial.connectTimeoutSec.toString()) }
    var routeViaWifi by rememberSaveable { mutableStateOf(initial.routeViaWifi) }
    var showPass by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var wifiWarning by remember { mutableStateOf(false) }
    var savedFlash by remember { mutableStateOf(false) }

    val listenerBlocked = mode == SrtMode.LISTENER && !wifi.connected

    fun build() = StreamConfig(
        name = name.trim().ifEmpty { "My Stream" },
        mode = mode,
        host = host.trim(),
        port = port.toIntOrNull() ?: -1,
        latencyMs = latency.toIntOrNull() ?: -1,
        passphrase = passphrase,
        keyLength = keyLength,
        streamId = streamId.trim(),
        connectTimeoutSec = timeout.toIntOrNull() ?: -1,
        routeViaWifi = routeViaWifi,
    )

    fun validated(): StreamConfig? {
        val c = build()
        errors = c.validate()
        if (errors.isNotEmpty()) {
            // jump to the tab holding the first problem
            tab = if (errors.keys.any { it in setOf("host", "port", "latency") }) 0 else 1
            return null
        }
        return c
    }

    LaunchedEffect(savedFlash) {
        if (savedFlash) {
            kotlinx.coroutines.delay(1500)
            savedFlash = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 560.dp)) {
            BackRow(onBack = onBack)
            Text("Quick Play", color = Palette.Text, fontSize = 34.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 4.dp))
            Text(
                "Enter the stream configuration to start watching.",
                color = Palette.Muted,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
            )

            SegmentedControl(
                options = listOf("Quick", "Advanced"),
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.fillMaxWidth(),
            )

            if (tab == 0) {
                FieldLabel("Mode", required = true)
                SegmentedControl(
                    options = listOf("Caller", "Listener"),
                    selected = if (mode == SrtMode.CALLER) 0 else 1,
                    onSelect = { i ->
                        if (i == 1 && !wifi.connected) {
                            wifiWarning = true
                        } else {
                            wifiWarning = false
                            mode = if (i == 0) SrtMode.CALLER else SrtMode.LISTENER
                        }
                    },
                    disabled = if (wifi.connected) emptySet() else setOf(1),
                    badge = { i ->
                        if (i == 1 && !wifi.connected) {
                            Icon(Icons.Filled.Lock, null, tint = Palette.Muted.copy(alpha = 0.6f), modifier = Modifier.size(14.dp).padding(end = 2.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (mode == SrtMode.CALLER) "The phone connects to the SRT output (source must be in Listener mode)."
                    else "The SRT output connects to the phone (source must be in Caller mode).",
                    color = Palette.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )

                AnimatedVisibility(visible = wifiWarning || listenerBlocked) {
                    WifiRequiredBanner(Modifier.padding(top = 12.dp))
                }

                if (mode == SrtMode.CALLER) {
                    LabeledField(
                        label = "Address",
                        value = host,
                        onValueChange = { host = it },
                        required = true,
                        placeholder = "10.60.2.99",
                        error = errors["host"],
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                } else if (wifi.connected) {
                    ListenerCard(ip = wifi.ip, port = port)
                }

                LabeledField(
                    label = "Port",
                    value = port,
                    onValueChange = { v -> port = v.filter { it.isDigit() }.take(5) },
                    required = true,
                    placeholder = "5000",
                    error = errors["port"],
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                LabeledField(
                    label = "Latency (ms)",
                    value = latency,
                    onValueChange = { v -> latency = v.filter { it.isDigit() }.take(4) },
                    required = true,
                    placeholder = "200",
                    error = errors["latency"],
                    helper = "Use at least 4 × the round-trip time. 120–250 ms is typical on a LAN.",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            } else {
                LabeledField(
                    label = "Stream name",
                    value = name,
                    onValueChange = { name = it.take(40) },
                    placeholder = "Program Feed",
                )
                LabeledField(
                    label = "Passphrase",
                    value = passphrase,
                    onValueChange = { passphrase = it.take(79) },
                    placeholder = "Leave empty for no encryption",
                    error = errors["passphrase"],
                    helper = "10–79 characters, must match the SRT output.",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    trailing = {
                        TextButton(onClick = { showPass = !showPass }) {
                            Text(if (showPass) "HIDE" else "SHOW", color = Palette.Accent, fontSize = 12.sp)
                        }
                    },
                )
                FieldLabel("Key length")
                val keyOptions = listOf(0, 16, 24, 32)
                SegmentedControl(
                    options = listOf("Auto", "AES-128", "AES-192", "AES-256"),
                    selected = keyOptions.indexOf(keyLength).coerceAtLeast(0),
                    onSelect = { keyLength = keyOptions[it] },
                    modifier = Modifier.fillMaxWidth(),
                )
                LabeledField(
                    label = "Stream ID",
                    value = streamId,
                    onValueChange = { streamId = it.take(512) },
                    placeholder = "Optional",
                )
                LabeledField(
                    label = "Connection timeout (s)",
                    value = timeout,
                    onValueChange = { v -> timeout = v.filter { it.isDigit() }.take(2) },
                    error = errors["timeout"],
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Palette.Surface.copy(alpha = 0.85f))
                        .border(1.dp, Palette.Outline.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Keep SRT on Wi-Fi", color = Palette.Text, fontSize = 15.sp)
                        Text(
                            "Use the Wi-Fi network even if it has no internet (typical for demo LANs).",
                            color = Palette.Muted,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Switch(
                        checked = routeViaWifi,
                        onCheckedChange = { routeViaWifi = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent),
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(
                    text = if (savedFlash) "SAVED ✓" else "SAVE",
                    onClick = {
                        validated()?.let {
                            onSave(it)
                            savedFlash = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = "PLAY STREAM",
                    onClick = { validated()?.let(onPlay) },
                    enabled = !listenerBlocked,
                    modifier = Modifier.weight(1.6f),
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ListenerCard(ip: String?, port: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Accent.copy(alpha = 0.10f))
            .border(1.dp, Palette.Accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(16.dp),
    ) {
        Text("THIS PHONE WILL LISTEN ON", color = Palette.Muted, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "${ip ?: "—"} : ${port.ifEmpty { "?" }}",
            color = Palette.Text,
            fontSize = 26.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        Text(
            "Set the Moxela / Virtuoso SRT output to Caller mode with this destination. " +
                "Guest or hotel Wi-Fi often blocks devices from reaching each other — a travel router or phone hotspot avoids that.",
            color = Palette.Muted,
            fontSize = 12.sp,
        )
    }
}
