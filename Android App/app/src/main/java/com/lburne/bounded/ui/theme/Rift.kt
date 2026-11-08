package com.lburne.bounded.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * "Gilded Rift": the card frame as the design system. The cards' black backing and gold
 * filigree are the base; gold means "yours" and "act"; domain colour (see CardStyle.kt)
 * means identity. Colours were sampled from the card art.
 */
object Rift {
    val Ink = Color(0xFF0C0A09)
    val Surface = Color(0xFF16120F)
    val Raised = Color(0xFF211A14)
    val Line = Color(0xFF3B3024)
    val Track = Color(0xFF2A221A)
    /** The navigation bar, a shade above the ink ground. */
    val BarGround = Color(0xFF120E0B)
    val Gold = Color(0xFFD3AC49)
    val GoldDeep = Color(0xFF8A6A22)
    val GoldWash = Color(0xFF3A2E14)
    val OnGold = Color(0xFF1A1206)
    val Ivory = Color(0xFFF4EEE3)
    val Parchment = Color(0xFFD9D1C4)
    val Muted = Color(0xFFA69A88)
    /** Text on the ivory rules panel. */
    val InkText = Color(0xFF1F1A14)
    val InkMuted = Color(0xFF5E5A55)
    /** Banned in Standard, and the softer warning for a ban in 2v2 only. */
    val Banned = Color(0xFFC62828)
    val Banned2v2 = Color(0xFFB45309)
}

/**
 * Condensed type for labels, numbers and type lines, the way the cards print "SIGNATURE
 * GEAR · RIVEN". Roboto Condensed ships with Android, so nothing is downloaded.
 */
val Condensed = FontFamily(Typeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)))

/** Italic condensed capitals for section labels. Pass text already upper-cased. */
val RiftLabel = TextStyle(
    fontFamily = Condensed,
    fontWeight = FontWeight.Bold,
    fontStyle = FontStyle.Italic,
    fontSize = 14.sp,
    letterSpacing = 0.12.em
)

/** Condensed numerals: counts, costs, progress. */
val RiftNumber = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold)

/** The card frame's cut corners, for buttons and bands. */
val ChamferShape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
val ChamferSmall = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
val BandShape = CutCornerShape(topStart = 10.dp)
val TabShape = CutCornerShape(topStart = 6.dp)
