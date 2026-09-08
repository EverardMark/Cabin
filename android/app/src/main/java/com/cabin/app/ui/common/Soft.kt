package com.cabin.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cabin.app.ui.theme.SoftAccent
import com.cabin.app.ui.theme.SoftAvatar
import com.cabin.app.ui.theme.SoftBackgroundBottom
import com.cabin.app.ui.theme.SoftBackgroundMid
import com.cabin.app.ui.theme.SoftBackgroundTop
import com.cabin.app.ui.theme.SoftInk
import com.cabin.app.ui.theme.SoftLabel
import com.cabin.app.ui.theme.SoftMuted
import com.cabin.app.ui.theme.SoftOutline
import com.cabin.app.ui.theme.SoftPinEnd
import com.cabin.app.ui.theme.SoftRed
import com.cabin.app.ui.theme.SoftSecondary
import com.cabin.app.ui.theme.SoftShadowCard
import com.cabin.app.ui.theme.SoftShadowTab
import com.cabin.app.ui.theme.SoftText
import com.cabin.app.ui.theme.SoftTextSoft
import com.cabin.app.ui.theme.SoftTile
import com.cabin.app.ui.theme.SoftType
import com.cabin.app.ui.theme.soft

// MARK: Building blocks from the Cabin Soft design — one composable per class
// in the hand-off (.mark, .circ, .card, .tile, .tab, .pill, .bub, .vtag, .otag,
// .row, .pbtn, .sbtn), matching the iOS SoftComponents.swift one to one.

object SoftRadius {
    val card = 28.dp
    val row = 22.dp
    val bubble = 22.dp
    val tile = 20.dp
    val image = 20.dp
    val codeBox = 18.dp
    val mark = 14.dp
    val photo = 12.dp
}

/** Soft drop shadows; Compose shadows are elevation-based so these are approximations. */
fun Modifier.softShadowCard(shape: androidx.compose.ui.graphics.Shape) =
    shadow(10.dp, shape, ambientColor = SoftShadowCard.copy(alpha = 0.10f), spotColor = SoftShadowCard.copy(alpha = 0.16f))

fun Modifier.softShadowRow(shape: androidx.compose.ui.graphics.Shape) =
    shadow(6.dp, shape, ambientColor = SoftShadowCard.copy(alpha = 0.08f), spotColor = SoftShadowCard.copy(alpha = 0.12f))

fun Modifier.softShadowTab(shape: androidx.compose.ui.graphics.Shape) =
    shadow(14.dp, shape, ambientColor = SoftShadowTab.copy(alpha = 0.18f), spotColor = SoftShadowTab.copy(alpha = 0.28f))

/** Click without the Material ripple; the design has no highlight. */
fun Modifier.softClick(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    clickable(enabled = enabled, indication = null, interactionSource = null, onClick = onClick)

/** The pale green-to-cream ground behind every screen. */
@Composable
fun SoftBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to SoftBackgroundTop,
                    0.45f to SoftBackgroundMid,
                    1f to SoftBackgroundBottom,
                )
            ),
        content = content,
    )
}

/** The 44dp black tile with the house glyph (.mark). */
@Composable
fun AppMark(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(SoftRadius.mark))
            .background(SoftInk),
    ) {
        Icon(Icons.Outlined.Home, contentDescription = "Cabin", tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** House glyph + "cabin" wordmark from the welcome screen. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        Icon(Icons.Outlined.Home, contentDescription = null, tint = SoftInk, modifier = Modifier.size(28.dp))
        Text("cabin", style = soft(26, FontWeight.Normal, (-0.3).sp))
    }
}

/** Translucent white circle button (.circ) used for back, search, chat, close, settings. */
@Composable
fun CircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    filled: Boolean = false,
    inverted: Boolean = false,
    badge: Boolean = false,
    enabled: Boolean = true,
) {
    Box(modifier = modifier.size(size)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .shadow(2.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.08f))
                .clip(CircleShape)
                .background(if (inverted) SoftInk else if (filled) Color.White else Color.White.copy(alpha = 0.75f))
                .softClick(enabled, onClick),
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (inverted) Color.White else SoftTextSoft,
                modifier = Modifier.size(size * 0.42f),
            )
        }
        if (badge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-10).dp, y = 10.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(SoftAccent),
            )
        }
    }
}

