package com.cabin.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.cabin.app.R

/** Outfit (SIL OFL), bundled under res/font. */
val Outfit = FontFamily(
    Font(R.font.outfit_light, FontWeight.Light),
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
)

/**
 * Global multiplier on every design size, mirroring the iOS `softScale`. The
 * hand-off's sizes felt large on a phone, so the whole scale is tightened here.
 */
const val SOFT_SCALE = 0.88f

/** Outfit at a design size (times [SOFT_SCALE]). Light is the design's default weight. */
fun soft(size: Int, weight: FontWeight = FontWeight.Light, letterSpacing: TextUnit = TextUnit.Unspecified): TextStyle =
    TextStyle(
        fontFamily = Outfit,
        fontWeight = weight,
        fontSize = (size * SOFT_SCALE).sp,
        lineHeight = (size * SOFT_SCALE * 1.25f).sp,
        letterSpacing = letterSpacing,
        color = SoftText,
    )

/** Named sizes that recur across the design. */
object SoftType {
    val display = soft(44, letterSpacing = (-0.9).sp)      // welcome headline
    val title = soft(36, letterSpacing = (-0.7).sp)        // "Create your account"
    val heading = soft(28, letterSpacing = (-0.3).sp)      // hero card title
    val screenTitle = soft(24)                             // centred header title
    val cardTitle = soft(20)                               // secondary card title
    val body = soft(17, FontWeight.Normal)                 // row title
    val bodyLight = soft(16)                               // bubbles, copy
    val small = soft(15)                                   // .sm
    val caption = soft(14)                                 // row meta
    val footnote = soft(13)                                // hints
    val tag = soft(12, FontWeight.Normal)                  // tag text
    val button = soft(15, FontWeight.Normal)
    val buttonLarge = soft(17, FontWeight.Normal)
}
