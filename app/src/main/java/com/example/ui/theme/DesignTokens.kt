package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object DesignTokens {
    // Raio de card entre 16-22dp (Token: 18.dp)
    val CardCornerRadius = 18.dp
    val CardShape = RoundedCornerShape(CardCornerRadius)

    // Pequenos cantos arredondados (10.dp / 12.dp)
    val SmallCornerRadius = 10.dp
    val SmallShape = RoundedCornerShape(SmallCornerRadius)

    // Formato pílula (Pill Shape)
    val PillShape = RoundedCornerShape(50)

    // Borda de 1dp bem suave usando token outlineVariant
    val BorderWidth = 1.dp
}
