package com.gabriel.srtmonitor.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.gabriel.srtmonitor.SrtApp
import com.gabriel.srtmonitor.model.SrtMode
import com.gabriel.srtmonitor.model.StreamConfig
import com.gabriel.srtmonitor.player.Labels
import com.gabriel.srtmonitor.player.LiveUi
import com.gabriel.srtmonitor.player.Phase
import com.gabriel.srtmonitor.player.StreamController
import com.gabriel.srtmonitor.ui.components.BackRow
import com.gabriel.srtmonitor.ui.components.Card
import com.gabriel.srtmonitor.ui.components.ChartSeries
import com.gabriel.srtmonitor.ui.components.FullscreenGlyph
import com.gabriel.srtmonitor.ui.components.LineChart
import com.gabriel.srtmonitor.ui.components.SegmentedControl
import com.gabriel.srtmonitor.ui.components.StatusPill
import com.gabriel.srtmonitor.ui.theme.Palette
import kotlinx.coroutines.delay

private const val HISTORY = 90

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(config: StreamConfig, app: SrtApp, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val controller = remember(config) { StreamController(context, config, app.wifi) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Play while visible, stop in background, clean up when leaving.
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> controller.start()
                Lifecycle.Event.ON_STOP -> controller.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.release()
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val ui by controller.ui.collectAsState()
    val wifi by app.wifi.state.collectAsState()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(landscape) {
        val w = activity?.window ?: return@LaunchedEffect
        val c = WindowCompat.getInsetsController(w, w.decorView)
        if (landscape) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val enterFullscreen = { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
    val exitFullscreen = { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }

    BackHandler(enabled = landscape) { exitFullscreen() }

    // The video view keeps its surface when moving between portrait and fullscreen layouts.
    val video = remember(controller) {
        movableContentOf<LiveUi, Boolean> { state, fill ->
            VideoArea(controller, state, config, wifi.ip, fill)
        }
    }

    var showLog by remember { mutableStateOf(false) }

    if (landscape) {
        FullscreenLayout(ui, config, onExit = exitFullscreen) { video(ui, true) }
    } else {
        PortraitLayout(ui, config, onBack = onBack, onFullscreen = enterFullscreen, onShowLog = { showLog = true }) { video(ui, false) }
    }
    if (showLog) LogDialog(onDismiss = { showLog = false })
}

@Composable
private fun LogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("Reading log…") }
    LaunchedEffect(Unit) {
        text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.gabriel.srtmonitor.DiagnosticsLog.read() }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("SRT log", color = Palette.Text) },
        text = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .background(Palette.Field)
                    .padding(8.dp),
            ) {
                Text(
                    text,
                    color = Palette.Text,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.verticalScroll(rememberScrollState(Int.MAX_VALUE)),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "SRT Monitor log")
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                }
                context.startActivity(android.content.Intent.createChooser(send, "Share log"))
            }) { Text("SHARE", color = Palette.Accent) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("CLOSE", color = Palette.Muted) }
        },
    )
}

// ---- layouts -----------------------------------------------------------------------------------

@Composable
private fun PortraitLayout(
    ui: LiveUi,
    config: StreamConfig,
    onBack: () -> Unit,
    onFullscreen: () -> Unit,
    onShowLog: () -> Unit,
    video: @Composable () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(1) }
    Column(
        Modifier.fillMaxSize().systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            BackRow(onBack = onBack, modifier = Modifier.padding(horizontal = 12.dp)) {
                androidx.compose.material3.TextButton(onClick = onShowLog) {
                    Text("LOG", color = Palette.Accent, fontSize = 12.sp, letterSpacing = 1.sp)
                }
                PhasePill(ui.phase, Modifier.padding(end = 4.dp))
            }
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
                video()
                IconButton(onClick = onFullscreen, modifier = Modifier.align(Alignment.BottomEnd)) {
                    FullscreenGlyph(exit = false)
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(config.name, color = Palette.Text, fontSize = 20.sp)
                        Text(
                            "${config.modeLabel} · ${ui.peer ?: config.endpointLabel}",
                            color = Palette.Muted,
                            fontSize = 13.sp,
                        )
                    }
                    ui.latest?.let {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Labels.bitrate(it.kbps), color = Palette.Text, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                            Text("RTT ${it.rttMs.toInt()} ms", color = Palette.Muted, fontSize = 12.sp)
                        }
                    }
                }
                SegmentedControl(
                    options = listOf("Info", "Statistics"),
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                )
                if (tab == 0) InfoTab(ui, config) else StatsTab(ui)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun FullscreenLayout(ui: LiveUi, config: StreamConfig, onExit: () -> Unit, video: @Composable () -> Unit) {
    var overlay by remember { mutableStateOf(true) }
    var lastTouch by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(overlay, lastTouch) {
        if (overlay) {
            delay(4000)
            overlay = false
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                overlay = !overlay
                lastTouch = SystemClock.elapsedRealtime()
            },
    ) {
        video()
        AnimatedVisibility(visible = overlay, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Exit fullscreen", tint = Color.White)
                    }
                    Text(config.name, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    PhasePill(ui.phase)
                    IconButton(onClick = onExit) { FullscreenGlyph(exit = true) }
                }
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    HudItem("VIDEO", "${Labels.shortVideoCodec(ui.video?.mime)} ${ui.video?.height?.takeIf { it > 0 }?.let { "${it}p" } ?: ""}".trim())
                    HudItem("BITRATE", Labels.bitrate(ui.latest?.kbps))
                    HudItem("RTT", ui.latest?.let { "${it.rttMs.toInt()} ms" } ?: "—")
                    HudItem("LOST", ui.pktLost.toString())
                    HudItem("DROPPED", ui.pktDropped.toString())
                    HudItem("FPS", ui.measuredFps?.let { "%.0f".format(it) } ?: "—")
                }
            }
        }
    }
}

