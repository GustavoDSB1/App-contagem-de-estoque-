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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import com.estoque.contagem.data.PhotoChange
import com.estoque.contagem.data.Product
import com.estoque.contagem.ui.components.ConfirmDialog
import com.estoque.contagem.ui.components.ProductAvatar
import com.estoque.contagem.ui.components.ProductFormDialog
import com.estoque.contagem.ui.formatQty
import com.estoque.contagem.ui.formatWhen
import com.estoque.contagem.ui.onlyDigits
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
    onAdd: (Long) -> Unit,
    onSetTotal: (Long) -> Unit,
    onEdit: (name: String, emoji: String, priceCents: Long, photo: PhotoChange) -> Boolean,
    onExcludeFromExcel: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val color = productColor(product.colorIndex)
    var correcting by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showEdit by rememberSaveable { mutableStateOf(false) }
    val value = input.toLongOrNull()
    val canSave = if (correcting) value != null else (value ?: 0L) > 0L
    val save = {
        if (canSave && value != null) {
            if (correcting) onSetTotal(value) else onAdd(value)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        // Faixa atrás da barra de status, na cor do produto
        Spacer(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(color)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Cabeçalho colorido com o produto e a quantidade atual
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(Brush.verticalGradient(listOf(color, lerp(color, Color.Black, 0.25f))))
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { showEdit = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar produto", tint = Color.White)
                        }
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Excluir produto", tint = Color.White)
                        }
                    }
                    ProductAvatar(product = product, size = 72.dp, onDark = true)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        product.name,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Text(
                        if (product.priceCents > 0) "💲 ${Money.formatWithSymbol(product.priceCents)}  ✏️"
                        else "💲 Definir valor",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = if (product.priceCents > 0) 0.15f else 0.3f))
                            .clickable { showEdit = true }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    // Lista de exclusão da planilha Excel
                    Text(
                        if (product.excludeFromExcel) "🚫 Fora da planilha Excel" else "📊 Vai para a planilha Excel",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (product.excludeFromExcel) Color.Black.copy(alpha = 0.3f)
                                else Color.White.copy(alpha = 0.15f)
                            )
                            .clickable { onExcludeFromExcel(!product.excludeFromExcel) }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Quantidade atual", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                    Text(
                        formatQty(product.quantity),
                        color = Color.White,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 56.sp
                    )
                }
            }

            Column(modifier = Modifier.padding(20.dp)) {
                // Escolha: somar ou corrigir
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModeOption(
                        emoji = "➕",
                        title = "Somar",
                        subtitle = "Adicionar ao que já tem",
                        selected = !correcting,
                        color = color,
                        modifier = Modifier.weight(1f),
                        onClick = { correcting = false }
                    )
                    ModeOption(
                        emoji = "✏️",
                        title = "Corrigir",
                        subtitle = "Trocar o total",
                        selected = correcting,
                        color = color,
                        modifier = Modifier.weight(1f),
                        onClick = { correcting = true }
                    )
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    if (correcting) "Qual é a quantidade correta?" else "Quantas unidades você contou agora?",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.onlyDigits() },
                    placeholder = {
                        Text("0", fontSize = 40.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = color,
                        cursorColor = color,
                        unfocusedContainerColor = Color.White,
                        focusedContainerColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                // Botões rápidos
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(1L, 5L, 10L, 50L).forEach { n ->
                        OutlinedButton(
                            onClick = { input = ((input.toLongOrNull() ?: 0L) + n).coerceAtMost(9_999_999L).toString() },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) { Text("+$n", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color) }
                    }
                }

                // Prévia do resultado
                if (value != null && canSave) {
                    Spacer(Modifier.height(16.dp))
                    val preview = if (correcting) {
                        "${formatQty(product.quantity)}  ➜  ${formatQty(value)}"
                    } else {
                        "${formatQty(product.quantity)} + ${formatQty(value)} = ${formatQty(product.quantity + value)}"
                    }
                    Text(
                        preview,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (correcting) WarningText else SuccessText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (correcting) WarningBg else SuccessBg)
                            .padding(14.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = save,
                    enabled = canSave,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                ) {
                    Text(
                        if (correcting) "✅ Salvar novo total" else "✅ Somar e salvar",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(28.dp))
                Text("🕘 Lançamentos desta contagem", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (product.entries.isEmpty()) {
                    Text("Nenhum lançamento ainda.", color = TextSecondary)
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                    ) {
                        product.entries.asReversed().take(20).forEachIndexed { i, e ->
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
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
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
                Spacer(Modifier.navigationBarsPadding().height(16.dp))
            }
        }
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
            askInitialQuantity = false,
            onDismiss = { showEdit = false },
            onSave = { name, emoji, _, price, photo ->
                val ok = onEdit(name, emoji, price, photo)
                if (ok) showEdit = false
                ok
            }
        )
    }
}

@Composable
private fun ModeOption(
    emoji: String,
    title: String,
    subtitle: String,
    selected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) color.copy(alpha = 0.12f) else Color.White)
            .border(2.dp, if (selected) color else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp)
    ) {
        Text(emoji, fontSize = 26.sp)
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (selected) color else Color.Unspecified)
        Text(subtitle, fontSize = 12.sp, color = TextSecondary, textAlign = TextAlign.Center)
    }
}
