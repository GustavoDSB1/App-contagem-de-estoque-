package com.estoque.contagem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.estoque.contagem.data.Person
import com.estoque.contagem.data.PersonType
import com.estoque.contagem.data.Product
import com.estoque.contagem.excel.StockReport
import com.estoque.contagem.ui.normalizedForSearch
import com.estoque.contagem.ui.components.ProductAvatar
import com.estoque.contagem.ui.BackupExportResult
import com.estoque.contagem.ui.BackupPreview
import com.estoque.contagem.ui.UiState
import com.estoque.contagem.ui.components.ConfirmDialog
import com.estoque.contagem.ui.components.PersonFormDialog
import com.estoque.contagem.ui.theme.DangerRed
import com.estoque.contagem.ui.theme.ExcelGreen
import com.estoque.contagem.ui.theme.Indigo
import com.estoque.contagem.ui.theme.TextSecondary

private val Teal = Color(0xFF0D9488)
private val TealDark = Color(0xFF0F766E)

@Composable
fun MoreScreen(
    state: UiState,
    snackbarHostState: SnackbarHostState,
    busy: Boolean,
    onAddPerson: (name: String, type: PersonType) -> Boolean,
    onDeletePerson: (Person) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onImportSpreadsheet: () -> Unit,
    onSetExcludedFromExcel: (Set<String>) -> Unit
) {
    var adding by remember { mutableStateOf<PersonType?>(null) }
    var choosingExcluded by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Person?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(Brush.linearGradient(listOf(Teal, Indigo)))
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp)
            ) {
                Column {
                    Text("⚙️ Mais opções", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Cadastros, backup e importação",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PersonType.entries.forEach { type ->
                    PeopleCard(
                        type = type,
                        people = state.people.filter { it.type == type }.sortedBy { it.name.lowercase() },
                        onAdd = { adding = type },
                        onDelete = { deleting = it }
                    )
                }

                val excluded = state.products.filter { it.excludeFromExcel }
                SectionCard(
                    emoji = "🚫",
                    title = "Fora da planilha Excel",
                    text = "Produtos marcados aqui continuam no app (contagem e consumo), " +
                        "mas não aparecem na planilha de contagem."
                ) {
                    if (excluded.isEmpty()) {
                        Text("Nenhum produto fora da planilha.", color = TextSecondary)
                    } else {
                        Text(
                            excluded.sortedBy { it.name.lowercase() }.joinToString(" • ") { it.name },
                            fontSize = 14.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { choosingExcluded = true },
                        enabled = state.products.isNotEmpty(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text(
                            if (excluded.isEmpty()) "Escolher produtos" else "Alterar lista (${excluded.size})",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                SectionCard(
                    emoji = "💾",
                    title = "Backup para outro celular",
                    text = "Leva os produtos, preços, ícones e fotos, funcionários e diaristas. " +
                        "Não leva a contagem nem as baixas de consumo."
                ) {
                    Button(
                        onClick = onExportBackup,
                        enabled = !busy,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                        }
                        Text("📤 Exportar backup", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onImportBackup,
                        enabled = !busy,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) { Text("📥 Importar backup", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TealDark) }
                }

                SectionCard(
                    emoji = "📊",
                    title = "Planilha Excel",
                    text = "Importa produtos de uma planilha (a gerada pelo app ou qualquer uma com a coluna \"Produto\")."
                ) {
                    OutlinedButton(
                        onClick = onImportSpreadsheet,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) { Text("📥 Importar produtos da planilha", fontWeight = FontWeight.Bold, color = ExcelGreen) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    adding?.let { type ->
        PersonFormDialog(
            type = type,
            onDismiss = { adding = null },
            onSave = { name ->
                val ok = onAddPerson(name, type)
                if (ok) adding = null
                ok
            }
        )
    }

    if (choosingExcluded) {
        ExcludeFromExcelDialog(
            products = state.products,
            onConfirm = { ids ->
                onSetExcludedFromExcel(ids)
                choosingExcluded = false
            },
            onDismiss = { choosingExcluded = false }
        )
    }

    deleting?.let { person ->
        ConfirmDialog(
            emoji = "🗑️",
            title = "Remover ${person.name}?",
            message = "Sai da lista de ${person.type.label}s. As baixas já feitas continuam salvas.",
            confirmText = "Remover",
            confirmColor = DangerRed,
            onConfirm = { onDeletePerson(person); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun SectionCard(
    emoji: String,
    title: String,
    text: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(16.dp)
    ) {
        Text("$emoji $title", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(text, color = TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun PeopleCard(
    type: PersonType,
    people: List<Person>,
    onAdd: () -> Unit,
    onDelete: (Person) -> Unit
) {
    val title = if (type == PersonType.EMPLOYEE) "Funcionários" else "Diaristas"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${type.emoji} $title (${people.size})",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onAdd,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo)
            ) { Text("➕ Cadastrar", fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(8.dp))
        if (people.isEmpty()) {
            Text("Ninguém cadastrado ainda.", color = TextSecondary)
        }
        people.forEach { person ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Indigo.copy(alpha = 0.12f))
                ) {
                    Text(person.name.take(1).uppercase(), color = Indigo, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    person.name,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onDelete(person) }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Remover ${person.name}", tint = DangerRed.copy(alpha = 0.7f))
                }
            }
        }
    }
}

/** Lista de exclusão: marque os produtos que NÃO vão para a planilha. */
@Composable
private fun ExcludeFromExcelDialog(
    products: List<Product>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(products.filter { it.excludeFromExcel }.map { it.id }.toSet()) }
    var query by remember { mutableStateOf("") }
    val sorted = remember(products) { StockReport.sortByName(products) }
    val shown = remember(sorted, query) {
        val q = query.trim().normalizedForSearch()
        if (q.isEmpty()) sorted else sorted.filter { it.name.normalizedForSearch().contains(q) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🚫 Fora da planilha", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Marque os produtos que não devem ir para a planilha Excel.", color = TextSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar produto...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    shown.forEach { p ->
                        val checked = p.id in selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selected = if (checked) selected - p.id else selected + p.id }
                                .padding(vertical = 4.dp)
                        ) {
                            ProductAvatar(product = p, size = 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                p.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { selected = if (it) selected + p.id else selected - p.id }
                            )
                        }
                    }
                    if (shown.isEmpty()) {
                        Text("Nenhum produto encontrado.", color = TextSecondary, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selected) }, shape = RoundedCornerShape(12.dp)) {
                Text("Salvar (${selected.size})", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Backup gerado: mostra onde ficou e permite enviar. */
@Composable
fun BackupDoneDialog(result: BackupExportResult, onShare: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text("💾", fontSize = 44.sp) },
        title = { Text("Backup pronto!", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "${result.products} produto(s) e ${result.people} pessoa(s) no arquivo:",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    result.fileName,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    (if (result.savedToDownloads) "📁 Uma cópia está na pasta Downloads.\n" else "") +
                        "No outro celular: abra o app → ⚙️ Mais → Importar backup.",
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onShare,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) { Text("📤 Enviar (WhatsApp, Drive...)", fontWeight = FontWeight.Bold) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

/** Prévia do backup antes de importar. */
@Composable
fun BackupImportDialog(preview: BackupPreview, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val nothing = preview.newProducts == 0 && preview.updatedPrices == 0 && preview.newPeople == 0
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text(if (nothing) "🤔" else "📥", fontSize = 44.sp) },
        title = {
            Text(
                if (nothing) "Nada novo nesse backup" else "Importar backup?",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "O arquivo tem ${preview.data.products.size} produto(s) e ${preview.data.people.size} pessoa(s).",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!nothing) {
                    Spacer(Modifier.height(4.dp))
                    if (preview.newProducts > 0) Text("📦 ${preview.newProducts} produto(s) novo(s)")
                    if (preview.updatedPrices > 0) Text("💲 ${preview.updatedPrices} produto(s) com preço atualizado")
                    if (preview.newPeople > 0) Text("👷 ${preview.newPeople} funcionário(s)/diarista(s) novo(s)")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "A contagem e as baixas deste celular não são alteradas.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            if (nothing) {
                TextButton(onClick = onDismiss) { Text("OK") }
            } else {
                Button(onClick = onConfirm, shape = RoundedCornerShape(12.dp)) {
                    Text("Importar", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = if (nothing) null else {
            { TextButton(onClick = onDismiss) { Text("Cancelar") } }
        }
    )
}
