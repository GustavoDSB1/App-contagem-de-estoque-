package com.estoque.contagem.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.estoque.contagem.data.PhotoChange
import com.estoque.contagem.data.Product
import com.estoque.contagem.excel.StockReport
import com.estoque.contagem.ui.UiState
import com.estoque.contagem.ui.components.ConfirmDialog
import com.estoque.contagem.ui.components.ProductAvatar
import com.estoque.contagem.ui.components.ProductFormDialog
import com.estoque.contagem.ui.components.StatusPill
import com.estoque.contagem.ui.formatDate
import com.estoque.contagem.ui.formatQty
import com.estoque.contagem.ui.formatWhen
import com.estoque.contagem.ui.normalizedForSearch
import com.estoque.contagem.ui.theme.DangerRed
import com.estoque.contagem.ui.theme.ExcelGreen
import com.estoque.contagem.ui.theme.Indigo
import com.estoque.contagem.ui.theme.SuccessBg
import com.estoque.contagem.ui.theme.SuccessText
import com.estoque.contagem.ui.theme.TextSecondary
import com.estoque.contagem.ui.theme.Violet
import com.estoque.contagem.ui.theme.WarningBg
import com.estoque.contagem.ui.theme.WarningText
import com.estoque.contagem.ui.theme.productColor

