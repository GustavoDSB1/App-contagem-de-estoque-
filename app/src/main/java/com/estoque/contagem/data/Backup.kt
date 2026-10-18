package com.estoque.contagem.data

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
    val excludeFromExcel: Boolean = false
)

data class BackupPerson(val name: String, val type: PersonType)

data class BackupData(
    val products: List<BackupProduct>,
    val people: List<BackupPerson>,
    val createdAt: Long
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
            )
        }
        val people = JSONArray()
        data.people.forEach { person ->
            people.put(JSONObject().put("nome", person.name).put("tipo", person.type.name))
        }
        return JSONObject()
            .put("tipo", KIND)
            .put("versao", VERSION)
            .put("criadoEm", data.createdAt)
            .put("produtos", products)
            .put("pessoas", people)
            .toString()
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
                excludeFromExcel = o.optBoolean("foraDaPlanilha", false)
            )
        }
        val peopleArr = json.optJSONArray("pessoas") ?: JSONArray()
        val people = (0 until peopleArr.length()).mapNotNull { i ->
            val o = peopleArr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("nome").trim().take(40)
            val type = PersonType.entries.firstOrNull { it.name == o.optString("tipo") }
            if (name.isEmpty() || type == null) null else BackupPerson(name, type)
        }
        return BackupData(products, people, json.optLong("criadoEm", 0))
    }
}
