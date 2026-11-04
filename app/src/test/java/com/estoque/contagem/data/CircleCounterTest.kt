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
