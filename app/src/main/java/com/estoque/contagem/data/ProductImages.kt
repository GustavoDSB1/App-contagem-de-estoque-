package com.estoque.contagem.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/** O que fazer com a foto ao salvar o produto. */
sealed class PhotoChange {
    data object Keep : PhotoChange()
    data object Remove : PhotoChange()
    data class New(val bitmap: Bitmap) : PhotoChange()
}

/**
 * Fotos dos produtos: recortadas em quadrado, reduzidas e guardadas dentro do app.
 * Assim todos os ícones ficam do mesmo jeito e a lista continua leve.
 */
object ProductImages {

    private const val ICON_SIZE = 384
    private val cache = LruCache<String, ImageBitmap>(80)

    private fun dir(context: Context) = File(context.filesDir, "fotos").apply { mkdirs() }

    fun cached(name: String): ImageBitmap? = cache.get(name)

    fun load(context: Context, name: String): ImageBitmap? {
        cache.get(name)?.let { return it }
        val file = File(dir(context), name)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.path)?.asImageBitmap()?.also { cache.put(name, it) }
    }

    /** Salva a foto e devolve o nome do arquivo (novo a cada foto). */
    fun save(context: Context, bitmap: Bitmap): String {
        val name = "${UUID.randomUUID()}.jpg"
        File(dir(context), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        cache.put(name, bitmap.asImageBitmap())
        return name
    }

    /** Bytes do JPEG salvo (para o backup). */
    fun readJpeg(context: Context, name: String?): ByteArray? {
        if (name.isNullOrBlank()) return null
        return File(dir(context), name).takeIf { it.exists() }?.readBytes()
    }

    /** Salva uma foto vinda do backup. Retorna null se não for uma imagem válida. */
    fun saveJpeg(context: Context, bytes: ByteArray): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val name = "${UUID.randomUUID()}.jpg"
        File(dir(context), name).writeBytes(bytes)
        return name
    }

    fun delete(context: Context, name: String?) {
        if (name.isNullOrBlank()) return
        cache.remove(name)
        File(dir(context), name).delete()
    }

    /** Lê a imagem escolhida (galeria ou câmera), corrige a rotação e recorta em quadrado. */
    fun decodeIcon(context: Context, uri: Uri): Bitmap {
        val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val smallest = min(info.size.width, info.size.height)
                decoder.setTargetSampleSize(max(1, smallest / (ICON_SIZE * 2)))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            decodeLegacy(context, uri)
        }
        return squareIcon(decoded)
    }

    private fun decodeLegacy(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val smallest = min(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = max(1, smallest / (ICON_SIZE * 2))
        }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Não foi possível abrir a imagem")
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Recorta o centro em quadrado e reduz para o tamanho do ícone. */
    private fun squareIcon(source: Bitmap): Bitmap {
        val side = min(source.width, source.height)
        val x = (source.width - side) / 2
        val y = (source.height - side) / 2
        val square = Bitmap.createBitmap(source, x, y, side, side)
        val target = min(side, ICON_SIZE)
        return if (target == side) square else Bitmap.createScaledBitmap(square, target, target, true)
    }

    /** Arquivo temporário para a câmera salvar a foto. */
    fun cameraTempFile(context: Context): File =
        File(File(context.cacheDir, "camera").apply { mkdirs() }, "foto.jpg")
}
