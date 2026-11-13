package com.lembar.journal.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lembar.journal.ui.theme.Lembar

val CardShape = RoundedCornerShape(18.dp)
val ButtonShape = RoundedCornerShape(10.dp)

/** Big flat button (design 1 "check in" / design 2 orange pill). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val c = Lembar.colors
    val bg by animateColorAsState(if (enabled) c.primary else c.surfaceAlt, label = "btn")
    Row(
        modifier
            .heightIn(min = 54.dp)
            .clip(ButtonShape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) c.onPrimary else c.inkFaint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Lembar.type.label.copy(fontSize = Lembar.type.title.fontSize), color = if (enabled) c.onPrimary else c.inkFaint)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = Lembar.colors
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(ButtonShape)
            .border(1.dp, c.border, ButtonShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = c.ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Lembar.type.label, color = c.ink)
    }
}

/** Small rounded pill (design 2 "New Notes"). */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Lembar.colors
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) c.primary else c.surfaceAlt)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Lembar.type.label, color = if (enabled) c.onPrimary else c.inkFaint)
    }
}

/** Outlined chip, filled when selected (design 1 emotion chips). */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Lembar.colors
    val bg by animateColorAsState(if (selected) c.ink else Color.Transparent, label = "chip")
    val fg by animateColorAsState(if (selected) c.background else c.ink, label = "chipText")
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, if (selected) c.ink else c.border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
    ) {
        Text(text, style = Lembar.type.label.copy(fontSize = Lembar.type.bodySmall.fontSize), color = fg)
    }
}

/** Read-only tag. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    val c = Lembar.colors
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.surfaceAlt)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(text, style = Lembar.type.caption.copy(color = c.ink))
    }
}

@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Lembar.colors.ink,
    size: Dp = 44.dp,
    background: Color = Color.Transparent,
    shape: Shape = CircleShape,
) {
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    val c = Lembar.colors
    Box(
        modifier
            .clip(CardShape)
            .background(c.surface)
            .then(if (c.isDark) Modifier.border(BorderStroke(1.dp, c.border), CardShape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
    ) { content() }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = Lembar.type.headline.copy(fontSize = Lembar.type.title.fontSize * 1.2f))
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = Lembar.type.bodySmall)
        }
    }
}

@Composable
fun TopBar(
    title: String?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backIcon: ImageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.widthIn(min = 56.dp), contentAlignment = Alignment.CenterStart) {
            if (onBack != null) IconAction(backIcon, "Kembali", onBack)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (title != null) Text(title, style = Lembar.type.label, textAlign = TextAlign.Center)
        }
        Row(Modifier.widthIn(min = 96.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Lembar.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(c.surfaceAlt),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = c.ink, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Lembar.type.label)
            if (subtitle != null) Text(subtitle, style = Lembar.type.bodySmall)
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

@Composable
fun LembarSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = Lembar.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onPrimary,
            checkedTrackColor = c.primary,
            checkedBorderColor = c.primary,
            uncheckedThumbColor = c.inkMuted,
            uncheckedTrackColor = c.surfaceAlt,
            uncheckedBorderColor = c.border,
        ),
    )
}

/** Segmented control (design 1 "Week / Month / Year"). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Lembar.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) c.ink else Color.Transparent)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Lembar.type.label, color = if (active) c.background else c.inkMuted)
            }
        }
    }
}

/** No eraser glyph in the Material icon set we ship, so draw one. */
val EraserIcon: ImageVector by lazy {
    ImageVector.Builder("Eraser", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
            moveTo(15.1f, 3.6f)
            lineTo(21.0f, 9.5f)
            curveTo(21.6f, 10.1f, 21.6f, 11.1f, 21.0f, 11.7f)
            lineTo(13.4f, 19.3f)
            lineTo(20f, 19.3f)
            lineTo(20f, 21f)
            lineTo(8.4f, 21f)
            lineTo(3.0f, 15.6f)
            curveTo(2.4f, 15.0f, 2.4f, 14.0f, 3.0f, 13.4f)
            lineTo(12.9f, 3.6f)
            curveTo(13.5f, 3.0f, 14.5f, 3.0f, 15.1f, 3.6f)
            close()
            moveTo(9.1f, 19.3f)
            lineTo(11.0f, 19.3f)
            lineTo(13.6f, 16.7f)
            lineTo(8.3f, 11.4f)
            lineTo(4.2f, 15.5f)
            lineTo(8.0f, 19.3f)
            close()
        }
    }.build()
}
