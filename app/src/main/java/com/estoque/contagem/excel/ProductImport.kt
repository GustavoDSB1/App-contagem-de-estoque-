package com.estoque.contagem.excel

import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToLong

data class ImportedProduct(
    val name: String,
    val quantity: Long,
    val priceCents: Long,
    val lastCountAt: Long?,
    val emoji: String
)

class ImportException(message: String) : Exception(message)

/**
 * Transforma as linhas de uma planilha em produtos.
 * Procura uma linha de cabeçalho com a coluna "Produto" (e, se houver, "Quantidade",
 * "Valor" e "Última contagem"). Funciona com a planilha gerada pelo próprio app.
 */
object ProductImport {

    private val BR: Locale = Locale.forLanguageTag("pt-BR")

    private val NAME_HEADERS = setOf(
        "produto", "produtos", "nome", "nome do produto", "item", "itens", "descricao", "mercadoria"
    )
    private val QTY_HEADERS = setOf("quantidade", "qtd", "qtde", "quant", "estoque", "saldo")
    private val PRICE_HEADERS = setOf(
        "valor", "preco", "valor de venda", "preco de venda", "valor unitario", "preco unitario", "r$"
    )
    private val DATE_HEADERS = setOf("ultima contagem", "data", "data da contagem")

    fun parse(sheets: Map<String, List<List<String>>>): List<ImportedProduct> {
        // Primeiro a aba "Contagem" (planilha do app), depois as outras na ordem.
        val ordered = sheets.entries.sortedBy { if (normalize(it.key) == "contagem") 0 else 1 }
        for ((_, rows) in ordered) {
            val products = parseRows(rows) ?: continue
            if (products.isEmpty()) throw ImportException("A planilha não tem nenhum produto preenchido.")
            return products
        }
        throw ImportException("Não encontrei a coluna \"Produto\" na planilha.")
    }

    /** null quando a aba não tem cabeçalho com a coluna de produto. */
    private fun parseRows(rows: List<List<String>>): List<ImportedProduct>? {
        val headerIndex = rows.take(30).indexOfFirst { row -> row.any { normalize(it) in NAME_HEADERS } }
        if (headerIndex < 0) return null
        val header = rows[headerIndex].map { normalize(it) }
        val nameCol = header.indexOfFirst { it in NAME_HEADERS }
        val qtyCol = header.indexOfFirst { it in QTY_HEADERS }
        val priceCol = header.indexOfFirst { it in PRICE_HEADERS }
        val dateCol = header.indexOfFirst { it in DATE_HEADERS }

        val seen = HashSet<String>()
        val products = mutableListOf<ImportedProduct>()
        for (row in rows.drop(headerIndex + 1)) {
            val name = row.getOrNull(nameCol)?.trim()?.replace(Regex("\\s+"), " ").orEmpty().take(60)
            if (name.isEmpty() || normalize(name) == "total") continue
            if (!seen.add(normalize(name))) continue
            products += ImportedProduct(
                name = name,
                quantity = row.cell(qtyCol)?.let { parseNumber(it) }?.roundToLong()?.coerceIn(0, 9_999_999) ?: 0,
                priceCents = row.cell(priceCol)?.let { parseNumber(it) }?.let { (it * 100).roundToLong() }
                    ?.coerceIn(0, 999_999_999) ?: 0,
                lastCountAt = row.cell(dateCol)?.let { parseDate(it) },
                emoji = guessEmoji(name)
            )
        }
        return products
    }

    private fun List<String>.cell(index: Int): String? =
        if (index < 0) null else getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() }

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(BR)
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd(':')
            .trim()

    /** Aceita "12", "12.5" (número do Excel), "R$ 9,00", "1.234,50". */
    fun parseNumber(text: String): Double? {
        var s = text.replace("R$", "").replace(" ", "").replace(" ", "").trim()
        if (s.isEmpty()) return null
        if (s.contains(',')) s = s.replace(".", "").replace(',', '.')
        return s.toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() && it >= 0 }
    }

    /** Aceita "02/10/2026 23:25", "02/10/2026" ou a data numérica do Excel. */
    fun parseDate(text: String): Long? {
        for (pattern in listOf("dd/MM/yyyy HH:mm", "dd/MM/yyyy")) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, BR).apply { isLenient = false }.parse(text)
            }.getOrNull()
            if (parsed != null) return parsed.time
        }
        val serial = text.toDoubleOrNull() ?: return null
        if (serial < 20000 || serial > 80000) return null
        // Excel conta dias desde 30/12/1899 (horário local da planilha)
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(1899, Calendar.DECEMBER, 30)
        }.timeInMillis + (serial * 86_400_000).roundToLong()
        return utc - TimeZone.getDefault().getOffset(utc)
    }

    private val EMOJI_RULES: List<Pair<List<String>, String>> = listOf(
        listOf("agua sanitaria") to "🧴",
        listOf("suco", "del valle", "ades") to "🧃",
        listOf("cerveja", "skol", "brahma", "heineken", "chopp", "amstel", "itaipava", "budweiser") to "🍺",
        listOf("coca", "refri", "guarana", "pepsi", "fanta", "sprite", "soda", "agua", "energetico", "isotonico") to "🥤",
        listOf("leite", "iogurte", "danone") to "🥛",
        listOf("cafe", "capuccino") to "☕",
        listOf("pao", "bisnaguinha", "torrada") to "🍞",
        listOf("arroz", "feijao", "macarrao", "farinha", "acucar") to "🍚",
        listOf("chocolate", "bombom", "kitkat", "trufa") to "🍫",
        listOf("bala", "pirulito", "chiclete", "pipoca", "doce", "paçoca", "pacoca") to "🍬",
        listOf("biscoito", "bolacha", "cookie", "wafer", "salgadinho", "chips") to "🍪",
        listOf("queijo", "requeijao", "presunto", "mortadela") to "🧀",
        listOf("carne", "frango", "linguica", "salsicha", "hamburguer", "bacon") to "🥩",
        listOf("fruta", "maca", "banana", "laranja", "uva", "limao") to "🍎",
        listOf("molho", "milho", "ervilha", "sardinha", "atum", "extrato", "enlatado") to "🥫",
        listOf("sabao", "sabonete") to "🧼",
        listOf("papel", "guardanapo", "lenco") to "🧻",
        listOf("detergente", "desinfetante", "alcool", "shampoo", "condicionador", "creme", "hidratante", "amaciante", "cloro") to "🧴",
        listOf("remedio", "dipirona", "paracetamol", "curativo") to "💊",
        listOf("camisa", "camiseta", "uniforme", "luva", "meia") to "👕",
        listOf("lampada") to "💡",
        listOf("pilha", "bateria", "carregador") to "🔋",
        listOf("caneta", "lapis", "marcador") to "🖊️",
        listOf("parafuso", "chave", "ferramenta", "prego", "fita") to "🔧"
    )

    /** Escolhe um ícone pelo nome do produto (ex.: "Coca 600" -> 🥤). */
    fun guessEmoji(name: String): String {
        val n = normalize(name)
        for ((keywords, emoji) in EMOJI_RULES) {
            if (keywords.any { kw -> Regex("(^|[^a-z0-9])" + Regex.escape(normalize(kw)) + "(s|es)?($|[^a-z])").containsMatchIn(n) }) return emoji
        }
        return "📦"
    }
}
