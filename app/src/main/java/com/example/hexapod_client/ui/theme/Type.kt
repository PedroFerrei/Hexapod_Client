package com.example.hexapod_client.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val GameFont = FontFamily.SansSerif

val Typography = Typography(
    displayLarge  = TextStyle(fontFamily = GameFont, fontWeight = FontWeight.Bold,     fontSize = 32.sp),
    displayMedium = TextStyle(fontFamily = GameFont, fontWeight = FontWeight.Bold,     fontSize = 26.sp),
    headlineLarge = TextStyle(fontFamily = GameFont, fontWeight = FontWeight.Bold,     fontSize = 22.sp),
    headlineMedium= TextStyle(fontFamily = GameFont, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleLarge    = TextStyle(fontFamily = GameFont, fontWeight = FontWeight.Bold,     fontSize = 20.sp, letterSpacing = 1.sp),
    titleMedium   = TextStyle(fontFamily = GameFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.8.sp),
    bodyLarge     = TextStyle(fontFamily = GameFont, fontWeight = FontWeight.Normal,   fontSize = 16.sp),
    bodyMedium    = TextStyle(fontFamily = GameFont, fontWeight = FontWeight.Normal,   fontSize = 14.sp),
    labelLarge    = TextStyle(fontFamily = GameFont, fontWeight = FontWeight.Bold,     fontSize = 13.sp, letterSpacing = 0.5.sp),
    labelMedium   = TextStyle(fontFamily = GameFont, fontWeight = FontWeight.Medium,   fontSize = 11.sp, letterSpacing = 0.3.sp),
    labelSmall    = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 10.sp),
)
