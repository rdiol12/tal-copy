package com.tal.workhours

import org.junit.Assert.*
import org.junit.Test

class LocalizedInputTest {
    @Test fun decimalWagesAcceptArabicAndHindiDigits() {
        assertEquals(1234.5, parseNumeric("١٬٢٣٤٫٥"), 0.0001)
        assertEquals(42.75, parseNumeric("४२.७५"), 0.0001)
        assertEquals(8.5, parseNumeric("8,5"), 0.0001)
    }
    @Test fun shiftHoursAcceptLocalizedTimeSeparators() {
        assertEquals(8.5, parseHours("०८:३०"), 0.0001)
        assertEquals(8.5, parseHours("０８：３０"), 0.0001)
        assertEquals(8.5, parseHours("٨:٣٠"), 0.0001)
        assertEquals(25, "٢٥".asLocalizedInt())
    }
    @Test fun invalidMinutesStillRejected() {
        assertThrows(IllegalArgumentException::class.java) { parseHours("٨:٦٠") }
        assertThrows(IllegalStateException::class.java) { parseNumeric("NaN") }
    }
}
