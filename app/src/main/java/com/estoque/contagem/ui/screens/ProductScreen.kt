package com.estoque.contagem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.estoque.contagem.consumo.Money
import com.estoque.contagem.data.Calc
import com.estoque.contagem.data.MeasureUnit
import com.estoque.contagem.data.Qty
import com.estoque.contagem.data.Product
import com.estoque.contagem.ui.components.CalculatorDisplay
import com.estoque.contagem.ui.components.CalculatorPad
import com.estoque.contagem.ui.components.ConfirmDialog
import com.estoque.contagem.ui.components.ProductAvatar
import com.estoque.contagem.ui.components.ProductFormDialog
import com.estoque.contagem.ui.ProductForm
import com.estoque.contagem.ui.formatQty
import com.estoque.contagem.ui.formatWhen
import com.estoque.contagem.ui.theme.CardColor
import com.estoque.contagem.ui.theme.DangerRed
import com.estoque.contagem.ui.theme.SuccessBg
import com.estoque.contagem.ui.theme.SuccessText
import com.estoque.contagem.ui.theme.TextSecondary
import com.estoque.contagem.ui.theme.WarningBg
import com.estoque.contagem.ui.theme.WarningText
import com.estoque.contagem.ui.theme.productColor

@Composable
fun ProductScreen(
    product: Product,
    onBack: () -> Unit,
    onAdd: (Double) -> Unit,
    onSetTotal: (Double) -> Unit,
    onEdit: (ProductForm) -> Boolean,
    /** Categorias já usadas (para sugerir na edição). */
    categories: List<String> = emptyList(),
    barcodeOwner: (String) -> String? = { null },
    onExcludeFromExcel: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val color = productColor(product.colorIndex)
    var correcting by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showEdit by rememberSaveable { mutableStateOf(false) }
    val unit = product.unit
    // A quantidade pode ser uma conta: "7×12+5" = 89
    val raw = Calc.evaluate(input)
    val inputError = when {
        input.isEmpty() -> null
        raw == null -> "Conta inválida"
        raw < 0 -> "O resultado não pode ser negativo"
        !unit.allowsDecimals && raw != Math.rint(raw) -> "Deu ${formatQty(raw)}: ${unit.plural} só aceita número inteiro"
        raw > Qty.MAX -> "Número grande demais"
        else -> null
    }
    val value = if (inputError == null) raw else null
    val canSave = if (correcting) value != null else (value ?: 0.0) > 0.0
    val save = {
        if (canSave && value != null) {
            if (correcting) onSetTotal(value) else onAdd(value)
        }
    }

    var showHistory by rememberSaveable { mutableStateOf(false) }
    // Prévia do que vai acontecer: "12 + 89 = 101 un" ou "12 ➜ 89 un"
    val preview = value?.takeIf { canSave }?.let { v ->
        if (correcting) "${formatQty(product.quantity)} ➜ ${formatQty(v)} ${unit.code}"
        else "${formatQty(product.quantity)} + ${formatQty(v)} = ${formatQty(product.quantity + v)} ${unit.code}"
    }

    // Tudo cabe numa tela só: o teclado ocupa o espaço que sobrar
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ----- Cabeçalho compacto -----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(Brush.verticalGradient(listOf(color, lerp(color, Color.Black, 0.25f))))
                .statusBarsPadding()
                .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                }
                ProductAvatar(product = product, size = 44.dp, onDark = true)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        product.name,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Atual: ${formatQty(product.quantity)} ${unit.label(product.quantity)}",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
                IconButton(onClick = { showHistory = true }) {
                    Text("🕘", fontSize = 20.sp)
                }
                IconButton(onClick = { showEdit = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Editar produto", tint = Color.White)
                }
                IconButton(onClick = { showDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Excluir produto", tint = Color.White)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 48.dp, top = 4.dp)
            ) {
                HeaderChip(
                    if (product.priceCents > 0) "💲 ${Money.formatWithSymbol(product.priceCents)}" else "💲 Definir valor",
                    strong = product.priceCents <= 0,
                    onClick = { showEdit = true }
                )
                HeaderChip(
                    if (product.excludeFromExcel) "🚫 Fora da planilha" else "📊 Na planilha",
                    strong = product.excludeFromExcel,
                    onClick = { onExcludeFromExcel(!product.excludeFromExcel) }
                )
            }
        }

        // ----- Contagem: modo, visor, atalhos, teclado e salvar -----
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            ModeToggle(correcting = correcting, color = color, onChange = { correcting = it })

            CalculatorDisplay(
                expression = input,
                result = raw,
                unitCode = unit.code,
                error = inputError,
                preview = preview,
                previewColor = if (correcting) WarningText else SuccessText,
                color = color,
                onClear = { input = "" }
            )

            // Botões rápidos: somam na conta
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                val steps = if (unit.allowsDecimals) listOf(0.5, 1.0, 5.0, 10.0) else listOf(1.0, 5.0, 10.0, 50.0)
                steps.forEach { n ->
                    OutlinedButton(
                        onClick = { input = Calc.addQuick(input, n) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) { Text("+${formatQty(n)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color) }
                }
            }

            CalculatorPad(
                allowsDecimals = unit.allowsDecimals,
                color = color,
                onKey = { input = Calc.press(input, it, unit.allowsDecimals) },
                onBackspace = { input = input.dropLast(1) },
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = save,
                enabled = canSave,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    if (correcting) "✅ Salvar novo total" else "✅ Somar e salvar",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("🕘 Lançamentos", fontWeight = FontWeight.Bold) },
            text = { HistoryList(product) },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Fechar") } }
        )
    }

    if (showDelete) {
        ConfirmDialog(
            emoji = "🗑️",
            title = "Excluir \"${product.name}\"?",
            message = "O produto e as quantidades contadas serão apagados.",
            confirmText = "Excluir",
            confirmColor = DangerRed,
            onConfirm = { showDelete = false; onDelete() },
            onDismiss = { showDelete = false }
        )
    }

    if (showEdit) {
        ProductFormDialog(
            title = "Editar produto",
            initialName = product.name,
            initialEmoji = product.emoji,
            initialPriceCents = product.priceCents,
            initialImageFile = product.imageFile,
            initialUnit = product.unit,
            initialCategory = product.category,
            initialBarcode = product.barcode,
            categories = categories,
            barcodeOwner = barcodeOwner,
            askInitialQuantity = false,
            onDismiss = { showEdit = false },
            onSave = { form ->
                val ok = onEdit(form)
                if (ok) showEdit = false
                ok
            }
        )
    }
}

