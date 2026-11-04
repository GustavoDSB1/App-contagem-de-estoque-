package com.estoque.contagem.data

import kotlin.math.hypot

/**
 * Compara a cor das tampinhas: guarda a cor da tampinha de exemplo e aceita só
 * círculos parecidos (ex.: só as douradas). Usa a "tonalidade" (proporção entre
 * vermelho, verde e azul), que muda pouco com sombra, e tolera diferença de brilho.
 */
object ColorMatch {

    data class Rgb(val r: Float, val g: Float, val b: Float) {
        val brightness: Float get() = (r + g + b) / 3f
    }

    /** Cor média num disco de raio 0,6·r em volta do centro (argb da imagem analisada). */
    fun meanColor(argb: IntArray, w: Int, h: Int, cx: Float, cy: Float, r: Float): Rgb {
        val rr = maxOf(1f, r * 0.6f)
        var sr = 0f; var sg = 0f; var sb = 0f; var n = 0
        val x0 = maxOf(0, (cx - rr).toInt()); val x1 = minOf(w - 1, (cx + rr).toInt())
        val y0 = maxOf(0, (cy - rr).toInt()); val y1 = minOf(h - 1, (cy + rr).toInt())
        for (y in y0..y1) for (x in x0..x1) {
            if (hypot(x - cx, y - cy) > rr) continue
            val c = argb[y * w + x]
            sr += (c shr 16 and 0xFF); sg += (c shr 8 and 0xFF); sb += (c and 0xFF); n++
        }
        return if (n == 0) Rgb(0f, 0f, 0f) else Rgb(sr / n, sg / n, sb / n)
    }

    fun average(colors: List<Rgb>): Rgb =
        Rgb(colors.map { it.r }.average().toFloat(), colors.map { it.g }.average().toFloat(), colors.map { it.b }.average().toFloat())

    /**
     * Mesma cor? [tolerance] 0..1: maior aceita cores mais diferentes.
     * Dourado × branco × vidro verde ficam bem separados.
     */
    fun similar(a: Rgb, b: Rgb, tolerance: Float = 0.5f): Boolean {
        val sa = a.r + a.g + a.b + 1f
        val sb = b.r + b.g + b.b + 1f
        val chroma = hypot(a.r / sa - b.r / sb, a.g / sa - b.g / sb)
        val la = a.brightness + 15f
        val lb = b.brightness + 15f
        val ratio = maxOf(la, lb) / minOf(la, lb)
        val t = tolerance.coerceIn(0f, 1f)
        return chroma <= 0.035f + 0.06f * t && ratio <= 1.5f + 1.5f * t
    }
}
