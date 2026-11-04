package com.estoque.contagem.data

import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Ponto marcado na foto (coordenadas da imagem analisada) e o raio do objeto ali. */
data class CountMark(val x: Float, val y: Float, val r: Float = 0f)

/**
 * Tamanho esperado do objeto conforme a altura na foto. Com a foto inclinada,
 * o que está na frente (embaixo) aparece maior que o do fundo (em cima).
 * Com uma amostra só, o tamanho é o mesmo na foto toda.
 */
class RadiusModel private constructor(
    private val y1: Float,
    private val r1: Float,
    private val y2: Float,
    private val r2: Float
) {
    val min: Float = minOf(r1, r2) * 0.6f
    val max: Float = maxOf(r1, r2) * 1.5f

    fun at(y: Float): Float {
        if (y1 == y2) return (r1 + r2) / 2
        val r = r1 + (r2 - r1) * (y - y1) / (y2 - y1)
        return r.coerceIn(min, max)
    }

    companion object {
        fun constant(r: Float) = RadiusModel(0f, r, 0f, r)

        /** A partir de 1 ou 2 tampinhas tocadas (posição e raio de cada uma). */
        fun fromSamples(samples: List<CountMark>): RadiusModel = when {
            samples.isEmpty() -> constant(10f)
            samples.size == 1 || kotlin.math.abs(samples[0].y - samples[1].y) < 5f ->
                constant(samples.map { it.r }.average().toFloat())
            else -> RadiusModel(samples[0].y, samples[0].r, samples[1].y, samples[1].r)
        }
    }
}

/**
 * Conta objetos redondos de tamanho parecido numa foto (tampinhas no engradado,
 * latas vistas de cima...). Usa a transformada de Hough para círculos, guiada pela
 * direção das bordas, com o raio informado pelo usuário (toque numa tampinha).
 *
 * Kotlin puro (sem Android), para poder testar no computador.
 */
object CircleCounter {

    /**
     * @param gray tons de cinza 0..255, linha a linha (w × h)
     * @param radius raio aproximado de UM objeto, em pixels desta imagem
     * @param sensitivity 0..1: maior encontra mais círculos (e mais erros)
     */
    fun detect(gray: IntArray, w: Int, h: Int, radius: Float, sensitivity: Float = 0.5f): List<CountMark> =
        detect(gray, w, h, RadiusModel.constant(radius), sensitivity, null)