/** Chave "Somar | Corrigir" (uma linha só). */
@Composable
private fun ModeToggle(correcting: Boolean, color: Color, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardColor)
            .padding(4.dp)
    ) {
        listOf(false to "➕ Somar", true to "✏️ Corrigir total").forEach { (mode, label) ->
            val selected = correcting == mode
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) color else Color.Transparent)
                    .clickable { onChange(mode) }
            ) {
                Text(
                    label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (selected) Color.White else TextSecondary
                )
            }
        }
    }
}

/** Etiqueta pequena e clicável no cabeçalho (valor, planilha). */
@Composable
private fun HeaderChip(text: String, strong: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (strong) Color.Black.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** Lançamentos desta contagem (mais recentes primeiro). */
@Composable
private fun HistoryList(product: Product) {
    if (product.entries.isEmpty()) {
        Text("Nenhum lançamento ainda.", color = TextSecondary)
        return
    }
    Column(
        modifier = Modifier
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
    ) {
        product.entries.asReversed().take(50).forEachIndexed { i, e ->
            if (i > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (e.isCorrection) "✏️ ${formatQty(e.amount)}" else "+${formatQty(e.amount)}",
                    fontWeight = FontWeight.Bold,
                    color = if (e.isCorrection) WarningText else SuccessText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (e.isCorrection) WarningBg else SuccessBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text("total ${formatQty(e.totalAfter)}", modifier = Modifier.weight(1f))
                Text(formatWhen(e.timestamp), color = TextSecondary, fontSize = 13.sp)
            }
        }
    }
}
