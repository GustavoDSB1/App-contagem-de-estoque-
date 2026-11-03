package com.estoque.contagem.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

val Indigo = Color(0xFF4F46E5)
val IndigoDark = Color(0xFF3730A3)
val Violet = Color(0xFF7C3AED)
val ExcelGreen = Color(0xFF16A34A)
val SuccessBg = Color(0xFFDCFCE7)
val SuccessText = Color(0xFF15803D)
val WarningBg = Color(0xFFFEF3C7)
val WarningText = Color(0xFFB45309)
val DangerRed = Color(0xFFDC2626)
val AppBackground = Color(0xFFF4F6FB)
val TextPrimary = Color(0xFF111827)

/** Texto secundário (cinza): acompanha o tema claro/escuro. */
val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant

/** Fundo de cartões, campos e barras: branco no tema claro, cinza-azulado no escuro. */
val CardColor: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface

/** Cores usadas nos ícones dos produtos (uma diferente para cada produto). */
val ProductColors = listOf(
    Color(0xFF6366F1),
    Color(0xFFEC4899),
    Color(0xFFF59E0B),
    Color(0xFF10B981),
    Color(0xFF3B82F6),
    Color(0xFFEF4444),
    Color(0xFF8B5CF6),
    Color(0xFF14B8A6)
)

fun productColor(index: Int): Color = ProductColors[Math.floorMod(index, ProductColors.size)]

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = IndigoDark,
    secondary = ExcelGreen,
    onSecondary = Color.White,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = Color.White,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF6B7280),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFF1F5F9),
    outline = Color(0xFFCBD5E1),
    error = DangerRed
)

@Composable
fun EstoqueTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = Typography(), content = content)
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF4ADE80),
    onSecondary = Color(0xFF052E16),
    background = Color(0xFF0F1220),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF1B2033),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF272D44),
    onSurfaceVariant = Color(0xFF9CA3AF),
    surfaceContainerLowest = Color(0xFF151A2B),
    surfaceContainerLow = Color(0xFF1B2033),
    surfaceContainer = Color(0xFF1B2033),
    surfaceContainerHigh = Color(0xFF222840),
    surfaceContainerHighest = Color(0xFF272D44),
    outline = Color(0xFF4B5563),
    error = Color(0xFFF87171)
)
