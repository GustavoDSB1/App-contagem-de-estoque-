package com.estoque.contagem.data

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Leitor de código de barras do Google (tela de câmera pronta, via Google Play Services).
 * O app não precisa pedir permissão de câmera.
 */
object BarcodeScanner {

    fun scan(context: Context, onResult: (String) -> Unit, onError: (String) -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39, Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_ITF, Barcode.FORMAT_CODABAR, Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX
            )
            .enableAutoZoom()
            .build()
        try {
            GmsBarcodeScanning.getClient(context, options)
                .startScan()
                .addOnSuccessListener { barcode ->
                    val value = Barcodes.clean(barcode.rawValue ?: "")
                    if (value.isNotEmpty()) onResult(value) else onError("Não foi possível ler o código.")
                }
                .addOnFailureListener {
                    onError("Não foi possível abrir o leitor. O celular precisa do Google Play Services atualizado.")
                }
        } catch (e: Exception) {
            onError("Não foi possível abrir o leitor de código de barras.")
        }
    }
}
