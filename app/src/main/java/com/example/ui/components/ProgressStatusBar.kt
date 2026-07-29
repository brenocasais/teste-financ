package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.financeColors

@Composable
fun ProgressStatusBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    customColor: Color? = null
) {
    val financeColors = MaterialTheme.financeColors

    val progressColor = customColor ?: when {
        progress <= 0f -> financeColors.neutral
        progress <= 0.8f -> financeColors.success
        progress <= 1.0f -> financeColors.warning
        else -> financeColors.danger
    }

    val normalizedProgress = progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(trackColor)
    ) {
        if (normalizedProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = normalizedProgress)
                    .clip(RoundedCornerShape(height / 2))
                    .background(progressColor)
            )
        }
    }
}