@Composable
private fun HudItem(label: String, value: String) {
    Column {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, letterSpacing = 1.sp)
        Text(value, color = Color.White, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
    }
}

// ---- video + status overlays -----------------------------------------------------------------

@OptIn(UnstableApi::class)
@Composable
private fun VideoArea(controller: StreamController, ui: LiveUi, config: StreamConfig, phoneIp: String?, fill: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val ar = ui.videoAspect
        val containerAr = if (maxHeight.value > 0) maxWidth.value / maxHeight.value else ar
        AndroidView(
            factory = { ctx -> TextureView(ctx).also { controller.textureView = it } },
            onRelease = { if (controller.textureView === it) controller.textureView = null },
            modifier = Modifier.aspectRatio(ar, matchHeightConstraintsFirst = containerAr > ar),
        )
        if (ui.phase == Phase.PLAYING) {
            StatusPill("LIVE", Palette.Live, Modifier.align(Alignment.TopStart).padding(if (fill) 0.dp else 10.dp).padding(top = if (fill) 70.dp else 0.dp, start = if (fill) 20.dp else 0.dp), solid = true)
        } else {
            StatusOverlay(ui, config, phoneIp)
        }
    }
}

@Composable
private fun StatusOverlay(ui: LiveUi, config: StreamConfig, phoneIp: String?) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF05080F).copy(alpha = 0.85f)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (ui.phase) {
            Phase.LISTENING -> {
                PulseRings()
                Text("Waiting for caller", color = Palette.Text, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
                Text(
                    "${phoneIp ?: "—"} : ${config.port}",
                    color = Palette.AccentCyan,
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Phase.RECONNECTING, Phase.ERROR -> {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Palette.Warn, modifier = Modifier.size(34.dp))
                Text(
                    if (ui.phase == Phase.ERROR) "Can't connect" else "Signal lost",
                    color = Palette.Text,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (ui.message.isNotBlank()) {
                    Text(ui.message, color = Palette.Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                }
                Text("Retrying…", color = Palette.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            else -> {
                CircularProgressIndicator(color = Palette.Accent, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                Text(
                    when (ui.phase) {
                        Phase.CONNECTED -> "Connected · waiting for video"
                        else -> if (config.mode == SrtMode.CALLER) "Connecting to ${config.host}:${config.port}" else "Starting…"
                    },
                    color = Palette.Text,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
                if (ui.phase == Phase.CONNECTED && ui.message.isNotBlank()) {
                    Text(ui.message, color = Palette.Warn, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun PulseRings() {
    val t = rememberInfiniteTransition(label = "pulse")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "p")
    Canvas(Modifier.size(64.dp)) {
        val r = size.minDimension / 2
        for (i in 0..1) {
            val f = (p + i * 0.5f) % 1f
            drawCircle(Palette.AccentCyan.copy(alpha = 1f - f), radius = r * (0.25f + 0.75f * f), style = Stroke(2.dp.toPx()))
        }
        drawCircle(Palette.AccentCyan, radius = r * 0.16f, center = Offset(size.width / 2, size.height / 2))
    }
}

@Composable
private fun PhasePill(phase: Phase, modifier: Modifier = Modifier) {
    val (text, color) = when (phase) {
        Phase.PLAYING -> "LIVE" to Palette.Good
        Phase.CONNECTED -> "CONNECTED" to Palette.Good
        Phase.CONNECTING -> "CONNECTING" to Palette.Warn
        Phase.LISTENING -> "LISTENING" to Palette.AccentCyan
        Phase.RECONNECTING -> "RECONNECTING" to Palette.Warn
        Phase.ERROR -> "NO SIGNAL" to Palette.Bad
        Phase.IDLE -> "IDLE" to Palette.Muted
    }
    StatusPill(text, color, modifier)
}

// ---- tabs --------------------------------------------------------------------------------------

@Composable
private fun StatsTab(ui: LiveUi) {
    val h = ui.history
    Card {
        Column(Modifier.padding(14.dp)) {
            LineChart(
                title = "Delays (ms)",
                capacity = HISTORY,
                series = listOf(
                    ChartSeries("Rcv Buffer", Palette.ChartBuffer, h.map { it.rcvBufferMs }),
                    ChartSeries("Round Trip", Palette.ChartRtt, h.map { it.rttMs }),
                    ChartSeries("Latency", Palette.ChartLatency, h.map { it.latencyMs }),
                ),
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Card {
        Column(Modifier.padding(14.dp)) {
            LineChart(
                title = "Bandwidth (kb/s)",
                capacity = HISTORY,
                series = listOf(ChartSeries("Receive rate", Palette.ChartBandwidth, h.map { it.kbps }, fill = true)),
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    val tiles = listOf(
        "BITRATE" to Labels.bitrate(ui.latest?.kbps),
        "ROUND TRIP" to (ui.latest?.let { "${it.rttMs.toInt()} ms" } ?: "—"),
        "PACKETS" to ui.pktReceived.toString(),
        "LOST" to ui.pktLost.toString(),
        "RETRANSMITTED" to ui.pktRetransmitted.toString(),
        "DROPPED" to ui.pktDropped.toString(),
        "FRAME RATE" to (ui.measuredFps?.let { "%.0f fps".format(it) } ?: "—"),
        "FRAMES DROPPED" to ui.droppedFrames.toString(),
        "PLAYER BUFFER" to "${ui.playerBufferMs} ms",
    )
    tiles.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { (label, value) ->
                val warn = (label == "LOST" || label == "DROPPED") && value != "0"
                StatTile(label, value, Modifier.weight(1f), if (warn) Palette.Warn else Palette.Text)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier, valueColor: Color) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Palette.Surface.copy(alpha = 0.85f))
            .border(1.dp, Palette.Outline.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Text(label, color = Palette.Muted, fontSize = 9.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(value, color = valueColor, fontSize = 15.sp, fontFamily = FontFamily.Monospace, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun InfoTab(ui: LiveUi, config: StreamConfig) {
    val now by produceTicker()
    val uptime = ui.connectedAtMs?.let { Labels.duration(now - it) } ?: "—"
    val v = ui.video
    val a = ui.audio
    val hw = Labels.isHardwareDecoder(ui.videoDecoder)

    InfoGroup(
        "Connection",
        listOf(
            "Status" to phaseText(ui.phase),
            "Mode" to config.modeLabel,
            "Remote" to (ui.peer ?: "—"),
            "Latency setting" to "${config.latencyMs} ms",
            "Negotiated latency" to (ui.latest?.let { "${it.latencyMs.toInt()} ms" } ?: "—"),
            "Encryption" to config.encryptionLabel,
            "Stream ID" to config.streamId.ifBlank { "—" },
            "Connected for" to uptime,
        ),
    )
    Spacer(Modifier.height(12.dp))
    InfoGroup(
        "Video",
        listOf(
            "Codec" to Labels.videoCodec(v?.mime),
            "Profile" to Labels.profile(v?.codecs),
            "Resolution" to (v?.takeIf { it.width > 0 }?.let {
                if (ui.isFieldCoded) "${it.width} × ${it.height * 2} interlaced (field-coded)" else "${it.width} × ${it.height}"
            } ?: "—"),
            "Frame rate" to (v?.frameRate?.takeIf { it > 0 }?.let { "%.2f fps".format(it) }
                ?: ui.measuredFps?.let { "≈ %.0f fps".format(it) } ?: "—"),
            "Decoder" to (ui.videoDecoder?.let { "$it (${if (hw == true) "hardware" else "software"})" } ?: "—"),
        ),
    )
    Spacer(Modifier.height(12.dp))
    InfoGroup(
        "Audio",
        listOf(
            "Codec" to (Labels.audioCodec(a?.mime) + if (a != null && !a.supported) " — not supported on this phone" else ""),
            "Channels" to (a?.channels?.takeIf { it > 0 }?.toString() ?: "—"),
            "Sample rate" to (a?.sampleRate?.takeIf { it > 0 }?.let { "${it / 1000.0} kHz" } ?: "—"),
            "Decoder" to (ui.audioDecoder ?: "—"),
        ),
    )
}

@Composable
private fun InfoGroup(title: String, rows: List<Pair<String, String>>) {
    Card {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(title.uppercase(), color = Palette.Accent, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
            rows.forEach { (k, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(k, color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.width(140.dp))
                    Text(value, color = Palette.Text, fontSize = 13.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
            }
        }
    }
}

private fun phaseText(p: Phase) = when (p) {
    Phase.PLAYING -> "Live"
    Phase.CONNECTED -> "Connected, waiting for video"
    Phase.CONNECTING -> "Connecting"
    Phase.LISTENING -> "Listening"
    Phase.RECONNECTING -> "Reconnecting"
    Phase.ERROR -> "No connection"
    Phase.IDLE -> "Idle"
}

@Composable
private fun produceTicker() = androidx.compose.runtime.produceState(SystemClock.elapsedRealtime()) {
    while (true) {
        value = SystemClock.elapsedRealtime()
        delay(1000)
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