@Composable
fun HomeScreen(
    state: UiState,
    snackbarHostState: SnackbarHostState,
    exporting: Boolean,
    onOpenProduct: (String) -> Unit,
    onAddProduct: (name: String, emoji: String, initialQuantity: Long, priceCents: Long, photo: PhotoChange) -> Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onNewCount: () -> Unit
) {
    var showNewProduct by rememberSaveable { mutableStateOf(false) }
    var showNewCount by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(ProductFilter.ALL.name) }
    val filter = ProductFilter.valueOf(filterName)
    // Guarda a posição da lista ao abrir um produto e voltar
    val listState = rememberLazyListState()

    val sorted = remember(state.products) { StockReport.sortByName(state.products) }
    val counts = remember(sorted) { ProductFilter.entries.associateWith { f -> sorted.count { f.matches(it) } } }
    val filtered = remember(sorted, query, filter) {
        val q = query.trim().normalizedForSearch()
        sorted.filter { filter.matches(it) && (q.isEmpty() || it.name.normalizedForSearch().contains(q)) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.products.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showNewProduct = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Novo produto", fontWeight = FontWeight.Bold) },
                    containerColor = Indigo,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp)
                )
            }
        },
        bottomBar = {
            ExportBar(enabled = state.products.isNotEmpty(), exporting = exporting, onExport = onExport)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            HomeHeader(state = state, onNewCount = { showNewCount = true })

            when {
                !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.products.isEmpty() -> EmptyState(onAdd = { showNewProduct = true }, onImport = onImport)

                else -> {
                    SearchField(query = query, onQueryChange = { query = it })
                    FilterChips(
                        selected = filter,
                        counts = counts,
                        onSelect = { filterName = it.name }
                    )
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (filtered.isEmpty()) {
                            item {
                                Text(
                                    when {
                                        query.isNotBlank() -> "Nenhum produto encontrado para \"$query\""
                                        filter == ProductFilter.NO_PRICE -> "🎉 Todos os produtos têm valor!"
                                        filter == ProductFilter.NOT_COUNTED -> "🎉 Todos os produtos foram contados!"
                                        filter == ProductFilter.EXCLUDED -> "Nenhum produto fora da planilha"
                                        else -> "Nenhum produto"
                                    },
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp)
                                )
                            }
                        }
                        if (filter == ProductFilter.NO_PRICE && filtered.isNotEmpty()) {
                            item {
                                Text(
                                    "Toque no produto e depois em 💲 Definir valor.",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        items(filtered, key = { it.id }) { product ->
                            ProductCard(product = product, onClick = { onOpenProduct(product.id) })
                        }
                        item {
                            TextButton(
                                onClick = onImport,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("📥 Importar produtos de uma planilha Excel", fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
        }
    }

    if (showNewProduct) {
        ProductFormDialog(
            title = "Novo produto",
            askInitialQuantity = true,
            onDismiss = { showNewProduct = false },
            onSave = { name, emoji, qty, price, photo ->
                val ok = onAddProduct(name, emoji, qty, price, photo)
                if (ok) showNewProduct = false
                ok
            }
        )
    }

    if (showNewCount) {
        ConfirmDialog(
            emoji = "🔄",
            title = "Começar nova contagem?",
            message = "Todas as quantidades voltam para zero. Os produtos continuam cadastrados.\n\n" +
                "Dica: gere a planilha Excel antes, para guardar esta contagem.",
            confirmText = "Zerar e começar",
            confirmColor = DangerRed,
            onConfirm = { showNewCount = false; onNewCount() },
            onDismiss = { showNewCount = false }
        )
    }
}

@Composable
private fun HomeHeader(state: UiState, onNewCount: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brush.linearGradient(listOf(Indigo, Violet)))
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("📦 Meu Estoque", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Contagem iniciada em ${formatDate(state.countStartedAt)}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp
                    )
                }
                if (state.products.isNotEmpty()) {
                    Text(
                        text = "🔄 Nova\ncontagem",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable(onClick = onNewCount)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(emoji = "🏷️", label = "Produtos", value = state.products.size.toString(), modifier = Modifier.weight(1f))
                StatCard(emoji = "🔢", label = "Itens contados", value = formatQty(state.totalItems), modifier = Modifier.weight(1f))
            }
            if (state.products.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                val counted = state.countedProducts
                val total = state.products.size
                Text(
                    if (counted == total) "🎉 Todos os $total produtos foram contados!"
                    else "$counted de $total produtos contados",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { counted.toFloat() / total },
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                )
            }
        }
    }
}

@Composable
private fun StatCard(emoji: String, label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(14.dp)
    ) {
        Text("$emoji $label", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
        Text(
            value,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

enum class ProductFilter(val label: String) {
    ALL("Todos"),
    NOT_COUNTED("⏳ Falta contar"),
    NO_PRICE("💲 Sem valor"),
    EXCLUDED("🚫 Fora do Excel");

    fun matches(p: Product): Boolean = when (this) {
        ALL -> true
        NOT_COUNTED -> !p.wasCounted
        NO_PRICE -> p.priceCents <= 0
        EXCLUDED -> p.excludeFromExcel
    }
}

@Composable
private fun FilterChips(
    selected: ProductFilter,
    counts: Map<ProductFilter, Int>,
    onSelect: (ProductFilter) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        // O filtro "Fora do Excel" só aparece quando existe algum produto na lista de exclusão
        items(ProductFilter.entries.filter { it != ProductFilter.EXCLUDED || (counts[it] ?: 0) > 0 || it == selected }) { f ->
            FilterChip(
                selected = f == selected,
                onClick = { onSelect(f) },
                label = { Text("${f.label} (${counts[f] ?: 0})", fontWeight = FontWeight.SemiBold) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.White,
                    selectedContainerColor = Indigo,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Buscar produto...") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Limpar busca")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Color.White,
            focusedContainerColor = Color.White,
            unfocusedBorderColor = Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun ProductCard(product: Product, onClick: () -> Unit) {
    val color = productColor(product.colorIndex)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductAvatar(product = product, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    product.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                val last = product.lastCountAt
                if (last != null) {
                    StatusPill("✅ ${formatWhen(last).replaceFirstChar { it.uppercase() }}", SuccessBg, SuccessText)
                } else {
                    StatusPill("⏳ Falta contar", WarningBg, WarningText)
                }
                if (product.excludeFromExcel) {
                    Spacer(Modifier.height(4.dp))
                    StatusPill("🚫 Fora do Excel", Color(0xFFF1F5F9), TextSecondary)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatQty(product.quantity),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = color
                )
                Text("unidades", fontSize = 12.sp, color = TextSecondary)
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Contar ${product.name}", tint = Color.White)
            }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit, onImport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Text("📦", fontSize = 72.sp) }
        Spacer(Modifier.height(24.dp))
        Text("Nenhum produto ainda", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Cadastre os produtos que você vai contar. Depois é só tocar em cada um e somar as quantidades.",
            textAlign = TextAlign.Center,
            color = TextSecondary,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAdd,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.height(56.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Cadastrar primeiro produto", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onImport,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.height(56.dp)
        ) {
            Text("📥 Importar de planilha Excel", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Use a planilha gerada pelo app (ou qualquer uma com a coluna \"Produto\").",
            textAlign = TextAlign.Center,
            color = TextSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun ExportBar(enabled: Boolean, exporting: Boolean, onExport: () -> Unit) {
    Surface(color = Color.White, shadowElevation = 12.dp) {
        Box(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = onExport,
                enabled = enabled && !exporting,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ExcelGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                if (exporting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text("📊", fontSize = 22.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (exporting) "Gerando planilha..." else "Gerar planilha Excel",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
