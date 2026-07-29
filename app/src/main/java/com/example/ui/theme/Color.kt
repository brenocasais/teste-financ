package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Modo Claro Tokens
val LightBackground = Color(0xFFFAFAFB)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF8FAF9)
val LightOnSurface = Color(0xFF111827)
val LightOnSurfaceVariant = Color(0xFF6B7280)
val LightOutlineVariant = Color(0xFFECEFF1)
val LightGreen = Color(0xFF22A45D)
val LightRed = Color(0xFFEF4444)
val LightOrange = Color(0xFFF59E0B)
val LightGrayNeutral = Color(0xFF9CA3AF)

// Modo Escuro Tokens
val DarkBackground = Color(0xFF0D1214)
val DarkSurface = Color(0xFF172021)
val DarkSurfaceVariant = Color(0xFF1C2526)
val DarkOnSurface = Color(0xFFF5F7F7)
val DarkOnSurfaceVariant = Color(0xFFA9B1B1)
val DarkOutlineVariant = Color(0xFF263233)
val DarkGreen = Color(0xFF39D47A)
val DarkRed = Color(0xFFFF4D55)
val DarkOrange = Color(0xFFFF9F1C)
val DarkGrayNeutral = Color(0xFF6B7280)

// Semantic Financial Named Tokens (Mandatory: No blue or purple for financial health)
val FinanceSuccessLight = LightGreen
val FinanceWarningLight = LightOrange
val FinanceDangerLight = LightRed
val FinanceNeutralLight = LightGrayNeutral

val FinanceSuccessDark = DarkGreen
val FinanceWarningDark = DarkOrange
val FinanceDangerDark = DarkRed
val FinanceNeutralDark = DarkGrayNeutral

@Immutable
data class FinanceColorScheme(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val neutral: Color
)

val LightFinanceColorScheme = FinanceColorScheme(
    success = FinanceSuccessLight,
    warning = FinanceWarningLight,
    danger = FinanceDangerLight,
    neutral = FinanceNeutralLight
)

val DarkFinanceColorScheme = FinanceColorScheme(
    success = FinanceSuccessDark,
    warning = FinanceWarningDark,
    danger = FinanceDangerDark,
    neutral = FinanceNeutralDark
)

val LocalFinanceColors = staticCompositionLocalOf { LightFinanceColorScheme }

// Backwards compatibility for existing code references
val EmeraldGreen = LightGreen
val MintGreen = Color(0xFF4DB6AC)
val SlateGrey = LightOnSurfaceVariant
val SlateLight = Color(0xFF90A4AE)

val EmeraldGreenDark = DarkGreen
val MintGreenDark = Color(0xFF004D40)
val SlateGreyDark = DarkSurface
val SlateBackgroundDark = DarkBackground

val IncomeGreen = LightGreen
val ExpenseRed = LightRed
val CardBackgroundLight = LightSurface
val CardBackgroundDark = DarkSurface

