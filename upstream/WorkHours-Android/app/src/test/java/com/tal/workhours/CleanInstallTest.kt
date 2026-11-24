package com.tal.workhours

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CleanInstallTest {
    @Test fun newInstallHasNoUserRecordsOrFinancialInputs() {
        val fresh = AppData()
        fresh.validate()
        assertTrue(fresh.shifts.isEmpty())
        assertNull(fresh.active)
        assertFalse(fresh.setupComplete)
        assertTrue(fresh.settings.reasons.isEmpty())
        assertEquals(0.0, fresh.settings.monthlySalary, 0.0)
        assertEquals(0.0, fresh.settings.monthlyHours, 0.0)
        assertEquals(0.0, fresh.settings.hourlyWage, 0.0)
        assertNull(fresh.settings.creditPoints)
        assertNull(fresh.settings.pensionPercent)
        assertEquals(1, fresh.settings.periodStartDay)
        assertEquals(fresh, decodeData(encodeData(fresh)))
    }
    @Test fun setupCannotBeCompletedWithAnEmptyMonthlyQuota() {
        try { AppData(setupComplete = true).validate(); fail("empty setup accepted") }
        catch (_: IllegalArgumentException) { }
        AppData(settings = AppSettings(monthlyHours = 200.0, hourlyWage = 50.0), setupComplete = true).validate()
    }
    @Test fun previousBackupsKeepTheirSettingsAndRecords() {
        val previous = AppData(settings = AppSettings(globalSalary = true, monthlySalary = 10000.0, monthlyHours = 200.0), setupComplete = true)
        val json = JSONObject(encodeData(previous))
        json.remove("setupComplete")
        val restored = decodeData(json.toString())
        assertTrue(restored.setupComplete)
        assertEquals(previous.settings, restored.settings)
    }
}
