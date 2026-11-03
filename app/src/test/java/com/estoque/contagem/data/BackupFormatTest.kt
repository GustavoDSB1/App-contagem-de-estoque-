package com.estoque.contagem.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class BackupFormatTest {

    @Test
    fun roundTripKeepsProductsPricesPhotosAndPeople() {
        val photo = byteArrayOf(1, 2, 3, 4, 5)
        val original = BackupData(
            products = listOf(
                BackupProduct("Coca 600", "🥤", 1, 900, photo),
                BackupProduct("Pipoca \"doce\"", "🍬", 2, 1500, null)
            ),
            people = listOf(BackupPerson("Ricardo", PersonType.EMPLOYEE), BackupPerson("Andressa", PersonType.DAY_WORKER)),
            createdAt = 123L,
            company = BackupCompany("Mercadinho Bom Preço", "12.345.678/0001-90", byteArrayOf(9, 8, 7))
        )
        val decoded = BackupFormat.decode(BackupFormat.encode(original))

        assertEquals(2, decoded.products.size)
        assertEquals("Coca 600", decoded.products[0].name)
        assertEquals(900L, decoded.products[0].priceCents)
        assertArrayEquals(photo, decoded.products[0].photoJpeg)
        assertEquals("Pipoca \"doce\"", decoded.products[1].name)
        assertNull(decoded.products[1].photoJpeg)
        assertEquals(original.people, decoded.people)
        assertEquals(123L, decoded.createdAt)
        assertEquals("Mercadinho Bom Preço", decoded.company?.name)
        assertEquals("12.345.678/0001-90", decoded.company?.document)
        assertArrayEquals(byteArrayOf(9, 8, 7), decoded.company?.logoPng)
    }

    @Test
    fun rejectsOtherFiles() {
        for (text in listOf("isso não é json", "{\"produtos\": []}", "[]")) {
            try {
                BackupFormat.decode(text)
                fail("deveria rejeitar: $text")
            } catch (e: BackupException) {
                // ok
            }
        }
    }
}
