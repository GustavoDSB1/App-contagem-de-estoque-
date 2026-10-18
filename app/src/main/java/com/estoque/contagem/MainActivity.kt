package com.estoque.contagem

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.estoque.contagem.consumo.WhatsAppShare
import com.estoque.contagem.excel.ExcelExporter
import com.estoque.contagem.excel.ExportResult
import com.estoque.contagem.ui.BACKUP_MIME
import com.estoque.contagem.ui.BackupExportResult
import com.estoque.contagem.ui.BackupPreview
import com.estoque.contagem.ui.ImportPreview
import com.estoque.contagem.ui.StockViewModel
import com.estoque.contagem.ui.formatQty
import com.estoque.contagem.ui.screens.BackupDoneDialog
import com.estoque.contagem.ui.screens.BackupImportDialog
import com.estoque.contagem.ui.screens.ConsumptionScreen
import com.estoque.contagem.ui.screens.MoreScreen
import com.estoque.contagem.ui.screens.ExportDoneDialog
import com.estoque.contagem.ui.screens.HomeScreen
import com.estoque.contagem.ui.screens.ImportDialog
import com.estoque.contagem.ui.screens.ProductScreen
import com.estoque.contagem.ui.theme.EstoqueTheme
import com.estoque.contagem.ui.theme.Indigo
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Ícones claros na barra de status, que fica sobre o cabeçalho colorido.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContent {
            EstoqueTheme {
                EstoqueApp()
            }
        }
    }
}

