package com.estoque.contagem.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.estoque.contagem.data.Consumption
import com.estoque.contagem.data.CountEntry
import com.estoque.contagem.data.BackupData
import com.estoque.contagem.data.BackupException
import com.estoque.contagem.data.BackupFormat
import com.estoque.contagem.data.BackupPerson
import com.estoque.contagem.data.BackupProduct
import com.estoque.contagem.data.Person
import com.estoque.contagem.data.PersonType
import com.estoque.contagem.data.PhotoChange
import com.estoque.contagem.data.ProductImages
import com.estoque.contagem.data.Product
import com.estoque.contagem.data.ProductRepository
import com.estoque.contagem.data.StockData
import com.estoque.contagem.excel.ExcelExporter
import com.estoque.contagem.excel.ExportResult
import com.estoque.contagem.excel.ImportException
import com.estoque.contagem.excel.ImportedProduct
import com.estoque.contagem.excel.ProductImport
import com.estoque.contagem.excel.XlsxReader
import com.estoque.contagem.ui.theme.ProductColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

const val BACKUP_MIME = "application/json"

data class UiState(
    val products: List<Product> = emptyList(),
    val countStartedAt: Long = System.currentTimeMillis(),
    val consumptions: List<Consumption> = emptyList(),
    val people: List<Person> = emptyList(),
    val loaded: Boolean = false
) {
    val totalItems: Long get() = products.sumOf { it.quantity }
    val countedProducts: Int get() = products.count { it.wasCounted }
}

/** Resultado da leitura da planilha, antes de confirmar a importação. */
data class ImportPreview(
    val newProducts: List<ImportedProduct>,
    val duplicates: Int
) {
    val hasQuantities: Boolean get() = newProducts.any { it.quantity > 0 }
    val hasPrices: Boolean get() = newProducts.any { it.priceCents > 0 }
}

/** Resultado da leitura do backup, antes de confirmar. */
data class BackupPreview(
    val data: BackupData,
    val newProducts: Int,
    val updatedPrices: Int,
    val newPeople: Int
)

data class BackupExportResult(
    val fileName: String,
    val shareUri: Uri,
    val savedToDownloads: Boolean,
    val products: Int,
    val people: Int
)

class StockViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ProductRepository(app)
    private val saveMutex = Mutex()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) { repository.load() }
            _state.value = UiState(data.products, data.countStartedAt, data.consumptions, data.people, loaded = true)
            // Primeira vez com o cadastro de pessoas: aproveita os nomes já usados nas baixas
            if (data.people.isEmpty() && data.consumptions.isNotEmpty()) {
                val seeded = data.consumptions
                    .sortedBy { it.timestamp }
                    .distinctBy { it.personType to it.personName.trim().lowercase() }
                    .map { Person(UUID.randomUUID().toString(), it.personName.trim(), it.personType) }
                update { it.copy(people = seeded) }
            }
        }
    }

    private fun update(transform: (UiState) -> UiState) {
        _state.updateAndGet(transform)
        viewModelScope.launch(Dispatchers.IO) {
            saveMutex.withLock {
                val latest = _state.value
                repository.save(StockData(latest.products, latest.countStartedAt, latest.consumptions, latest.people))
            }
        }
    }

    private fun nameTaken(name: String, exceptId: String? = null) =
        _state.value.products.any { it.id != exceptId && it.name.equals(name.trim(), ignoreCase = true) }

    fun isNameTaken(name: String, exceptId: String? = null) = nameTaken(name, exceptId)

    /** Retorna false se já existir um produto com o mesmo nome. */
    fun addProduct(
        name: String,
        emoji: String,
        initialQuantity: Long,
        priceCents: Long,
        photo: PhotoChange = PhotoChange.Keep
    ): Boolean {
        val clean = name.trim()
        if (clean.isEmpty() || nameTaken(clean)) return false
        val imageFile = (photo as? PhotoChange.New)?.let { savePhoto(it) }
        val now = System.currentTimeMillis()
        val products = _state.value.products
        val nextColor = ((products.maxOfOrNull { it.colorIndex } ?: -1) + 1) % ProductColors.size
        val entries = if (initialQuantity > 0) {
            listOf(CountEntry(initialQuantity, initialQuantity, now, isCorrection = false))
        } else {
            emptyList()
        }
        val product = Product(
            id = UUID.randomUUID().toString(),
            name = clean,
            emoji = emoji,
            colorIndex = nextColor,
            quantity = initialQuantity,
            entries = entries,
            createdAt = now,
            priceCents = priceCents,
            imageFile = imageFile
        )
        update { it.copy(products = it.products + product) }
        return true
    }

    fun addQuantity(id: String, amount: Long) {
        if (amount <= 0) return
        val now = System.currentTimeMillis()
        updateProduct(id) { p ->
            val total = p.quantity + amount
            p.copy(quantity = total, entries = p.entries + CountEntry(amount, total, now, isCorrection = false))
        }
    }

    fun setQuantity(id: String, value: Long) {
        if (value < 0) return
        val now = System.currentTimeMillis()
        updateProduct(id) { p ->
            p.copy(quantity = value, entries = p.entries + CountEntry(value, value, now, isCorrection = true))
        }
    }

    fun editProduct(id: String, name: String, emoji: String, priceCents: Long, photo: PhotoChange): Boolean {
        val clean = name.trim()
        if (clean.isEmpty() || nameTaken(clean, exceptId = id)) return false
        val oldImage = _state.value.products.firstOrNull { it.id == id }?.imageFile
        val newImage = when (photo) {
            PhotoChange.Keep -> oldImage
            PhotoChange.Remove -> null
            is PhotoChange.New -> savePhoto(photo) ?: oldImage
        }
        if (newImage != oldImage) ProductImages.delete(getApplication(), oldImage)
        updateProduct(id) { it.copy(name = clean, emoji = emoji, priceCents = priceCents, imageFile = newImage) }
        return true
    }

    private fun savePhoto(photo: PhotoChange.New): String? =
        runCatching { ProductImages.save(getApplication(), photo.bitmap) }.getOrNull()

    fun deleteProduct(id: String) {
        ProductImages.delete(getApplication(), _state.value.products.firstOrNull { it.id == id }?.imageFile)
        update { s -> s.copy(products = s.products.filterNot { it.id == id }) }
    }

    /** Zera as quantidades de todos os produtos e começa uma nova contagem. */
    fun startNewCount() {
        update { s ->
            s.copy(
                products = s.products.map { it.copy(quantity = 0, entries = emptyList()) },
                countStartedAt = System.currentTimeMillis()
            )
        }
    }

    /** Baixa de consumo: não mexe no estoque nem na contagem. Retorna a baixa criada. */
    fun addConsumption(
        personName: String,
        type: PersonType,
        product: Product,
        quantity: Long,
        unitPriceCents: Long,
        dayMillis: Long
    ): Consumption? {
        val name = personName.trim()
        if (name.isEmpty() || quantity <= 0) return null
        val entry = Consumption(
            id = UUID.randomUUID().toString(),
            personName = name,
            personType = type,
            productId = product.id,
            productName = product.name,
            quantity = quantity,
            unitPriceCents = unitPriceCents,
            dayMillis = dayMillis,
            timestamp = System.currentTimeMillis()
        )
        update { it.copy(consumptions = it.consumptions + entry) }
        return entry
    }

    fun deleteConsumption(id: String) {
        update { s -> s.copy(consumptions = s.consumptions.filterNot { it.id == id }) }
    }

    /** Lê a planilha escolhida e mostra o que será importado (ainda não salva nada). */
    fun readImport(uri: Uri, onDone: (Result<ImportPreview>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val sheets = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.use { XlsxReader.read(it) }
                        ?: throw ImportException("Não foi possível abrir o arquivo.")
                    ProductImport.parse(sheets)
                }
            }
            onDone(result.map { items ->
                val existing = _state.value.products.map { it.name.trim().lowercase() }.toSet()
                val fresh = items.filter { it.name.trim().lowercase() !in existing }
                ImportPreview(fresh, items.size - fresh.size)
            })
        }
    }

    /** Cadastra os produtos da planilha. Nomes que já existem são ignorados. Retorna quantos entraram. */
    fun importProducts(items: List<ImportedProduct>, withQuantities: Boolean): Int {
        val now = System.currentTimeMillis()
        var added = 0
        update { s ->
            val taken = s.products.map { it.name.trim().lowercase() }.toMutableSet()
            var color = (s.products.maxOfOrNull { it.colorIndex } ?: -1) + 1
            val newOnes = items.filter { taken.add(it.name.trim().lowercase()) }.map { item ->
                val qty = if (withQuantities) item.quantity else 0L
                val entries = if (qty > 0) {
                    listOf(CountEntry(qty, qty, item.lastCountAt ?: now, isCorrection = false))
                } else {
                    emptyList()
                }
                Product(
                    id = UUID.randomUUID().toString(),
                    name = item.name,
                    emoji = item.emoji,
                    colorIndex = (color++) % ProductColors.size,
                    quantity = qty,
                    entries = entries,
                    createdAt = now,
                    priceCents = item.priceCents
                )
            }
            added = newOnes.size
            s.copy(products = s.products + newOnes)
        }
        return added
    }

    // ---------- Funcionários e diaristas ----------

    /** Retorna false se o nome estiver vazio ou já cadastrado nesse tipo. */
    fun addPerson(name: String, type: PersonType): Boolean {
        val clean = name.trim().replace(Regex("\\s+"), " ").take(40)
        if (clean.isEmpty()) return false
        if (_state.value.people.any { it.type == type && it.name.equals(clean, ignoreCase = true) }) return false
        update { it.copy(people = it.people + Person(UUID.randomUUID().toString(), clean, type)) }
        return true
    }

    fun deletePerson(id: String) {
        update { s -> s.copy(people = s.people.filterNot { it.id == id }) }
    }

    // ---------- Backup para outro celular ----------

    fun exportBackup(onDone: (Result<BackupExportResult>) -> Unit) {
        val snapshot = _state.value
        val app = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val now = System.currentTimeMillis()
                    val data = BackupData(
                        products = snapshot.products.map { p ->
                            BackupProduct(
                                p.name, p.emoji, p.colorIndex, p.priceCents,
                                ProductImages.readJpeg(app, p.imageFile), p.excludeFromExcel
                            )
                        },
                        people = snapshot.people.map { BackupPerson(it.name, it.type) },
                        createdAt = now
                    )
                    val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.US).format(java.util.Date(now))
                    val fileName = "Backup_Estoque_$stamp.json"
                    val dir = java.io.File(app.cacheDir, "backup").apply {
                        mkdirs()
                        listFiles()?.forEach { it.delete() }
                    }
                    val file = java.io.File(dir, fileName)
                    file.writeText(BackupFormat.encode(data))
                    val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
                    val saved = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
                        runCatching { ExcelExporter.saveToDownloads(app, file, fileName, BACKUP_MIME) }.isSuccess
                    BackupExportResult(fileName, uri, saved, data.products.size, data.people.size)
                }
            }
            onDone(result)
        }
    }

    fun readBackup(uri: Uri, onDone: (Result<BackupPreview>) -> Unit) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = app.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        if (bytes.size > 60 * 1024 * 1024) throw BackupException("Arquivo grande demais.")
                        String(bytes, Charsets.UTF_8)
                    } ?: throw BackupException("Não foi possível abrir o arquivo.")
                    BackupFormat.decode(text)
                }
            }
            onDone(result.map { data ->
                val s = _state.value
                val existing = s.products.associateBy { it.name.trim().lowercase() }
                BackupPreview(
                    data = data,
                    newProducts = data.products.count { it.name.trim().lowercase() !in existing },
                    updatedPrices = data.products.count { b ->
                        val p = existing[b.name.trim().lowercase()]
                        p != null && b.priceCents > 0 && b.priceCents != p.priceCents
                    },
                    newPeople = data.people.count { b ->
                        s.people.none { it.type == b.type && it.name.equals(b.name, ignoreCase = true) }
                    }
                )
            })
        }
    }

    /**
     * Junta o backup com o que já existe: produtos novos são criados (quantidade zero),
     * produtos com o mesmo nome recebem o preço (e a foto, se não tiverem) do backup,
     * e pessoas novas são cadastradas. Contagens e baixas não são mexidas.
     */
    fun applyBackup(data: BackupData, onDone: () -> Unit) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            // Grava as fotos antes (fora da tela principal)
            val photoFiles = withContext(Dispatchers.IO) {
                data.products.map { b -> b.photoJpeg?.let { runCatching { ProductImages.saveJpeg(app, it) }.getOrNull() } }
            }
            val now = System.currentTimeMillis()
            val unused = mutableListOf<String>()
            update { s ->
                unused.clear()
                val byName = s.products.associateBy { it.name.trim().lowercase() }.toMutableMap()
                val added = mutableListOf<Product>()
                val updatedIds = HashMap<String, Product>()
                data.products.forEachIndexed { i, b ->
                    val key = b.name.trim().lowercase()
                    val photo = photoFiles[i]
                    val current = byName[key]
                    if (current == null) {
                        val p = Product(
                            id = UUID.randomUUID().toString(),
                            name = b.name,
                            emoji = b.emoji,
                            colorIndex = Math.floorMod(b.colorIndex, ProductColors.size),
                            quantity = 0,
                            entries = emptyList(),
                            createdAt = now,
                            priceCents = b.priceCents,
                            imageFile = photo,
                            excludeFromExcel = b.excludeFromExcel
                        )
                        added += p
                        byName[key] = p
                    } else if (current.id in updatedIds || added.any { it.id == current.id }) {
                        // Nome repetido dentro do próprio backup: ignora
                        if (photo != null) unused += photo
                    } else {
                        val usePhoto = current.imageFile == null && photo != null
                        if (!usePhoto && photo != null) unused += photo
                        updatedIds[current.id] = current.copy(
                            priceCents = if (b.priceCents > 0) b.priceCents else current.priceCents,
                            imageFile = if (usePhoto) photo else current.imageFile
                        )
                    }
                }
                val newPeople = mutableListOf<Person>()
                data.people.forEach { b ->
                    val exists = (s.people + newPeople).any { it.type == b.type && it.name.equals(b.name, ignoreCase = true) }
                    if (!exists) newPeople += Person(UUID.randomUUID().toString(), b.name, b.type)
                }
                s.copy(
                    products = s.products.map { updatedIds[it.id] ?: it } + added,
                    people = s.people + newPeople
                )
            }
            withContext(Dispatchers.IO) { unused.forEach { ProductImages.delete(app, it) } }
            onDone()
        }
    }

    fun exportExcel(onDone: (Result<ExportResult>) -> Unit) {
        val snapshot = _state.value
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (snapshot.products.none { !it.excludeFromExcel }) {
                        throw IllegalStateException("todos os produtos estão marcados como fora da planilha")
                    }
                    ExcelExporter.export(getApplication(), snapshot.products, snapshot.countStartedAt)
                }
            }
            onDone(result)
        }
    }

    /** Lista de exclusão da planilha: marca ou desmarca um produto. */
    fun setExcludedFromExcel(id: String, excluded: Boolean) {
        updateProduct(id) { it.copy(excludeFromExcel = excluded) }
    }

    /** Define de uma vez quais produtos ficam fora da planilha. */
    fun setExcludedFromExcel(ids: Set<String>) {
        update { s -> s.copy(products = s.products.map { it.copy(excludeFromExcel = it.id in ids) }) }
    }

    private fun updateProduct(id: String, transform: (Product) -> Product) {
        update { s -> s.copy(products = s.products.map { if (it.id == id) transform(it) else it }) }
    }
}
