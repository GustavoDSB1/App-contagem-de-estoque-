package com.estoque.contagem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.estoque.contagem.consumo.ConsumptionMessage
import com.estoque.contagem.consumo.Days
import com.estoque.contagem.consumo.Money
import com.estoque.contagem.data.Consumption
import com.estoque.contagem.data.Person
import com.estoque.contagem.data.PersonType
import com.estoque.contagem.data.Product
import com.estoque.contagem.excel.StockReport
import com.estoque.contagem.ui.UiState
import com.estoque.contagem.ui.normalizedForSearch
import com.estoque.contagem.ui.components.ConfirmDialog
import com.estoque.contagem.ui.components.MoneyField
import com.estoque.contagem.ui.components.PersonFormDialog
import com.estoque.contagem.ui.components.ProductAvatar
import com.estoque.contagem.ui.theme.DangerRed
import com.estoque.contagem.ui.theme.TextSecondary
import com.estoque.contagem.ui.theme.productColor

private val ConsumoOrange = Color(0xFFF97316)
private val ConsumoPink = Color(0xFFDB2777)
private val WhatsAppGreen = Color(0xFF25D366)
private val WhatsAppBubble = Color(0xFFDCF8C6)

@Composable
fun ConsumptionScreen(
    state: UiState,
    snackbarHostState: SnackbarHostState,
    onAdd: (person: String, type: PersonType, product: Product, quantity: Long, unitPriceCents: Long, dayMillis: Long) -> Consumption?,
    onDelete: (Consumption) -> Unit,
    onSendWhatsApp: (String) -> Unit,
    onCopy: (String) -> Unit,
    onNeedName: () -> Unit,
    onAddPerson: (name: String, type: PersonType) -> Boolean
) {
    val today = Days.startOfDay(System.currentTimeMillis())
    var typeName by rememberSaveable { mutableStateOf(PersonType.EMPLOYEE.name) }
    val type = PersonType.valueOf(typeName)
    var day by rememberSaveable { mutableStateOf(today) }
    var person by rememberSaveable { mutableStateOf("") }
    var picking by remember { mutableStateOf<Product?>(null) }
    var deleting by remember { mutableStateOf<Consumption?>(null) }
    var justAdded by remember { mutableStateOf<Consumption?>(null) }

    val products = remember(state.products) { StockReport.sortByName(state.products) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    val shownProducts = remember(products, search) {
        val q = search.trim().normalizedForSearch()
        if (q.isEmpty()) products else products.filter { it.name.normalizedForSearch().contains(q) }
    }
    val dayEntries = remember(state.consumptions, day, type) {
        state.consumptions.filter { it.dayMillis == day && it.personType == type }.sortedBy { it.timestamp }
    }
    val people = remember(state.people, type) {
        state.people.filter { it.type == type }.sortedBy { it.name.lowercase() }
    }
    var addingPerson by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ConsumptionHeader(
                type = type,
                day = day,
                today = today,
                onTypeChange = { typeName = it.name; person = "" },
                onDayChange = { day = it }
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                fullWidth {
                    PersonSection(
                        type = type,
                        person = person,
                        people = people,
                        onPersonChange = { person = it },
                        onAddPerson = { addingPerson = true }
                    )
                }
                fullWidth {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) { StepTitle("2️⃣", "Toque no produto consumido") }
                        if (products.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    if (searchOpen) search = ""
                                    searchOpen = !searchOpen
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (searchOpen) ConsumoOrange else Color.White,
                                    contentColor = if (searchOpen) Color.White else ConsumoOrange
                                )
                            ) {
                                Icon(
                                    if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                                    contentDescription = if (searchOpen) "Fechar busca" else "Buscar produto"
                                )
                            }
                        }
                    }
                }
                if (searchOpen) {
                    fullWidth {
                        ProductSearchField(query = search, onQueryChange = { search = it })
                    }
                    if (shownProducts.isEmpty()) {
                        fullWidth {
                            Text(
                                "Nenhum produto encontrado para \"$search\"",
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            )
                        }
                    }
                }
                if (products.isEmpty()) {
                    fullWidth {
                        Text(
                            "Nenhum produto cadastrado. Cadastre na aba 📦 Estoque.",
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }
                }
                items(shownProducts, key = { it.id }) { product ->
                    ProductTile(
                        product = product,
                        onClick = {
                            if (person.isBlank()) onNeedName() else picking = product
                        }
                    )
                }
                fullWidth {
                    DaySummary(
                        type = type,
                        day = day,
                        entries = dayEntries,
                        onDelete = { deleting = it },
                        onSendWhatsApp = onSendWhatsApp,
                        onCopy = onCopy
                    )
                }
            }
        }
    }

    picking?.let { product ->
        ConsumeDialog(
            product = product,
            person = person.trim(),
            type = type,
            onDismiss = { picking = null },
            onConfirm = { qty, price ->
                justAdded = onAdd(person.trim(), type, product, qty, price, day)
                search = "" // limpa a busca para a próxima baixa
                picking = null
            }
        )
    }

    justAdded?.let { entry ->
        val allOfDay = state.consumptions
            .filter { it.dayMillis == entry.dayMillis && it.personType == entry.personType }
        AfterConsumeDialog(
            entry = entry,
            dayCount = allOfDay.size,
            onSendThis = {
                onSendWhatsApp(ConsumptionMessage.build(entry.personType, entry.dayMillis, listOf(entry)))
                justAdded = null
            },
            onSendDay = {
                onSendWhatsApp(ConsumptionMessage.build(entry.personType, entry.dayMillis, allOfDay))
                justAdded = null
            },
            onDismiss = { justAdded = null }
        )
    }

    if (addingPerson) {
        PersonFormDialog(
            type = type,
            onDismiss = { addingPerson = false },
            onSave = { name ->
                val ok = onAddPerson(name, type)
                if (ok) {
                    person = name.trim()
                    addingPerson = false
                }
                ok
            }
        )
    }

    deleting?.let { entry ->
        ConfirmDialog(
            emoji = "🗑️",
            title = "Apagar esta baixa?",
            message = ConsumptionMessage.line(entry),
            confirmText = "Apagar",
            confirmColor = DangerRed,
            onConfirm = { onDelete(entry); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun ConsumptionHeader(
    type: PersonType,
    day: Long,
    today: Long,
    onTypeChange: (PersonType) -> Unit,
    onDayChange: (Long) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brush.linearGradient(listOf(ConsumoOrange, ConsumoPink)))
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)
    ) {
        Column {
            Text("🧾 Consumo", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Baixa do que foi consumido. Não altera o estoque.",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PersonType.entries.forEach { t ->
                    val selected = t == type
                    Text(
                        "${t.emoji} ${t.label.replaceFirstChar { it.uppercase() }}",
                        color = if (selected) ConsumoPink else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) Color.White else Color.White.copy(alpha = 0.2f))
                            .clickable { onTypeChange(t) }
                            .padding(vertical = 12.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                IconButton(
                    onClick = { onDayChange(Days.addDays(day, -1)) },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Dia anterior") }
                val label = when (day) {
                    today -> "Hoje"
                    Days.addDays(today, -1) -> "Ontem"
                    else -> null
                }
                Text(
                    "📅 " + (label?.let { "$it, " } ?: "") + Days.shortDate(day),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onDayChange(Days.addDays(day, 1)) },
                    enabled = day < today,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color.White,
                        disabledContentColor = Color.White.copy(alpha = 0.3f)
                    )
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Próximo dia") }
            }
        }
    }
}

