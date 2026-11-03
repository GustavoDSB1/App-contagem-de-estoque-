package com.estoque.contagem.data

import com.estoque.contagem.consumo.MessageTemplate
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** Produto no arquivo de backup: cadastro e preço, sem contagem. */
data class BackupProduct(
    val name: String,
    val emoji: String,
    val colorIndex: Int,
    val priceCents: Long,
    /** Foto em JPEG (opcional). */
    val photoJpeg: ByteArray? = null,
    val excludeFromExcel: Boolean = false,
    val unit: MeasureUnit = MeasureUnit.UN,
    val category: String = "",
    val barcode: String = ""
)

/** [typeId] é o id do grupo ([PersonType.id]). */
data class BackupPerson(val name: String, val typeId: String)

data class BackupCompany(
    val name: String,
    val document: String,
    /** Logo em PNG (opcional). */
    val logoPng: ByteArray? = null
)

data class BackupData(
    val products: List<BackupProduct>,
    val people: List<BackupPerson>,
    val createdAt: Long,
    val company: BackupCompany? = null,
    /** Grupos de pessoas (funcionário, diarista...). */
    val groups: List<PersonType> = emptyList(),
    /** Modelo da mensagem do WhatsApp (null = não incluído). */
    val messageTemplate: MessageTemplate? = null
)

class BackupException(message: String) : Exception(message)

/**
 * Arquivo de backup para levar os cadastros para outro celular:
 * produtos, preços, ícones/fotos, funcionários e diaristas.
 * Não leva contagem nem baixas de consumo.
 */
object BackupFormat {

    private const val KIND = "contagem-estoque-backup"
    private const val VERSION = 1

    fun encode(data: BackupData): String {
        val products = JSONArray()
        data.products.forEach { p ->
            products.put(
                JSONObject()
                    .put("nome", p.name)
                    .put("emoji", p.emoji)
                    .put("cor", p.colorIndex)
                    .put("valorCentavos", p.priceCents)
                    .put("foto", p.photoJpeg?.let { Base64.getEncoder().encodeToString(it) } ?: JSONObject.NULL)
                    .put("foraDaPlanilha", p.excludeFromExcel)
                    .put("unidade", p.unit.code)
                    .put("categoria", p.category)
                    .put("codigo", p.barcode)
            )
        }
        val people = JSONArray()
        data.people.forEach { person ->
            people.put(JSONObject().put("nome", person.name).put("tipo", person.typeId))
        }
        val json = JSONObject()
            .put("tipo", KIND)
            .put("versao", VERSION)
            .put("criadoEm", data.createdAt)
            .put("produtos", products)
            .put("pessoas", people)
            .put("grupos", JSONArray().apply {
                data.groups.forEach { g -> put(JSONObject().put("id", g.id).put("nome", g.label).put("emoji", g.emoji)) }
            })
        data.messageTemplate?.let { json.put("mensagem", it.toJson()) }
        data.company?.let { c ->
            json.put(
                "empresa",
                JSONObject()
                    .put("nome", c.name)
                    .put("documento", c.document)
                    .put("logo", c.logoPng?.let { Base64.getEncoder().encodeToString(it) } ?: JSONObject.NULL)
            )
        }
        return json.toString()
    }

    fun decode(text: String): BackupData {
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw BackupException("Esse arquivo não é um backup do app.")
        }
        if (json.optString("tipo") != KIND) throw BackupException("Esse arquivo não é um backup do app.")

        val productsArr = json.optJSONArray("produtos") ?: JSONArray()
        val products = (0 until productsArr.length()).mapNotNull { i ->
            val o = productsArr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("nome").trim().take(60)
            if (name.isEmpty()) return@mapNotNull null
            BackupProduct(
                name = name,
                emoji = o.optString("emoji").ifBlank { "📦" },
                colorIndex = o.optInt("cor", i),
                priceCents = o.optLong("valorCentavos", 0).coerceAtLeast(0),
                photoJpeg = if (o.isNull("foto")) null
                else runCatching { Base64.getDecoder().decode(o.getString("foto")) }.getOrNull(),
                excludeFromExcel = o.optBoolean("foraDaPlanilha", false),
                unit = MeasureUnit.fromText(o.optString("unidade")),
                category = o.optString("categoria").trim().take(40),
                barcode = Barcodes.clean(o.optString("codigo"))
            )
        }
        val peopleArr = json.optJSONArray("pessoas") ?: JSONArray()
        val people = (0 until peopleArr.length()).mapNotNull { i ->
            val o = peopleArr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("nome").trim().take(40)
            val typeId = o.optString("tipo").trim()
            if (name.isEmpty() || typeId.isEmpty()) null else BackupPerson(name, typeId)
        }
        // Backups antigos não têm grupos: usa os padrões (funcionário e diarista)
        val groupsArr = json.optJSONArray("grupos")
        val groups = if (groupsArr == null) PersonType.DEFAULTS else (0 until groupsArr.length()).mapNotNull { i ->
            val o = groupsArr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").trim()
            val label = o.optString("nome").trim().take(30)
            if (id.isEmpty() || label.isEmpty()) null else PersonType(id, label, o.optString("emoji").ifBlank { "👤" })
        }
        val template = json.optJSONObject("mensagem")?.let { MessageTemplate.fromJson(it) }
        val company = json.optJSONObject("empresa")?.let { o ->
            BackupCompany(
                name = o.optString("nome").trim().take(60),
                document = o.optString("documento").trim().take(30),
                logoPng = if (o.isNull("logo")) null
                else runCatching { Base64.getDecoder().decode(o.getString("logo")) }.getOrNull()
            )
        }
        return BackupData(products, people, json.optLong("criadoEm", 0), company, groups, template)
    }
}
