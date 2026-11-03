package com.estoque.contagem.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.estoque.contagem.consumo.ConsumptionMessage
import com.estoque.contagem.consumo.Days
import com.estoque.contagem.consumo.MessageTemplate
import com.estoque.contagem.data.Consumption
import com.estoque.contagem.data.PersonType

private val WhatsAppBubble = Color(0xFFDCF8C6)

/** Criar ou editar um grupo de pessoas (nome no singular + ícone). */
@Composable
fun GroupDialog(
    initial: PersonType?,
    onDismiss: () -> Unit,
    onSave: (label: String, emoji: String) -> Boolean
) {
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: PersonType.EMOJIS.first()) }
    var duplicate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text(emoji, fontSize = 40.sp) },
        title = { Text(if (initial == null) "Novo grupo" else "Editar grupo", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(30); duplicate = false },
                    label = { Text("Nome do grupo (no singular)") },
                    placeholder = { Text("Ex.: cliente, mesa, setor") },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = {
                        Text(if (duplicate) "Já existe um grupo com esse nome" else "Aparece na mensagem: \"Consumo ${label.ifBlank { "cliente" }} 02/10\"")
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("Ícone", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                PersonType.EMOJIS.chunked(6).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        row.forEach { e ->
                            val selected = e == emoji
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                    .clickable { emoji = e }
                            ) { Text(e, fontSize = 20.sp) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = label.isNotBlank(),
                onClick = { if (!onSave(label.trim(), emoji)) duplicate = true },
                shape = RoundedCornerShape(12.dp)
            ) { Text("Salvar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Exemplo usado na prévia da mensagem. */
private fun sampleEntries(): List<Consumption> {
    val day = Days.startOfDay(System.currentTimeMillis())
    return listOf(
        Consumption("1", "Ricardo", "x", "p1", "coca 600", 1, 900, day, day + 1),
        Consumption("2", "Ricardo", "x", "p2", "pão de queijo", 2, 350, day, day + 2),
        Consumption("3", "Leonardo", "x", "p3", "guaraná lata", 1, 700, day, day + 3)
    )
}

/** Editor do modelo da mensagem do WhatsApp, com prévia ao vivo. */
@Composable
fun MessageTemplateDialog(
    initial: MessageTemplate,
    sampleType: PersonType,
    companyName: String,
    onDismiss: () -> Unit,
    onSave: (MessageTemplate) -> Unit
) {
    var header by remember { mutableStateOf(initial.header) }
    var line by remember { mutableStateOf(initial.line) }
    var blank by remember { mutableStateOf(initial.blankLineBetweenPeople) }
    var footer by remember { mutableStateOf(initial.footer) }
    val current = MessageTemplate(header, line.ifBlank { MessageTemplate.DEFAULT_LINE }, blank, footer)
    val preview = ConsumptionMessage.build(
        sampleType, Days.startOfDay(System.currentTimeMillis()), sampleEntries(), current, companyName
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("💬 Mensagem do WhatsApp", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = header,
                    onValueChange = { header = it.take(120) },
                    label = { Text("Primeira linha") },
                    supportingText = { Text("Campos: ${MessageTemplate.HEADER_FIELDS.joinToString(" ")}") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = line,
                    onValueChange = { line = it.take(120) },
                    label = { Text("Cada item") },
                    supportingText = { Text("Campos: ${MessageTemplate.LINE_FIELDS.joinToString(" ")}") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = footer,
                    onValueChange = { footer = it.take(120) },
                    label = { Text("Última linha (opcional)") },
                    placeholder = { Text("Ex.: Total: {total}") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { blank = !blank }
                        .padding(vertical = 6.dp)
                ) {
                    Text("Linha em branco entre pessoas", modifier = Modifier.weight(1f))
                    Switch(checked = blank, onCheckedChange = { blank = it })
                }
                Text("Prévia (exemplo)", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    preview,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFF111827),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                        .background(WhatsAppBubble)
                        .padding(12.dp)
                )
                TextButton(
                    onClick = {
                        header = MessageTemplate.DEFAULT_HEADER
                        line = MessageTemplate.DEFAULT_LINE
                        blank = true
                        footer = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Restaurar modelo padrão") }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(current) }, shape = RoundedCornerShape(12.dp)) {
                Text("Salvar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