@Composable
private fun ProductSearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Buscar produto pelo nome...") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Limpar busca")
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Color.White,
            focusedContainerColor = Color.White,
            focusedBorderColor = ConsumoOrange
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
    )
}

@Composable
private fun StepTitle(number: String, text: String) {
    Text("$number $text", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonSection(
    type: PersonType,
    person: String,
    people: List<Person>,
    onPersonChange: (String) -> Unit,
    onAddPerson: () -> Unit
) {
    Column {
        StepTitle("1️⃣", "Quem consumiu?")
        Spacer(Modifier.height(8.dp))
        if (people.isEmpty()) {
            Text(
                "Nenhum ${type.label} cadastrado. Toque em ➕ para cadastrar.",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            people.forEach { p ->
                val selected = p.name.equals(person.trim(), ignoreCase = true)
                PersonChip(
                    name = p.name,
                    selected = selected,
                    onClick = { onPersonChange(if (selected) "" else p.name) }
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(50))
                    .border(1.5.dp, ConsumoOrange, RoundedCornerShape(50))
                    .clickable(onClick = onAddPerson)
                    .padding(horizontal = 14.dp)
            ) {
                Text("➕ Novo", color = ConsumoOrange, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PersonChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) ConsumoOrange else Color.White)
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 14.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (selected) Color.White else ConsumoOrange.copy(alpha = 0.15f))
        ) {
            Text(
                name.trim().take(1).uppercase(),
                color = if (selected) ConsumoOrange else ConsumoPink,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            name,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProductTile(product: Product, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 12.dp)
        ) {
            ProductAvatar(product = product, size = 48.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                product.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (product.priceCents > 0) Money.formatWithSymbol(product.priceCents) else "Sem valor",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (product.priceCents > 0) productColor(product.colorIndex) else TextSecondary
            )
        }
    }
}

@Composable
private fun DaySummary(
    type: PersonType,
    day: Long,
    entries: List<Consumption>,
    onDelete: (Consumption) -> Unit,
    onSendWhatsApp: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)) {
        StepTitle("3️⃣", "Baixas de ${type.label} • ${Days.shortDate(day)}")
        Spacer(Modifier.height(8.dp))
        if (entries.isEmpty()) {
            Text(
                "Nenhuma baixa neste dia. Escolha o nome e toque em um produto acima.",
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            return@Column
        }

        val groups = entries.groupBy { it.personName.trim().lowercase() }.values
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(vertical = 6.dp)
        ) {
            groups.forEachIndexed { gi, list ->
                if (gi > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp)
                ) {
                    Text(
                        "${type.emoji} ${list.first().personName.trim()}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        Money.formatWithSymbol(list.sumOf { it.totalCents }),
                        fontWeight = FontWeight.Bold,
                        color = ConsumoPink
                    )
                }
                list.forEach { e ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 14.dp, end = 4.dp)
                    ) {
                        Text(
                            "${e.quantity}× ${e.productName}",
                            color = TextSecondary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(Money.format(e.totalCents), color = TextSecondary)
                        IconButton(onClick = { onDelete(e) }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = "Apagar baixa",
                                tint = DangerRed.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                Text("Total do dia", fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text(
                    Money.formatWithSymbol(entries.sumOf { it.totalCents }),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                )
            }
        }

        val message = ConsumptionMessage.build(type, day, entries)
        Spacer(Modifier.height(16.dp))
        Text("💬 Mensagem para o WhatsApp", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                .background(WhatsAppBubble)
                .padding(14.dp)
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onSendWhatsApp(message) },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
        ) { Text("📲 Enviar no WhatsApp", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { onCopy(message) },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) { Text("📋 Copiar mensagem", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ConsumeDialog(
    product: Product,
    person: String,
    type: PersonType,
    onDismiss: () -> Unit,
    onConfirm: (quantity: Long, unitPriceCents: Long) -> Unit
) {
    var qty by remember { mutableStateOf(1L) }
    var price by remember { mutableStateOf(product.priceCents) }
    val color = productColor(product.colorIndex)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { ProductAvatar(product = product, size = 64.dp) },
        title = { Text(product.name, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Para: ${type.emoji} $person (${type.label})", color = TextSecondary)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepperButton("−", enabled = qty > 1, color = color) { qty-- }
                    Text(
                        qty.toString(),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(96.dp)
                    )
                    StepperButton("+", enabled = qty < 999, color = color) { qty++ }
                }
                Text("quantidade", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                MoneyField(cents = price, onChange = { price = it }, label = "Valor de cada um")
                Spacer(Modifier.height(12.dp))
                Text(
                    "Total: ${Money.formatWithSymbol(qty * price)}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ConsumoPink
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(qty, price) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ConsumoOrange)
            ) { Text("✅ Dar baixa", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Aparece logo depois de dar baixa, já com a mensagem pronta para o WhatsApp. */
@Composable
private fun AfterConsumeDialog(
    entry: Consumption,
    dayCount: Int,
    onSendThis: () -> Unit,
    onSendDay: () -> Unit,
    onDismiss: () -> Unit
) {
    val message = ConsumptionMessage.build(entry.personType, entry.dayMillis, listOf(entry))
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text("✅", fontSize = 40.sp) },
        title = { Text("Baixa registrada!", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    message,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                        .background(WhatsAppBubble)
                        .padding(14.dp)
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onSendThis,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) { Text("📲 Enviar no WhatsApp", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                if (dayCount > 1) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onSendDay,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            "Enviar todas as $dayCount baixas do dia",
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Agora não") } }
    )
}

@Composable
private fun StepperButton(symbol: String, enabled: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (enabled) color else color.copy(alpha = 0.25f))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Text(symbol, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    }
}
