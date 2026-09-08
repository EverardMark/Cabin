package com.cabin.app.ui.theme

import androidx.compose.ui.graphics.Color

// Cabin Soft palette — the same tokens the iOS app uses (Support/Theme.swift).

/** Near-black for primary buttons, the app mark and the verification tag. */
val SoftInk = Color(0xFF111514)
/** Body text. */
val SoftText = Color(0xFF171C1A)
/** Icons and subdued headings. */
val SoftTextSoft = Color(0xFF2A302D)
/** Tile labels and unselected segment text. */
val SoftLabel = Color(0xFF3D453F)
/** Secondary copy. */
val SoftSecondary = Color(0xFF5D665F)
/** Placeholders and map labels. */
val SoftMuted = Color(0xFF8D968F)

/** Tinted tile fill. */
val SoftTile = Color(0xFFE3EEE7)
/** Very pale row fill. */
val SoftPale = Color(0xFFF4F6F3)
/** Pressed tint on white. */
val SoftHover = Color(0xFFEEF3EF)
/** Avatar fill. */
val SoftAvatar = Color(0xFFCFE0D6)
/** Photo placeholder gradient end. */
val SoftPinEnd = Color(0xFFA9C4B8)

/** Background gradient stops. */
val SoftBackgroundTop = Color(0xFFDCEBDF)
val SoftBackgroundMid = Color(0xFFE9F0E9)
val SoftBackgroundBottom = Color(0xFFF4EFE3)

/** Attention accent: unread dots and "N new" tags. */
val SoftAccent = Color(0xFFF0A73A)
/** Warning copy. */
val SoftClay = Color(0xFFB8652A)
/** Online / positive dot. */
val SoftGreen = Color(0xFF4FC36A)
/** Errors and the favourite heart. */
val SoftRed = Color(0xFFE0533F)

/** Hairline divider. */
val SoftDivider = SoftText.copy(alpha = 0.15f)
/** Outlined tag border. */
val SoftOutline = SoftText.copy(alpha = 0.25f)

/** Shadow tints (the design's rgba shadows). */
val SoftShadowCard = Color(0xFF283C37)
val SoftShadowTab = Color(0xFF1E322D)

// Legacy names still referenced by the create-listing form.
val Forest = SoftInk
val Clay = SoftClay
val Ink = SoftText
