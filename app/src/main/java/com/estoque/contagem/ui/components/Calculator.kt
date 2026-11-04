package com.estoque.contagem.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.estoque.contagem.data.Calc
import com.estoque.contagem.data.Qty
import com.estoque.contagem.ui.theme.CardColor
import com.estoque.contagem.ui.theme.DangerRed
import com.estoque.contagem.ui.theme.TextSecondary

/** Visor da calculadora: a conta digitada e, embaixo, o resultado. */
@Composable
fun CalculatorDisplay(
    expression: String,
    result: Double?,
    unitCode: String,
    error: String?,
    color: Color,
    onClear: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardColor)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "C",
                color = DangerRed,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = expression.isNotEmpty(), onClick = onClear)
                    .alpha(if (expression.isNotEmpty()) 1f else 0.3f)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (expression.isEmpty()) "0" else Calc.pretty(expression),
                fontSize = if (expression.length > 14) 26.sp else 38.sp,
                fontWeight = FontWeight.Bold,
                color = if (expression.isEmpty()) TextSecondary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 40.sp,
                modifier = Modifier.weight(4f, fill = false)
            )
            if (!Calc.hasOperation(expression)) {
                Spacer(Modifier.width(6.dp))
                Text(unitCode, fontSize = 18.sp, color = TextSecondary)
            }
        }
        when {
            error != null -> Text(error, color = DangerRed, fontSize = 13.sp, textAlign = TextAlign.End)
            Calc.hasOperation(expression) && result != null -> Text(
                "= ${Qty.format(result)} $unitCode",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
        }
    }
}

/** Teclado da calculadora: números, + − × ÷, vírgula e apagar. */
@Composable
fun CalculatorPad(
    allowsDecimals: Boolean,
    color: Color,
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val rows = listOf(
        listOf('7', '8', '9', Calc.DIVIDE),
        listOf('4', '5', '6', Calc.TIMES),
        listOf('1', '2', '3', Calc.MINUS),
        listOf(',', '0', '⌫', Calc.PLUS)
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    val isOp = Calc.isOperator(key)
                    val enabled = key != ',' || allowsDecimals
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                when {
                                    isOp -> color.copy(alpha = 0.15f)
                                    key == '⌫' -> MaterialTheme.colorScheme.surfaceVariant
                                    else -> CardColor
                                }
                            )
                            .clickable(enabled = enabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (key == '⌫') onBackspace() else onKey(key)
                            }
                            .alpha(if (enabled) 1f else 0.25f)
                    ) {
                        Text(
                            key.toString(),
                            fontSize = if (isOp) 28.sp else 24.sp,
                            fontWeight = if (isOp) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (isOp) color else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
