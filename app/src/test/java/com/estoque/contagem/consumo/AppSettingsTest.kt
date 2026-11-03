package com.estoque.contagem.consumo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    @Test
    fun defaultsForOldFilesWithoutSettings() {
        val s = AppSettings.fromJson(null)
        assertTrue(s.consumptionEnabled)
        assertFalse(s.onboardingDone)
        assertEquals("system", s.theme)
        assertFalse(s.exportWithValues)
        assertEquals(MessageTemplate.DEFAULT, s.messageTemplate)
    }

    @Test
    fun roundTrip() {
        val original = AppSettings(
            consumptionEnabled = false,
            messageTemplate = MessageTemplate(header = "Consumo {tipo}", line = "{nome}: {valor}", blankLineBetweenPeople = false, footer = "Total {total}"),
            onboardingDone = true,
            theme = "dark",
            exportWithValues = true
        )
        assertEquals(original, AppSettings.fromJson(JSONObject(original.toJson().toString())))
    }

    @Test
    fun invalidThemeFallsBackToSystem() {
        assertEquals("system", AppSettings.fromJson(JSONObject().put("theme", "roxo")).theme)
    }

    @Test
    fun emptyLineTemplateFallsBackToDefault() {
        val t = MessageTemplate.fromJson(JSONObject().put("line", "  "))
        assertEquals(MessageTemplate.DEFAULT_LINE, t.line)
    }
}
