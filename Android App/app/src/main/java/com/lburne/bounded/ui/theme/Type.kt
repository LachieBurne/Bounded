package com.lburne.bounded.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

/**
 * Material's scale with bolder titles for card names, and condensed labels to match the
 * type lines printed on the cards.
 */
val Typography = Typography(
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.6.sp),
    labelMedium = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.5.sp)
)
