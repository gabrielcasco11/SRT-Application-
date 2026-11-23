package com.gabriel.srtmonitor.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabriel.srtmonitor.R
import com.gabriel.srtmonitor.ui.theme.Palette

/**
 * Full-screen background. To change it, replace res/drawable/app_background.xml
 * with your own image named app_background (e.g. res/drawable-nodpi/app_background.jpg).
 */
@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        Image(
            painter = painterResource(R.drawable.app_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        content()
    }
}

/** App mark: gradient tile with a play triangle and two signal arcs. */
@Composable
fun LogoMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Palette.Accent, Palette.AccentCyan), Offset.Zero, Offset(w, w)),
            cornerRadius = CornerRadius(w * 0.26f),
        )
        val tri = Path().apply {
            moveTo(w * 0.27f, w * 0.29f)
            lineTo(w * 0.27f, w * 0.71f)
            lineTo(w * 0.56f, w * 0.50f)
            close()
        }
        drawPath(tri, Color.White)
        val stroke = Stroke(width = w * 0.065f, cap = StrokeCap.Round)
        listOf(0.17f, 0.29f).forEachIndexed { i, r ->
            val radius = w * r
            val c = Offset(w * 0.52f, w * 0.5f)
            drawArc(
                color = Color.White.copy(alpha = if (i == 0) 0.95f else 0.6f),
                startAngle = -50f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(c.x - radius, c.y - radius),
                size = Size(radius * 2, radius * 2),
                style = stroke,
            )
        }
    }
}

@Composable
fun Wordmark(modifier: Modifier = Modifier, big: Boolean = false) {
    Column(modifier) {
        Text(
            "SRT",
            color = Palette.Muted,
            fontSize = if (big) 13.sp else 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 3.sp,
        )
        Text(
            "Monitor",
            color = Palette.Text,
            fontSize = if (big) 34.sp else 22.sp,
            fontWeight = FontWeight.Light,
            lineHeight = if (big) 36.sp else 24.sp,
        )
    }
}

@Composable
fun BackRow(onBack: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back", tint = Palette.Accent)
            Text("Back", color = Palette.Accent, fontSize = 15.sp)
        }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** Pill-shaped two/three-way switch, like the Quick | Advanced control in Play Pro. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    disabled: Set<Int> = emptySet(),
    badge: @Composable (Int) -> Unit = {},
) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Palette.Field)
            .border(1.dp, Palette.Outline, RoundedCornerShape(8.dp))
            .padding(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val isSel = i == selected
            val bg by animateColorAsState(if (isSel) Palette.SurfaceHigh else Color.Transparent, label = "seg")
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(bg)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                badge(i)
                Text(
                    label,
                    color = when {
                        i in disabled -> Palette.Muted.copy(alpha = 0.5f)
                        isSel -> Palette.Text
                        else -> Palette.Muted
                    },
                    fontSize = 14.sp,
                    fontWeight = if (isSel) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
fun FieldLabel(text: String, required: Boolean = false) {
    Text(
        buildAnnotatedString {
            append(text)
            if (required) withStyle(SpanStyle(color = Palette.Live)) { append("*") }
        },
        color = Palette.Muted,
        fontSize = 13.sp,
        modifier = Modifier.padding(bottom = 6.dp, top = 14.dp),
    )
}

@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    placeholder: String = "",
    error: String? = null,
    helper: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        FieldLabel(label, required)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            isError = error != null,
            placeholder = { Text(placeholder, color = Palette.Muted.copy(alpha = 0.6f)) },
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            trailingIcon = trailing,
            shape = RoundedCornerShape(6.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Palette.Field,
                unfocusedContainerColor = Palette.Field,
                focusedBorderColor = Palette.Accent,
                unfocusedBorderColor = Palette.Outline,
                errorBorderColor = Palette.Bad,
                errorContainerColor = Palette.Field,
                cursorColor = Palette.Accent,
                focusedTextColor = Palette.Text,
                unfocusedTextColor = Palette.Text,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            error != null -> Text(error, color = Palette.Bad, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            helper != null -> Text(helper, color = Palette.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.Accent,
            disabledContainerColor = Palette.Accent.copy(alpha = 0.3f),
            disabledContentColor = Color.White.copy(alpha = 0.5f),
        ),
        modifier = modifier.height(48.dp),
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, Palette.Accent),
        modifier = modifier.height(48.dp),
    ) {
        Text(text, color = Palette.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, icon: (@Composable () -> Unit)? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(8.dp))
        }
        Text(title, color = Palette.Text, fontSize = 17.sp)
        Spacer(Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                color = Palette.Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onAction).padding(6.dp),
            )
        }
    }
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier, solid: Boolean = false) {
    Row(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (solid) color else color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!solid) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            color = if (solid) Color.White else color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

/** Shown wherever listener mode is chosen but the phone is not on Wi-Fi. */
@Composable
fun WifiRequiredBanner(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Palette.Warn.copy(alpha = 0.10f))
            .border(1.dp, Palette.Warn.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = Palette.Warn, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("Listener mode only works on Wi-Fi", color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                "Connect the phone to the same Wi-Fi or LAN as the Moxela / Virtuoso. " +
                    "Over mobile data the phone can't accept incoming SRT connections, so use Caller mode instead.",
                color = Palette.Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Four-corner "expand" glyph (not in the core icon set). */
@Composable
fun FullscreenGlyph(exit: Boolean, modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier.size(22.dp)) {
        val w = size.width
        val l = w * 0.32f
        val s = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            val p = Path().apply {
                if (!exit) {
                    moveTo(x, y + dy * l); lineTo(x, y); lineTo(x + dx * l, y)
                } else {
                    val cx = x + dx * l
                    val cy = y + dy * l
                    moveTo(cx - dx * l, cy); lineTo(cx, cy); lineTo(cx, cy - dy * l)
                }
            }
            drawPath(p, color, style = s)
        }
        val m = w * 0.1f
        corner(m, m, 1f, 1f)
        corner(w - m, m, -1f, 1f)
        corner(m, w - m, 1f, -1f)
        corner(w - m, w - m, -1f, -1f)
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Surface.copy(alpha = 0.85f))
            .border(1.dp, Palette.Outline.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}
