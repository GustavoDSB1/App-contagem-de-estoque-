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
                BackupProduct(
                    "Coca 600", "🥤", 1, 900, photo,
                    unit = MeasureUnit.L, category = "Bebidas", barcode = "7894900011517"
                ),
                BackupProduct("Pipoca \"doce\"", "🍬", 2, 1500, null)
            ),
            people = listOf(BackupPerson("Ricardo", PersonType.EMPLOYEE.id), BackupPerson("Andressa", "g1")),
            groups = listOf(PersonType.EMPLOYEE, PersonType("g1", "cliente", "🛒")),
            messageTemplate = com.estoque.contagem.consumo.MessageTemplate(footer = "Total: {total}"),
            createdAt = 123L,
            company = BackupCompany("Mercadinho Bom Preço", "12.345.678/0001-90", byteArrayOf(9, 8, 7))
        )
        val decoded = BackupFormat.decode(BackupFormat.encode(original))

        assertEquals(2, decoded.products.size)
        assertEquals("Coca 600", decoded.products[0].name)
        assertEquals(900L, decoded.products[0].priceCents)
        assertArrayEquals(photo, decoded.products[0].photoJpeg)
        assertEquals(MeasureUnit.L, decoded.products[0].unit)
        assertEquals("Bebidas", decoded.products[0].category)
        assertEquals("7894900011517", decoded.products[0].barcode)
        assertEquals(MeasureUnit.UN, decoded.products[1].unit)
        assertEquals("", decoded.products[1].category)
        assertEquals("Pipoca \"doce\"", decoded.products[1].name)
        assertNull(decoded.products[1].photoJpeg)
        assertEquals(original.people, decoded.people)
        assertEquals(123L, decoded.createdAt)
        assertEquals(listOf("EMPLOYEE", "g1"), decoded.groups.map { it.id })
        assertEquals("cliente", decoded.groups[1].label)
        assertEquals("Total: {total}", decoded.messageTemplate?.footer)
        assertEquals("Mercadinho Bom Preço", decoded.company?.name)
        assertEquals("12.345.678/0001-90", decoded.company?.document)
        assertArrayEquals(byteArrayOf(9, 8, 7), decoded.company?.logoPng)
    }

    @Test
    fun oldBackupWithoutGroupsGetsDefaults() {
        val old = "{\"tipo\":\"contagem-estoque-backup\",\"versao\":1,\"produtos\":[]," +
            "\"pessoas\":[{\"nome\":\"Ana\",\"tipo\":\"DAY_WORKER\"}]}"
        val decoded = BackupFormat.decode(old)
        assertEquals(PersonType.DEFAULTS, decoded.groups)
        assertEquals("DAY_WORKER", decoded.people.single().typeId)
        assertNull(decoded.messageTemplate)
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
