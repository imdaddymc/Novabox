package com.novabox.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import coil.compose.AsyncImage

object Palette {
    val bg = Color(0xFF0A0E18)
    val bg2 = Color(0xFF111829)
    val card = Color(0xFF172036)
    val cardFocused = Color(0xFFEEF2FF)
    val accent = Color(0xFF5B8CFF)
    val teal = Color(0xFF3DDCCA)
    val gold = Color(0xFFFFC857)
    val text = Color(0xFFE8ECF7)
    val muted = Color(0xFF8A94AD)
    val danger = Color(0xFFFF6B6B)
}

@Composable
fun NovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.accent,
            onPrimary = Color.White,
            secondary = Palette.teal,
            background = Palette.bg,
            surface = Palette.bg2,
            onSurface = Palette.text,
            onBackground = Palette.text,
        )
    ) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = androidx.compose.material3.darkColorScheme(
                primary = Palette.accent, surface = Palette.bg2, background = Palette.bg,
                onSurface = Palette.text, onBackground = Palette.text,
            )
        ) {
            CompositionLocalProvider(LocalContentColor provides Palette.text) { content() }
        }
    }
}

val Shape12 = RoundedCornerShape(12.dp)

/**
 * The basic focusable building block. Everything in the app is reachable with
 * D-pad + OK; there are no colour-button shortcuts anywhere.
 */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    shape: RoundedCornerShape = Shape12,
    focusedScale: Float = 1.04f,
    onFocus: (() -> Unit)? = null,
    container: Color = Palette.card,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onFocus?.invoke()
        },
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Palette.accent.copy(alpha = 0.28f) else container,
            contentColor = Palette.text,
            focusedContainerColor = Palette.cardFocused,
            focusedContentColor = Color(0xFF0A0E18),
            pressedContainerColor = Palette.accent,
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(1.dp, Palette.accent.copy(alpha = 0.7f)), shape = shape) else Border.None,
            focusedBorder = Border(BorderStroke(2.dp, Palette.accent), shape = shape),
        ),
    ) {
        content(focused)
    }
}

@Composable
fun NovaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    tint: Color? = null,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        container = if (primary) Palette.accent else Palette.card,
        focusedScale = 1.08f,
    ) { focused ->
        Row(
            Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(20.dp), tint = if (!focused && tint != null) tint else LocalContentColor.current)
                Spacer(Modifier.width(10.dp))
            }
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier.padding(bottom = 10.dp),
        color = Palette.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
    )
}

@Composable
fun Logo(url: String, name: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(8.dp)).background(Color(0xFF222C45)),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(2).uppercase(), color = Palette.muted, fontWeight = FontWeight.Bold, fontSize = (size.value / 3).sp)
        if (url.isNotBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
        }
    }
}

@Composable
fun PosterCard(
    title: String,
    poster: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
    onFocus: (() -> Unit)? = null,
) {
    FocusSurface(onClick = onClick, modifier = modifier, focusedScale = 1.07f, onFocus = onFocus) { focused ->
        Column {
            Box(
                Modifier.fillMaxWidth().aspectPoster().background(
                    Brush.verticalGradient(listOf(Color(0xFF26324F), Color(0xFF151D30)))
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(title.take(1).uppercase(), fontSize = 42.sp, color = Palette.muted, fontWeight = FontWeight.Bold)
                if (poster.isNotBlank()) {
                    AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                if (favorite) {
                    Text("★", color = Palette.gold, fontSize = 22.sp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                }
            }
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    subtitle.ifBlank { " " }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp,
                    color = if (focused) Color(0xFF39425A) else Palette.muted,
                )
            }
        }
    }
}

fun Modifier.aspectPoster() = this.aspectRatio(2f / 3f)

@Composable
fun CenterMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Palette.muted, fontSize = 18.sp)
    }
}

@Composable
fun ScreenBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF0D1324), Palette.bg, Color(0xFF0B1220)))
        ),
        content = content,
    )
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier) {
    Text(
        text, fontSize = 13.sp, color = Palette.text,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

val RowGap = Arrangement.spacedBy(12.dp)
