package com.gabriel.srtmonitor.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabriel.srtmonitor.model.SrtMode
import com.gabriel.srtmonitor.model.StreamConfig
import com.gabriel.srtmonitor.net.WifiMonitor
import com.gabriel.srtmonitor.player.StreamController
import com.gabriel.srtmonitor.ui.components.Card
import com.gabriel.srtmonitor.ui.components.LogoMark
import com.gabriel.srtmonitor.ui.components.PrimaryButton
import com.gabriel.srtmonitor.ui.components.SectionHeader
import com.gabriel.srtmonitor.ui.components.StatusPill
import com.gabriel.srtmonitor.ui.components.WifiRequiredBanner
import com.gabriel.srtmonitor.ui.components.Wordmark
import com.gabriel.srtmonitor.ui.theme.Palette

@Composable
fun HomeScreen(
    config: StreamConfig,
    wifi: WifiMonitor.State,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
) {
    val listenerBlocked = config.mode == SrtMode.LISTENER && !wifi.connected
    val canPlay = config.isConfigured && !listenerBlocked

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 560.dp)) {
            // ---- header ----
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                LogoMark(34.dp)
                Spacer(Modifier.width(12.dp))
                Wordmark()
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Settings, contentDescription = "Stream settings", tint = Palette.Text)
                }
            }

            SectionHeader(
                title = "My Stream",
                icon = { Box(Modifier.size(8.dp).clip(CircleShape).background(if (canPlay) Palette.Good else Palette.Muted)) },
                action = if (config.isConfigured) "EDIT" else null,
                onAction = onEdit,
            )

            // ---- stream card with last-seen thumbnail ----
            Card(onClick = if (config.isConfigured) (if (canPlay) onPlay else onEdit) else onEdit) {
                Column {
                    StreamThumbnail(config, canPlay)
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            if (config.isConfigured) config.name else "Set up your stream",
                            color = Palette.Text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            if (config.isConfigured) "${config.modeLabel} · ${config.endpointLabel} · ${config.latencyMs} ms"
                            else "Enter the SRT address of your Moxela or Virtuoso output",
                            color = Palette.Muted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            if (config.isConfigured) {
                SectionHeader(title = "Connection", modifier = Modifier.padding(top = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoTile("MODE", config.modeLabel, Modifier.weight(1f))
                    InfoTile(
                        if (config.mode == SrtMode.CALLER) "ADDRESS" else "LISTEN ON",
                        if (config.mode == SrtMode.CALLER) config.host else (wifi.ip ?: "No Wi-Fi"),
                        Modifier.weight(1.6f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoTile("PORT", config.port.toString(), Modifier.weight(1f))
                    InfoTile("LATENCY", "${config.latencyMs} ms", Modifier.weight(1f))
                    InfoTile("ENCRYPTION", config.encryptionLabel, Modifier.weight(1f))
                }
            }

            if (listenerBlocked) {
                Spacer(Modifier.height(16.dp))
                WifiRequiredBanner()
            }

            Spacer(Modifier.height(22.dp))
            PrimaryButton(
                text = if (config.isConfigured) "PLAY STREAM" else "SET UP STREAM",
                onClick = if (config.isConfigured) onPlay else onEdit,
                enabled = !config.isConfigured || canPlay,
                modifier = Modifier.fillMaxWidth(),
            )

            NetworkLine(wifi, Modifier.padding(top = 14.dp, bottom = 24.dp))
        }
    }
}

@Composable
private fun StreamThumbnail(config: StreamConfig, canPlay: Boolean) {
    val context = LocalContext.current
    val file = StreamController.thumbnailFile(context)
    val bitmap = remember(file.lastModified()) {
        if (file.exists()) runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull() else null
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(
                Brush.linearGradient(listOf(Color(0xFF0F2547), Color(0xFF0A1428), Color(0xFF14305A))),
            ),
    ) {
        if (bitmap != null && config.isConfigured) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // bottom fade for legibility
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f)),
            ),
        )
        if (config.isConfigured) {
            Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(config.modeLabel.uppercase(), Palette.AccentCyan)
                if (config.encrypted) {
                    Row(
                        Modifier.clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = Palette.Text, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(config.encryptionLabel, color = Palette.Text, fontSize = 11.sp)
                    }
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .size(62.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .border(1.5.dp, Color.White.copy(alpha = if (canPlay || !config.isConfigured) 0.9f else 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (config.isConfigured) Icons.Filled.PlayArrow else Icons.Filled.Add,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (canPlay || !config.isConfigured) 1f else 0.4f),
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

@Composable
private fun InfoTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Palette.Surface.copy(alpha = 0.85f))
            .border(1.dp, Palette.Outline.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, color = Palette.Muted, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
        Text(
            value,
            color = Palette.Text,
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun NetworkLine(wifi: WifiMonitor.State, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (wifi.connected) Palette.Good else Palette.Warn))
        Spacer(Modifier.width(8.dp))
        Text(
            if (wifi.connected) "Wi-Fi · ${wifi.ip ?: "no IPv4 address"}" else "Mobile data · caller mode only",
            color = Palette.Muted,
            fontSize = 12.sp,
        )
    }
}
