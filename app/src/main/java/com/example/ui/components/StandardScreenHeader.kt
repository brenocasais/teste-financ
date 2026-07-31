package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StandardScreenHeader(
    title: String,
    periodString: String,
    onMonthClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val cardBgColor = if (isDark) Color(0xFF1C2427) else Color(0xFFFFFFFF)
    val cardBorderColor = if (isDark) Color(0xFF283438) else Color(0xFFE5E7EC)
    val primaryTextColor = if (isDark) Color(0xFFF5F7F8) else Color(0xFF111827)
    val secondaryTextColor = if (isDark) Color(0xFF9FA9AB) else Color(0xFF6B7280)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp, start = 4.dp, end = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = primaryTextColor
        )

        // Seletor compacto de mês
        Surface(
            onClick = onMonthClick,
            shape = RoundedCornerShape(10.dp),
            color = cardBgColor,
            border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = periodString,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Selecionar Mês",
                    tint = secondaryTextColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