/** Header row used on every main screen: a leading mark (or back circle), a centred title, a trailing action. */
@Composable
fun SoftHeader(
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit = { Spacer(Modifier.size(48.dp)) },
    title: @Composable () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Box(modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp)) { title() }
        Box(modifier = Modifier.align(Alignment.CenterStart)) { leading() }
        Box(modifier = Modifier.align(Alignment.CenterEnd)) { trailing() }
    }
}

/** Black pill button (.pbtn). */
@Composable
fun SoftPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    large: Boolean = false,
    loading: Boolean = false,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .clip(CircleShape)
            .background(SoftInk.copy(alpha = if (enabled) 1f else 0.45f))
            .softClick(enabled && !loading, onClick)
            .padding(vertical = if (large) 18.dp else 14.dp, horizontal = 22.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            if (icon != null) Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(text, style = if (large) SoftType.buttonLarge else SoftType.button, color = Color.White)
        }
    }
}

/** White (or tinted) pill button (.sbtn). */
@Composable
fun SoftSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    large: Boolean = false,
    tint: Color = Color.White,
    loading: Boolean = false,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = if (enabled) tint.alpha else 0.45f))
            .softClick(enabled && !loading, onClick)
            .padding(vertical = if (large) 16.dp else 14.dp, horizontal = 22.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(color = SoftInk, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            if (icon != null) Icon(icon, contentDescription = null, tint = SoftText, modifier = Modifier.size(18.dp))
            Text(text, style = if (large) soft(16, FontWeight.Normal) else SoftType.button, color = SoftText)
        }
    }
}

/** Small text-only action ("Send a new code", "Log in"). */
@Composable
fun SoftLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, muted: Boolean = false) {
    Text(
        text,
        style = soft(14, if (muted) FontWeight.Light else FontWeight.Normal),
        color = if (muted) SoftSecondary else SoftText,
        modifier = modifier.softClick(onClick = onClick).padding(4.dp),
    )
}

/** Black check tag (.vtag) — the verification badge. `light` is the white variant for "Featured". */
@Composable
fun VTag(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Outlined.Check,
    light: Boolean = false,
    tint: Color? = null,
) {
    val fg = if (light) SoftText else Color.White
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .clip(CircleShape)
            .background(tint ?: if (light) Color.White.copy(alpha = 0.9f) else SoftInk)
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
        Text(text, style = SoftType.tag, color = fg)
    }
}

/** Outlined tag (.otag): "Optional", "★ 4.8 · 12", "Pending". */
@Composable
fun OTag(text: String, modifier: Modifier = Modifier, filled: Boolean = false) {
    Text(
        text,
        style = SoftType.tag,
        color = SoftLabel,
        modifier = modifier
            .clip(CircleShape)
            .background(if (filled) Color.White else Color.Transparent)
            .border(BorderStroke(1.dp, SoftOutline), CircleShape)
            .padding(horizontal = 11.dp, vertical = 4.dp),
    )
}

/** White rounded card (.card). */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(SoftRadius.card)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .softShadowCard(shape)
            .clip(shape)
            .background(Color.White)
            .padding(padding),
        content = content,
    )
}

/** Tinted stat tile (.tile): icon, label, big number. */
@Composable
fun SoftTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(if (compact) 18.dp else 26.dp),
        modifier = modifier
            .clip(RoundedCornerShape(SoftRadius.tile))
            .background(SoftTile)
            .padding(if (compact) 14.dp else 16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = SoftTextSoft.copy(alpha = 0.75f), modifier = Modifier.size(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = soft(if (compact) 14 else 15), color = SoftLabel)
            Text(
                value,
                style = soft(if (compact) 30 else 38, letterSpacing = (-0.6).sp),
                color = SoftTextSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** White list row (.row). */
@Composable
fun SoftRow(
    modifier: Modifier = Modifier,
    fill: Color = Color.White,
    shadow: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(SoftRadius.row)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(if (shadow) Modifier.softShadowRow(shape) else Modifier)
            .clip(shape)
            .background(fill)
            .then(if (onClick != null) Modifier.softClick(onClick = onClick) else Modifier)
            .padding(12.dp),
        content = content,
    )
}

/** Pill-shaped white text field (.bub input). */
@Composable
fun SoftField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    secure: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leading: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
) {
    val shape = CircleShape
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .softShadowRow(shape)
            .clip(shape)
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        if (leading != null) leading()
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = SoftType.bodyLight, color = SoftMuted)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = SoftType.bodyLight,
                singleLine = singleLine,
                cursorBrush = SolidColor(SoftInk),
                visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Two-way pill segment ("Private individual" / "Real estate agent"). */
