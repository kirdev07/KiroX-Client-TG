package com.kirox.app.phone

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNumberEngineTest {
    private val engine = PhoneNumberEngine()

    @Test
    fun `shared plus seven stays unchanged while ambiguous`() {
        assertEquals("KZ", engine.detectInternationalRegion("+7", "KZ"))
        assertEquals("RU", engine.detectInternationalRegion("+7", "RU"))
    }

    @Test
    fun `full Kazakhstan number overrides Russia`() {
        assertEquals("KZ", engine.detectInternationalRegion("+7 701 123 45 67", "RU"))
    }

    @Test
    fun `full Russia number overrides Kazakhstan`() {
        assertEquals("RU", engine.detectInternationalRegion("+7 912 123-45-67", "KZ"))
    }

    @Test
    fun `international number is converted to national digits`() {
        val kz = Country("KZ", "Казахстан", 7)
        assertEquals("7011234567", engine.nationalDigits("+7 (701) 123-45-67", kz))
    }

    @Test
    fun `formatted value can be edited without duplicating digits`() {
        val kz = Country("KZ", "Казахстан", 7)
        var digits = ""
        "7011234567".forEach { entered ->
            val visibleBeforeInput = engine.formatNational(digits, kz.regionCode)
            digits = engine.nationalDigits(visibleBeforeInput + entered, kz)
        }
        assertEquals("7011234567", digits)
        assertEquals("701 123 4567", engine.formatNational(digits, kz.regionCode))
    }

    @Test
    fun `deleting from formatted number removes exactly one digit`() {
        val kz = Country("KZ", "Казахстан", 7)
        val visible = engine.formatNational("7011234567", kz.regionCode)
        assertEquals("701123456", engine.nationalDigits(visible.dropLast(1), kz))
    }
}
