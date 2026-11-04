package com.estoque.contagem.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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

/**
 * Visor compacto: a conta com o resultado ("7 × 12 + 5 = 89") e, embaixo,
 * a prévia do total ou o aviso de erro.
 */
@Composable
fun CalculatorDisplay(
    expression: String,
    result: Double?,
    unitCode: String,
    error: String?,
    preview: String?,
    previewColor: Color,
    color: Color,
    onClear: () -> Unit
) {
    val hasOp = Calc.hasOperation(expression)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardColor)
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 8.dp)
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
            val shown = when {
                expression.isEmpty() -> "0"
                hasOp && result != null && error == null -> "${Calc.pretty(expression)} = ${Qty.format(result)}"
                else -> Calc.pretty(expression)
            }
            Text(
                shown,
                fontSize = when {
                    shown.length > 22 -> 20.sp
                    shown.length > 14 -> 26.sp
                    else -> 34.sp
                },
                fontWeight = FontWeight.Bold,
                color = if (expression.isEmpty()) TextSecondary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(6f, fill = false)
            )
            Spacer(Modifier.width(6.dp))
            Text(unitCode, fontSize = 16.sp, color = TextSecondary)
        }
        Text(
            when {
                error != null -> error
                preview != null -> preview
                else -> "Digite a quantidade ou faça a conta (ex.: 7 × 12 + 5)"
            },
            color = when {
                error != null -> DangerRed
                preview != null -> previewColor
                else -> TextSecondary
            },
            fontSize = if (preview != null && error == null) 17.sp else 13.sp,
            fontWeight = if (preview != null && error == null) FontWeight.ExtraBold else FontWeight.Normal,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Teclado da calculadora: números, + − × ÷, vírgula e apagar. */
@Composable
fun CalculatorPad(
    allowsDecimals: Boolean,
    color: Color,
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    /** Com weight(1f), o teclado ocupa a altura que sobrar na tela. */
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val rows = listOf(
        listOf('7', '8', '9', Calc.DIVIDE),
        listOf('4', '5', '6', Calc.TIMES),
        listOf('1', '2', '3', Calc.MINUS),
        listOf(',', '0', '⌫', Calc.PLUS)
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                row.forEach { key ->
                    val isOp = Calc.isOperator(key)
                    val enabled = key != ',' || allowsDecimals
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
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
