package com.danimanx.replicantsynth.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FxCatalogTest {

    @Test
    fun catalogCoversAll48FirmwareSlots() {
        assertEquals(48, FX_CATALOG.size)
        assertEquals((0 until 48).toList(), FX_CATALOG.map { it.id })
    }

    @Test
    fun everyEntryHasSixParameters() {
        FX_CATALOG.forEach { info ->
            assertTrue(info.name.isNotBlank())
            assertTrue(info.code.isNotBlank())
            assertEquals(6, info.labels.size)
            assertEquals(6, info.defaults.size)
            assertTrue(info.labels.all { it.isNotBlank() })
            assertTrue(info.defaults.all { it in 0f..1f })
        }
    }

    @Test
    fun knownEffectsKeepTheirFirmwareCodes() {
        assertEquals("F+D", fxById(1).code)
        assertEquals("ISO", fxById(3).code)
        assertEquals("CRS", fxById(4).code)
        assertEquals("RES", fxById(12).code)
    }
}
