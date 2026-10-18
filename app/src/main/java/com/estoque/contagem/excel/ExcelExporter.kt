package com.estoque.contagem.excel

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.estoque.contagem.data.Product
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportResult(
    val fileName: String,
    val shareUri: Uri,
    val savedToDownloads: Boolean,
    val productCount: Int
)

/** Gera a planilha, salva em Downloads e permite compartilhar/abrir. */
object ExcelExporter {

    const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun export(
        context: Context,
        products: List<Product>,
        countStartedAt: Long,
        now: Long = System.currentTimeMillis()
    ): ExportResult {
        val sheets = StockReport.build(products, countStartedAt, now)
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date(now))
        val fileName = "Contagem_Estoque_$stamp.xlsx"

        val dir = File(context.cacheDir, "planilhas").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val file = File(dir, fileName)
        file.outputStream().use { XlsxWriter.write(sheets, it) }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val saved = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            runCatching { saveToDownloads(context, file, fileName) }.isSuccess

        return ExportResult(fileName, uri, saved, products.count { !it.excludeFromExcel })
    }

    /** Copia o arquivo para a pasta Downloads do celular (Android 10+). */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun saveToDownloads(context: Context, file: File, fileName: String, mime: String = MIME) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Não foi possível criar o arquivo em Downloads")
        try {
            val out = resolver.openOutputStream(uri) ?: error("Não foi possível gravar em Downloads")
            out.use { o -> file.inputStream().use { it.copyTo(o) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    fun share(context: Context, result: ExportResult) =
        shareFile(context, result.shareUri, MIME, "Contagem de estoque", "Enviar planilha")

    /** Abre a lista de apps (WhatsApp, e-mail, Drive...) para enviar um arquivo. */
    fun shareFile(context: Context, uri: Uri, mime: String, subject: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title))
    }

    /** Retorna false se não houver nenhum app que abra planilhas. */
    fun open(context: Context, result: ExportResult): Boolean {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(result.shareUri, MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(view)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
