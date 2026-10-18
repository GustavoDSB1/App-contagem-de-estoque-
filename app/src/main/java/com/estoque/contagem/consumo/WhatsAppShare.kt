package com.estoque.contagem.consumo

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

object WhatsAppShare {

    /** Abre o WhatsApp (ou WhatsApp Business) com a mensagem pronta. Sem WhatsApp, abre a lista de apps. */
    fun send(context: Context, message: String) {
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            try {
                context.startActivity(textIntent(message).setPackage(pkg))
                return
            } catch (_: ActivityNotFoundException) {
                // tenta o próximo
            }
        }
        context.startActivity(Intent.createChooser(textIntent(message), "Enviar mensagem"))
    }

    private fun textIntent(message: String) = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }
}