    /**
     * @param model tamanho esperado conforme a altura na foto (perspectiva)
     * @param accept filtro extra para cada centro encontrado (cor da tampinha, área escolhida)
     */
    fun detect(
        gray: IntArray,
        w: Int,
        h: Int,
        model: RadiusModel,
        sensitivity: Float = 0.5f,
        accept: ((x: Int, y: Int) -> Boolean)? = null
    ): List<CountMark> {
        require(gray.size == w * h) { "tamanho da imagem não confere" }
        if (model.max < 3f || w < 8 || h < 8) return emptyList()

        val img = blur(blur(gray, w, h), w, h)

        // Bordas (Sobel)
        val gx = FloatArray(w * h)
        val gy = FloatArray(w * h)
        val mag = FloatArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val a = img[i - w - 1]; val b = img[i - w]; val c = img[i - w + 1]
                val d = img[i - 1]; val f = img[i + 1]
                val g = img[i + w - 1]; val hh = img[i + w]; val k = img[i + w + 1]
                val sx = (c + 2 * f + k) - (a + 2 * d + g)
                val sy = (g + 2 * hh + k) - (a + 2 * b + c)
                gx[i] = sx.toFloat()
                gy[i] = sy.toFloat()
                mag[i] = sqrt((sx * sx + sy * sy).toFloat())
            }
        }
        val edgeThreshold = maxOf(40f, percentile(mag, 0.80f))

        // Votação: cada borda vota no centro a "raio" de distância, na direção do gradiente.
        // Só vale o voto se o raio combina com o tamanho esperado naquela altura da foto.
        val rMin = maxOf(2f, model.min * 0.8f)
        val rMax = model.max * 1.2f
        val expected = FloatArray(h) { model.at(it.toFloat()) }
        val votes = FloatArray(w * h)
        for (i in mag.indices) {
            val m = mag[i]
            if (m < edgeThreshold) continue
            val x = i % w
            val y = i / w
            val dx = gx[i] / m
            val dy = gy[i] / m
            var r = rMin
            while (r <= rMax) {
                for (sign in intArrayOf(1, -1)) {
                    val cx = (x + sign * dx * r).roundToInt()
                    val cy = (y + sign * dy * r).roundToInt()
                    if (cx in 0 until w && cy in 0 until h && kotlin.math.abs(r - expected[cy]) <= expected[cy] * 0.2f) {
                        votes[cy * w + cx] += 1f
                    }
                }
                r += 1f
            }
        }

        // Junta votos vizinhos (janela 5×5) e divide pelo perímetro esperado:
        // assim tampinha pequena (fundo) e grande (frente) têm a mesma nota.
        val acc = boxSum(votes, w, h, 2)
        for (y in 0 until h) {
            val circumference = (2 * PI * expected[y]).toFloat()
            for (x in 0 until w) acc[y * w + x] /= circumference
        }

        // Picos: máximos locais acima do limite
        var maxPeak = 0f
        for (v in acc) if (v > maxPeak) maxPeak = v
        val s = sensitivity.coerceIn(0.05f, 0.95f)
        val threshold = maxOf(1.1f - s, maxPeak * (0.75f - s * 0.5f))
        val candidates = mutableListOf<Triple<Int, Int, Float>>()
        for (y in 0 until h) {
            val border = (expected[y] * 0.5f).toInt()
            if (y < border || y >= h - border) continue
            for (x in border until w - border) {
                val v = acc[y * w + x]
                if (v < threshold) continue
                var isMax = true
                loop@ for (yy in y - 2..y + 2) {
                    for (xx in x - 2..x + 2) {
                        if (xx == x && yy == y || xx !in 0 until w || yy !in 0 until h) continue
                        if (acc[yy * w + xx] > v) { isMax = false; break@loop }
                    }
                }
                if (isMax && (accept == null || accept(x, y))) candidates += Triple(x, y, v)
            }
        }

        // Um centro por objeto: descarta picos muito próximos de outro mais forte
        val accepted = mutableListOf<CountMark>()
        for ((x, y, _) in candidates.sortedByDescending { it.third }) {
            val minDist = expected[y] * 1.3f
            if (accepted.none { hypot(it.x - x, it.y - y) < minDist }) {
                accepted += CountMark(x.toFloat(), y.toFloat(), expected[y])
            }
        }
        return accepted
    }

    /** Desfoque 3×3 (tira ruído antes de achar bordas). */
    private fun blur(src: IntArray, w: Int, h: Int): IntArray {
        val out = src.copyOf()
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var sum = 0
                for (yy in y - 1..y + 1) for (xx in x - 1..x + 1) sum += src[yy * w + xx]
                out[y * w + x] = sum / 9
            }
        }
        return out
    }

    /** Soma dos valores numa janela (2·k+1)², usando imagem integral. */
    private fun boxSum(src: FloatArray, w: Int, h: Int, k: Int): FloatArray {
        val integral = DoubleArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var row = 0.0
            for (x in 0 until w) {
                row += src[y * w + x]
                integral[(y + 1) * (w + 1) + (x + 1)] = integral[y * (w + 1) + (x + 1)] + row
            }
        }
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            val y0 = maxOf(0, y - k); val y1 = minOf(h, y + k + 1)
            for (x in 0 until w) {
                val x0 = maxOf(0, x - k); val x1 = minOf(w, x + k + 1)
                out[y * w + x] = (integral[y1 * (w + 1) + x1] - integral[y0 * (w + 1) + x1] -
                    integral[y1 * (w + 1) + x0] + integral[y0 * (w + 1) + x0]).toFloat()
            }
        }
        return out
    }

    private fun percentile(values: FloatArray, p: Float): Float {
        // Histograma (Sobel em 8 bits vai até ~1443)
        val bins = IntArray(1500)
        for (v in values) bins[v.toInt().coerceIn(0, bins.lastIndex)]++
        val target = (values.size * p).toInt()
        var count = 0
        for (i in bins.indices) {
            count += bins[i]
            if (count >= target) return i.toFloat()
        }
        return bins.lastIndex.toFloat()
    }

    /** Marca mais próxima do toque (para remover), ou null se nenhuma estiver perto. */
    fun nearest(marks: List<CountMark>, x: Float, y: Float, maxDist: Float): Int? =
        marks.indices.minByOrNull { hypot(marks[it].x - x, marks[it].y - y) }
            ?.takeIf { hypot(marks[it].x - x, marks[it].y - y) <= maxDist }
}
