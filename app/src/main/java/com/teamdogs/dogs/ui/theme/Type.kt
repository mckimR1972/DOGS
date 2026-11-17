package com.teamdogs.dogs.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Type scale from design/tokens.json → typography.
// FontFamily.Default renders Korean with the platform's Noto Sans CJK, which matches the Figma file (Noto Sans KR).
// Pretendard is the long-term choice; bundling it is a separate change (APK size + OFL license file).
private val Family = FontFamily.Default

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Family,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
    fontFeatureSettings = "tnum",
)

val Typography = Typography(
    displaySmall = style(30, 38, FontWeight.Bold, -0.02),
    headlineLarge = style(28, 36, FontWeight.Bold, -0.025),
    headlineMedium = style(24, 33, FontWeight.Bold, -0.02),
    headlineSmall = style(26, 34, FontWeight.Bold, -0.025), // Display (home headline)
    titleLarge = style(20, 28, FontWeight.Bold, -0.02),
    titleMedium = style(18, 26, FontWeight.Bold, -0.015),
    titleSmall = style(16, 24, FontWeight.Bold, -0.01),
    bodyLarge = style(16, 24, FontWeight.SemiBold, -0.01),
    bodyMedium = style(14, 22, FontWeight.Medium),
    bodySmall = style(13, 19, FontWeight.Medium),
    labelLarge = style(15, 22, FontWeight.SemiBold),
    labelMedium = style(13, 18, FontWeight.Medium),
    labelSmall = style(12, 16, FontWeight.SemiBold),
)