@Composable
fun <T> SoftSegment(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    Row(
        modifier = modifier
            .fillMaxWidth()
            .softShadowRow(shape)
            .clip(shape)
            .background(Color.White)
            .padding(5.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Text(
                label,
                style = SoftType.button,
                color = if (on) Color.White else SoftLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (on) SoftInk else Color.Transparent)
                    .softClick { onSelect(value) }
                    .padding(vertical = 12.dp, horizontal = 10.dp),
            )
        }
    }
}

/** Filter chip: black when selected, white otherwise. */
@Composable
fun SoftChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        style = soft(14, FontWeight.Normal),
        color = if (selected) Color.White else SoftLabel,
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) SoftInk else Color.White.copy(alpha = 0.75f))
            .softClick(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

/** Initials avatar (.av). */
@Composable
fun SoftAvatarView(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val initials = remember(name) {
        name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size).clip(CircleShape).background(SoftAvatar),
    ) {
        Text(initials, style = soft((size.value * 0.34f).toInt().coerceAtLeast(10)), color = SoftTextSoft)
    }
}

/** Black floating stat pill on the map (.pill). */
@Composable
fun SoftPill(value: String, label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .shadow(10.dp, shape, spotColor = Color.Black.copy(alpha = 0.3f))
            .clip(shape)
            .background(SoftInk)
            .then(if (onClick != null) Modifier.softClick(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp, horizontal = 22.dp),
    ) {
        Text(value, style = soft(26, FontWeight.Normal), color = Color.White, maxLines = 1)
        Text(label, style = soft(13), color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Photo placeholder where the design has a drop zone and we have no image. */
@Composable
fun SoftPhotoPlaceholder(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.background(Brush.linearGradient(listOf(SoftAvatar, SoftPinEnd))),
    ) {
        Icon(Icons.Outlined.Home, contentDescription = null, tint = SoftText.copy(alpha = 0.6f), modifier = Modifier.size(30.dp))
    }
}

/** Rounded remote photo with the soft placeholder. */
@Composable
fun SoftPhoto(url: String?, modifier: Modifier = Modifier, radius: Dp = SoftRadius.image, contentDescription: String? = null) {
    NetworkImage(
        url = url,
        contentDescription = contentDescription,
        modifier = modifier.clip(RoundedCornerShape(radius)),
    )
}

/** Centred empty / error state. */
@Composable
fun SoftEmpty(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 60.dp),
    ) {
        Icon(icon, contentDescription = null, tint = SoftMuted, modifier = Modifier.size(36.dp))
        Text(title, style = soft(22), color = SoftText, textAlign = TextAlign.Center)
        Text(message, style = SoftType.small, color = SoftSecondary, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            SoftLink(actionLabel, onAction, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Inline error line under a form. */
@Composable
fun SoftError(message: String?, modifier: Modifier = Modifier) {
    if (message != null) {
        Text(message, style = SoftType.caption, color = SoftRed, modifier = modifier.fillMaxWidth().padding(horizontal = 6.dp))
    }
}

/** Centred spinner in the ink colour. */
@Composable
fun SoftLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = SoftInk, strokeWidth = 2.dp)
    }
}

/** Small ink pill action used inside rows ("Accept", "Request"). */
@Composable
fun SoftSmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true) {
    Text(
        text,
        style = soft(13, FontWeight.Normal),
        color = if (primary) Color.White else SoftText,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .clip(CircleShape)
            .background(if (primary) SoftInk else SoftTile)
            .softClick(enabled, onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

/** Thin hairline. */
@Composable
fun SoftDividerLine(modifier: Modifier = Modifier) {
    Box(modifier.height(1.dp).background(com.cabin.app.ui.theme.SoftDivider))
}

/** Chevron used at the end of tappable rows. */
@Composable
fun SoftChevron() {
    Icon(
        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = SoftMuted,
        modifier = Modifier.size(20.dp).padding(end = 2.dp),
    )
}

/** Accessible label wrapper for icon-only controls. */
fun Modifier.describe(text: String) = semantics { contentDescription = text }
