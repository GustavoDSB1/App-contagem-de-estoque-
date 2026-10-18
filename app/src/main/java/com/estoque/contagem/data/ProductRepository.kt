package com.estoque.contagem.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Salva e carrega os produtos em um arquivo JSON dentro do próprio app. */
class ProductRepository(context: Context) {

    private val file = File(context.filesDir, "estoque.json")

    fun load(): StockData {
        if (!file.exists()) return StockData(emptyList(), System.currentTimeMillis())
        return try {
            fromJson(JSONObject(file.readText()))
        } catch (e: Exception) {
            // Guarda o arquivo com problema para não perder os dados ao salvar de novo.
            file.renameTo(File(file.parentFile, "estoque-corrompido-${System.currentTimeMillis()}.json"))
            StockData(emptyList(), System.currentTimeMillis())
        }
    }

    fun save(data: StockData) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(toJson(data).toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun toJson(data: StockData): JSONObject {
        val products = JSONArray()
        data.products.forEach { p ->
            val entries = JSONArray()
            p.entries.forEach { e ->
                entries.put(
                    JSONObject()
                        .put("amount", e.amount)
                        .put("totalAfter", e.totalAfter)
                        .put("timestamp", e.timestamp)
                        .put("isCorrection", e.isCorrection)
                )
            }
            products.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("emoji", p.emoji)
                    .put("colorIndex", p.colorIndex)
                    .put("quantity", p.quantity)
                    .put("createdAt", p.createdAt)
                    .put("priceCents", p.priceCents)
                    .put("image", p.imageFile ?: JSONObject.NULL)
                    .put("excludeFromExcel", p.excludeFromExcel)
                    .put("entries", entries)
            )
        }
        val consumptions = JSONArray()
        data.consumptions.forEach { c ->
            consumptions.put(
                JSONObject()
                    .put("id", c.id)
                    .put("personName", c.personName)
                    .put("personType", c.personType.name)
                    .put("productId", c.productId)
                    .put("productName", c.productName)
                    .put("quantity", c.quantity)
                    .put("unitPriceCents", c.unitPriceCents)
                    .put("dayMillis", c.dayMillis)
                    .put("timestamp", c.timestamp)
            )
        }
        val people = JSONArray()
        data.people.forEach { p ->
            people.put(JSONObject().put("id", p.id).put("name", p.name).put("type", p.type.name))
        }
        return JSONObject()
            .put("version", 3)
            .put("countStartedAt", data.countStartedAt)
            .put("products", products)
            .put("consumptions", consumptions)
            .put("people", people)
    }

    private fun fromJson(json: JSONObject): StockData {
        val arr = json.optJSONArray("products") ?: JSONArray()
        val products = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val entriesArr = o.optJSONArray("entries") ?: JSONArray()
            val entries = (0 until entriesArr.length()).map { j ->
                val e = entriesArr.getJSONObject(j)
                CountEntry(
                    amount = e.getLong("amount"),
                    totalAfter = e.getLong("totalAfter"),
                    timestamp = e.getLong("timestamp"),
                    isCorrection = e.optBoolean("isCorrection", false)
                )
            }
            Product(
                id = o.getString("id"),
                name = o.getString("name"),
                emoji = o.optString("emoji", "📦"),
                colorIndex = o.optInt("colorIndex", 0),
                quantity = o.optLong("quantity", 0),
                entries = entries,
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                priceCents = o.optLong("priceCents", 0),
                imageFile = if (o.isNull("image")) null else o.optString("image").ifBlank { null },
                excludeFromExcel = o.optBoolean("excludeFromExcel", false)
            )
        }
        val consArr = json.optJSONArray("consumptions") ?: JSONArray()
        val consumptions = (0 until consArr.length()).map { i ->
            val o = consArr.getJSONObject(i)
            Consumption(
                id = o.getString("id"),
                personName = o.getString("personName"),
                personType = PersonType.entries.firstOrNull { it.name == o.optString("personType") }
                    ?: PersonType.EMPLOYEE,
                productId = o.optString("productId"),
                productName = o.getString("productName"),
                quantity = o.getLong("quantity"),
                unitPriceCents = o.optLong("unitPriceCents", 0),
                dayMillis = o.getLong("dayMillis"),
                timestamp = o.optLong("timestamp", o.getLong("dayMillis"))
            )
        }
        val peopleArr = json.optJSONArray("people") ?: JSONArray()
        val people = (0 until peopleArr.length()).mapNotNull { i ->
            val o = peopleArr.getJSONObject(i)
            val type = PersonType.entries.firstOrNull { it.name == o.optString("type") } ?: return@mapNotNull null
            Person(o.getString("id"), o.getString("name"), type)
        }
        return StockData(
            products,
            json.optLong("countStartedAt", System.currentTimeMillis()),
            consumptions,
            people
        )
    }
}
