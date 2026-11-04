package com.estoque.contagem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

class CircleCounterTest {

    /** Imagem sintética: fundo + discos (tampinhas) + ruído. */
    private fun image(
        w: Int, h: Int, centers: List<Pair<Int, Int>>, r: Int,
        bg: Int = 40, disk: Int = 200, noise: Int = 12, seed: Int = 1
    ): IntArray {
        val rnd = Random(seed)
        return IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val inside = centers.any { (cx, cy) -> hypot((x - cx).toDouble(), (y - cy).toDouble()) <= r }
            ((if (inside) disk else bg) + rnd.nextInt(-noise, noise + 1)).coerceIn(0, 255)
        }
    }

    private fun grid(cols: Int, rows: Int, spacing: Int, x0: Int, y0: Int) =
        (0 until rows).flatMap { r -> (0 until cols).map { c -> (x0 + c * spacing) to (y0 + r * spacing) } }

    @Test
    fun countsBottleCapsInACrate() {
        // Engradado 6×4 = 24 tampinhas quase encostadas (raio 14, distância entre centros 32)
        val centers = grid(6, 4, 32, 40, 40)
        val marks = CircleCounter.detect(image(260, 180, centers, 14), 260, 180, radius = 14f)
        assertEquals(24, marks.size)
        // Cada marca perto de uma tampinha de verdade
        assertTrue(marks.all { m -> centers.any { (cx, cy) -> hypot(m.x - cx, m.y - cy) < 5 } })
    }

    @Test
    fun countsDarkCirclesOnLightBackground() {
        // Latas vistas de cima (escuras sobre fundo claro)
        val centers = grid(5, 3, 40, 40, 40)
        val marks = CircleCounter.detect(image(240, 160, centers, 16, bg = 210, disk = 60), 240, 160, radius = 16f)
        assertEquals(15, marks.size)
    }

    @Test
    fun scatteredItemsWithDifferentNoise() {
        val centers = listOf(30 to 30, 120 to 40, 200 to 35, 60 to 110, 170 to 120, 110 to 160)
        for (seed in 1..3) {
            val marks = CircleCounter.detect(image(240, 200, centers, 12, noise = 20, seed = seed), 240, 200, radius = 12f)
            assertEquals("semente $seed", centers.size, marks.size)
        }
    }

    @Test
    fun ignoresObjectsOfVeryDifferentSize() {
        // 8 tampinhas (raio 12) + 1 objeto grande (raio 40): conta só as tampinhas
        val small = grid(4, 2, 30, 30, 30)
        val w = 300
        val h = 200
        val img = image(w, h, small, 12)
        val big = image(w, h, listOf(220 to 120), 40)
        val merged = IntArray(w * h) { maxOf(img[it], big[it]) }
        val marks = CircleCounter.detect(merged, w, h, radius = 12f)
        assertEquals(8, marks.size)
    }

    /**
     * Engradado mais parecido com uma foto real: divisórias escuras, luz desigual,
     * tampinhas com logotipo no meio, reflexo e tamanhos/posições levemente diferentes.
     */
    private class Photo(val pixels: IntArray, val w: Int, val h: Int, val caps: List<Pair<Int, Int>>)

    private fun realisticCrate(cols: Int, rows: Int, seed: Int, missing: Set<Int> = emptySet()): Photo {
        val rnd = Random(seed)
        val cell = 34
        val w = cols * cell + 20
        val h = rows * cell + 20
        val caps = mutableListOf<Triple<Int, Int, Int>>() // x, y, raio
        for (r in 0 until rows) for (c in 0 until cols) {
            if (r * cols + c in missing) continue // garrafa faltando no engradado
            caps += Triple(10 + c * cell + cell / 2 + rnd.nextInt(-2, 3), 10 + r * cell + cell / 2 + rnd.nextInt(-2, 3), 13 + rnd.nextInt(-1, 2))
        }
        val img = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val light = (x * 40 / w) - 20 // luz desigual: mais escuro à esquerda
            var v = 95 // plástico do engradado
            if ((x - 10) % cell < 3 || (y - 10) % cell < 3) v = 45 // divisórias
            for ((cx, cy, r) in caps) {
                val d = hypot((x - cx).toDouble(), (y - cy).toDouble())
                if (d <= r) {
                    v = when {
                        d <= r * 0.5 -> 120 // logotipo no meio da tampinha
                        else -> 185 // metal da tampinha
                    }
                    if (hypot((x - cx + r / 3).toDouble(), (y - cy + r / 3).toDouble()) < 3) v = 250 // reflexo
                }
            }
            (v + light + rnd.nextInt(-15, 16)).coerceIn(0, 255)
        }
        return Photo(img, w, h, caps.map { it.first to it.second })
    }

    @Test
    fun realisticCrateWithMissingBottles() {
        for (seed in 1..5) {
            val photo = realisticCrate(6, 4, seed, missing = setOf(3, 10, 17)) // 24 - 3 = 21 garrafas
            val marks = CircleCounter.detect(photo.pixels, photo.w, photo.h, radius = 13f)
            assertEquals("semente $seed", 21, marks.size)
            assertTrue(marks.all { m -> photo.caps.any { (cx, cy) -> hypot(m.x - cx, m.y - cy) < 6 } })
        }
    }

    // ---------- Ajustes: perspectiva, cor e área ----------

    private class Cap(val x: Int, val y: Int, val r: Int, val rgb: Int)

    /** Foto colorida sintética: fundo escuro + tampinhas coloridas + ruído. */
    private fun colorPhoto(w: Int, h: Int, caps: List<Cap>, seed: Int = 1): IntArray {
        val rnd = Random(seed)
        return IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            var c = 0x3A3A3A
            for (cap in caps) if (hypot((x - cap.x).toDouble(), (y - cap.y).toDouble()) <= cap.r) c = cap.rgb
            val n = rnd.nextInt(-10, 11)
            val r = ((c shr 16 and 0xFF) + n).coerceIn(0, 255)
            val g = ((c shr 8 and 0xFF) + n).coerceIn(0, 255)
            val b = ((c and 0xFF) + n).coerceIn(0, 255)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    private fun gray(argb: IntArray) = IntArray(argb.size) {
        val c = argb[it]
        ((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
    }

    private val GOLD = 0xC8A03C
    private val WHITE = 0xEDEDE6
    private val GREEN_GLASS = 0x2E6B32

    @Test
    fun perspectiveFrontBigBackSmall() {
        // Foto inclinada: no fundo (em cima) raio 8, na frente (embaixo) raio 20
        val w = 320
        val h = 300
        val caps = mutableListOf<Cap>()
        var y = 25
        while (y < 285) {
            val r = (8 + (y - 25) * 12 / 255)
            var x = 15 + r
            while (x < w - r - 5) {
                caps += Cap(x, y, r, WHITE)
                x += r * 2 + 6
            }
            y += r * 2 + 8
        }
        val argb = colorPhoto(w, h, caps)
        val back = caps.first()
        val front = caps.last()
        val model = RadiusModel.fromSamples(
            listOf(CountMark(front.x.toFloat(), front.y.toFloat(), front.r.toFloat()), CountMark(back.x.toFloat(), back.y.toFloat(), back.r.toFloat()))
        )
        val marks = CircleCounter.detect(gray(argb), w, h, model)
        assertEquals(caps.size, marks.size)

        // Só com o tamanho da tampinha da frente, as do fundo se perdem
        val oneSize = CircleCounter.detect(gray(argb), w, h, front.r.toFloat())
        assertTrue(oneSize.size < caps.size)
    }

    @Test
    fun colorFilterCountsOnlyOneBrand() {
        // 8 douradas + 6 brancas + 4 "fundos de garrafa" verdes (vidro), todos do mesmo tamanho
        val w = 340
        val h = 200
        val caps = mutableListOf<Cap>()
        for (i in 0 until 8) caps += Cap(25 + i * 40, 30, 14, GOLD)
        for (i in 0 until 6) caps += Cap(25 + i * 40, 90, 14, WHITE)
        for (i in 0 until 4) caps += Cap(25 + i * 40, 150, 14, GREEN_GLASS)
        val argb = colorPhoto(w, h, caps)
        val g = gray(argb)
        val model = RadiusModel.constant(14f)

        // Sem filtro, conta pelo menos todas as douradas e brancas (o vidro verde tem pouco contraste)
        assertTrue(CircleCounter.detect(g, w, h, model).size >= 14)

        // Tocou numa dourada: só as douradas
        val gold = ColorMatch.meanColor(argb, w, h, 25f, 30f, 14f)
        val onlyGold = CircleCounter.detect(g, w, h, model, accept = { x, y ->
            ColorMatch.similar(gold, ColorMatch.meanColor(argb, w, h, x.toFloat(), y.toFloat(), 14f))
        })
        assertEquals(8, onlyGold.size)
        assertTrue(onlyGold.all { it.y < 50 })

        // Tocou numa branca: só as brancas
        val white = ColorMatch.meanColor(argb, w, h, 25f, 90f, 14f)
        val onlyWhite = CircleCounter.detect(g, w, h, model, accept = { x, y ->
            ColorMatch.similar(white, ColorMatch.meanColor(argb, w, h, x.toFloat(), y.toFloat(), 14f))
        })
        assertEquals(6, onlyWhite.size)
    }

    @Test
    fun colorSimilarityIgnoresShadowButNotHue() {
        val gold = ColorMatch.Rgb(200f, 160f, 60f)
        val goldInShadow = ColorMatch.Rgb(130f, 104f, 40f)
        assertTrue(ColorMatch.similar(gold, goldInShadow))
        assertTrue(!ColorMatch.similar(gold, ColorMatch.Rgb(237f, 237f, 230f))) // branca
        assertTrue(!ColorMatch.similar(gold, ColorMatch.Rgb(46f, 107f, 50f))) // vidro verde
    }

    @Test
    fun areaFilterCountsOnlyInsideTheRectangle() {
        val centers = grid(6, 4, 32, 40, 40)
        val img = image(260, 180, centers, 14)
        // Retângulo cobrindo só as 3 primeiras colunas
        val marks = CircleCounter.detect(img, 260, 180, RadiusModel.constant(14f), accept = { x, _ -> x < 120 })
        assertEquals(12, marks.size)
    }

    @Test
    fun radiusModelInterpolatesAndClamps() {
        val m = RadiusModel.fromSamples(listOf(CountMark(0f, 200f, 20f), CountMark(0f, 0f, 10f)))
        assertEquals(15f, m.at(100f), 0.01f)
        assertEquals(10f * 0.6f, m.at(-1000f), 0.01f) // não encolhe além do limite
        assertEquals(20f * 1.5f, m.at(5000f), 0.01f) // nem cresce além
        assertEquals(12f, RadiusModel.fromSamples(listOf(CountMark(5f, 5f, 12f))).at(999f), 0.01f)
    }

    @Test
    fun emptyImageHasNoMarks() {
        assertEquals(0, CircleCounter.detect(image(100, 100, emptyList(), 10), 100, 100, 10f).size)
    }

    @Test
    fun nearestMark() {
        val marks = listOf(CountMark(10f, 10f), CountMark(50f, 50f))
        assertEquals(1, CircleCounter.nearest(marks, 48f, 52f, 10f))
        assertNull(CircleCounter.nearest(marks, 100f, 100f, 10f))
    }
}
