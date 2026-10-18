package com.estoque.contagem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.estoque.contagem.consumo.Money
import com.estoque.contagem.ui.ImportPreview
import com.estoque.contagem.ui.formatQty
import com.estoque.contagem.ui.theme.TextSecondary

@Composable
fun ImportDialog(
    preview: ImportPreview,
    onDismiss: () -> Unit,
    onConfirm: (withQuantities: Boolean) -> Unit
) {
    var withQuantities by remember { mutableStateOf(preview.hasQuantities) }
    val count = preview.newProducts.size

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text(if (count > 0) "📥" else "🤔", fontSize = 44.sp) },
        title = {
            Text(
                if (count > 0) "$count produto(s) encontrados" else "Nada novo para importar",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (preview.duplicates > 0) {
                    Text(
                        "${preview.duplicates} já estão cadastrados no app e serão ignorados.",
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (count > 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp)
                    ) {
                        preview.newProducts.take(8).forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                                Text(item.emoji, fontSize = 18.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    item.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (withQuantities && item.quantity > 0) {
                                    Text(formatQty(item.quantity), fontWeight = FontWeight.Bold)
                                }
                                if (item.priceCents > 0) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(Money.formatWithSymbol(item.priceCents), color = TextSecondary, fontSize = 13.sp)
                                }
                            }
                        }
                        if (count > 8) {
                            Text("… e mais ${count - 8}", color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    if (preview.hasQuantities) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { withQuantities = !withQuantities }
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Trazer as quantidades", fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (withQuantities) "Continua a contagem de onde parou"
                                    else "Produtos entram com quantidade zero",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            Switch(checked = withQuantities, onCheckedChange = { withQuantities = it })
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (count > 0) {
                Button(onClick = { onConfirm(withQuantities) }, shape = RoundedCornerShape(12.dp)) {
                    Text("Importar", fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = onDismiss) { Text("OK") }
            }
        },
        dismissButton = if (count > 0) {
            { TextButton(onClick = onDismiss) { Text("Cancelar") } }
        } else null
    )
}