@Composable
fun EstoqueApp(vm: StockViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    var tab by rememberSaveable { mutableStateOf(0) }
    // Mantém a posição da lista, a busca e os filtros enquanto um produto está aberto
    // ou enquanto a outra aba está na tela.
    val screenStates = rememberSaveableStateHolder()

    var openProductId by rememberSaveable { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<ExportResult?>(null) }

    fun notify(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    var importPreview by remember { mutableStateOf<ImportPreview?>(null) }
    val pickSpreadsheet = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.readImport(uri) { result ->
                result
                    .onSuccess { importPreview = it }
                    .onFailure { notify("❌ ${it.message ?: "Não foi possível ler a planilha"}") }
            }
        }
    }
    val openImport = {
        pickSpreadsheet.launch(
            arrayOf(
                ExcelExporter.MIME,
                "application/vnd.ms-excel",
                "application/octet-stream",
                "application/zip"
            )
        )
    }

    // Backup dos cadastros
    var backupBusy by remember { mutableStateOf(false) }
    var backupDone by remember { mutableStateOf<BackupExportResult?>(null) }
    var backupPreview by remember { mutableStateOf<BackupPreview?>(null) }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            backupBusy = true
            vm.readBackup(uri) { result ->
                backupBusy = false
                result
                    .onSuccess { backupPreview = it }
                    .onFailure { notify("❌ ${it.message ?: "Não foi possível ler o backup"}") }
            }
        }
    }

    val openProduct = state.products.firstOrNull { it.id == openProductId }

    AnimatedContent(
        targetState = openProduct?.id,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "tela"
    ) { productId ->
        val product = state.products.firstOrNull { it.id == productId }
        if (productId != null && product == null) {
            // Produto acabou de ser excluído: tela vazia durante a animação de saída
            Box(Modifier.fillMaxSize())
        } else if (product != null) {
            BackHandler { openProductId = null }
            ProductScreen(
                product = product,
                onBack = { openProductId = null },
                onAdd = { amount ->
                    vm.addQuantity(product.id, amount)
                    openProductId = null
                    notify("✅ +${formatQty(amount)} em ${product.name} (total ${formatQty(product.quantity + amount)})")
                },
                onSetTotal = { value ->
                    vm.setQuantity(product.id, value)
                    openProductId = null
                    notify("✏️ ${product.name} corrigido para ${formatQty(value)}")
                },
                onEdit = { name, emoji, price, photo -> vm.editProduct(product.id, name, emoji, price, photo) },
                onExcludeFromExcel = { excluded -> vm.setExcludedFromExcel(product.id, excluded) },
                onDelete = {
                    vm.deleteProduct(product.id)
                    openProductId = null
                    notify("🗑️ ${product.name} foi excluído")
                }
            )
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    NavigationBar(containerColor = Color.White) {
                        listOf("📦" to "Estoque", "🧾" to "Consumo", "⚙️" to "Mais").forEachIndexed { i, (emoji, label) ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Text(emoji, fontSize = 22.sp) },
                                label = { Text(label, fontWeight = FontWeight.SemiBold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedTextColor = Indigo,
                                    indicatorColor = Indigo.copy(alpha = 0.12f)
                                )
                            )
                        }
                    }
                }
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding)
                ) {
                    if (tab == 0) screenStates.SaveableStateProvider("estoque") {
                        HomeScreen(
                            state = state,
                            snackbarHostState = snackbar,
                            exporting = exporting,
                            onOpenProduct = { openProductId = it },
                            onAddProduct = { name, emoji, qty, price, photo ->
                                val ok = vm.addProduct(name, emoji, qty, price, photo)
                                if (ok) notify("📦 $name cadastrado!")
                                ok
                            },
                            onExport = {
                                exporting = true
                                vm.exportExcel { result ->
                                    exporting = false
                                    result
                                        .onSuccess { exportResult = it }
                                        .onFailure { notify("❌ Não foi possível gerar a planilha: ${it.message}") }
                                }
                            },
                            onImport = openImport,
                            onNewCount = {
                                vm.startNewCount()
                                notify("🔄 Nova contagem iniciada!")
                            }
                        )
                    } else if (tab == 1) screenStates.SaveableStateProvider("consumo") {
                        ConsumptionScreen(
                            state = state,
                            snackbarHostState = snackbar,
                            onAdd = { person, type, product, qty, price, day ->
                                vm.addConsumption(person, type, product, qty, price, day)
                            },
                            onDelete = { vm.deleteConsumption(it.id) },
                            onSendWhatsApp = { WhatsAppShare.send(context, it) },
                            onCopy = {
                                clipboard.setText(AnnotatedString(it))
                                notify("📋 Mensagem copiada!")
                            },
                            onNeedName = { notify("👆 Primeiro toque no nome de quem consumiu") },
                            onAddPerson = { name, type -> vm.addPerson(name, type) }
                        )
                    } else screenStates.SaveableStateProvider("mais") {
                        MoreScreen(
                            state = state,
                            snackbarHostState = snackbar,
                            busy = backupBusy,
                            onAddPerson = { name, type -> vm.addPerson(name, type) },
                            onDeletePerson = { vm.deletePerson(it.id) },
                            onExportBackup = {
                                backupBusy = true
                                vm.exportBackup { result ->
                                    backupBusy = false
                                    result
                                        .onSuccess { backupDone = it }
                                        .onFailure { notify("❌ Não foi possível gerar o backup: ${it.message}") }
                                }
                            },
                            onImportBackup = {
                                // Alguns celulares mostram o .json com outro tipo, então aceita qualquer arquivo
                                pickBackup.launch(arrayOf(BACKUP_MIME, "application/octet-stream", "text/plain", "*/*"))
                            },
                            onImportSpreadsheet = openImport,
                            onSetExcludedFromExcel = { ids ->
                                vm.setExcludedFromExcel(ids)
                                notify("✅ Lista salva: ${ids.size} produto(s) fora da planilha")
                            }
                        )
                    }
                }
            }
        }
    }

    backupDone?.let { result ->
        BackupDoneDialog(
            result = result,
            onShare = {
                ExcelExporter.shareFile(context, result.shareUri, BACKUP_MIME, "Backup do Contagem de Estoque", "Enviar backup")
            },
            onDismiss = { backupDone = null }
        )
    }

    backupPreview?.let { preview ->
        BackupImportDialog(
            preview = preview,
            onConfirm = {
                backupPreview = null
                vm.applyBackup(preview.data) { notify("✅ Backup importado!") }
            },
            onDismiss = { backupPreview = null }
        )
    }

    importPreview?.let { preview ->
        ImportDialog(
            preview = preview,
            onDismiss = { importPreview = null },
            onConfirm = { withQuantities ->
                val added = vm.importProducts(preview.newProducts, withQuantities)
                importPreview = null
                notify("📥 $added produto(s) importados!")
            }
        )
    }

    exportResult?.let { result ->
        ExportDoneDialog(
            result = result,
            onShare = { ExcelExporter.share(context, result) },
            onOpen = {
                if (!ExcelExporter.open(context, result)) {
                    notify("Nenhum app para abrir planilhas. Use \"Enviar\" ou instale o Excel / Google Planilhas.")
                }
            },
            onDismiss = { exportResult = null }
        )
    }
}
