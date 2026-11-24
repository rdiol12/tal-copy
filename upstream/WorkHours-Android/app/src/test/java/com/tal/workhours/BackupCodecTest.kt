package com.tal.workhours

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    private fun sample() = AppData(
        shifts = listOf(Shift(id = "fixed-id", start = 1_770_000_000_000, end = 1_770_028_800_000, location = HOME,
            reason = "משימת בדיקה", note = "לא הוחתם יציאה", source = "excel", workDate = "2026-02-02")),
        settings = AppSettings(creditPoints = 2.25, pensionPercent = 6.0, studyPercent = 2.5,
            reasons = listOf("משימת בדיקה", "סיבה חופשית")),
        importedIds = setOf("excel-1"), importAttempted = true, importReport = "יובא בהצלחה"
    )
    @Test fun fullBackupRoundTripPreservesSettingsReasonsAndSourceIds() {
        val original = sample()
        assertEquals(original, readBackup(backupText(original)))
    }
    @Test fun restorePreservesEveryCustomizedPayrollSetting() {
        for (global in listOf(true, false)) for (breaks in listOf("NONE", "DAILY", "SEGMENT")) {
            val settings = AppSettings(
                regularHours = 7.5,
                tiers = listOf(OvertimeTier(1.5, 125.0), OvertimeTier(2.0, 150.0), OvertimeTier(null, 200.0)),
                globalSalary = global, monthlySalary = 12000.0, monthlyHours = 182.5, hourlyWage = 65.75,
                periodStartDay = 25, breakMode = breaks, breakMinutes = 45, deductHomeBreak = true,
                creditPoints = 3.75, pensionPercent = 7.0, studyPercent = 2.5,
                contributionBasis = "CUSTOM", customBasis = 10500.0, pensionTaxCredit = false,
                reasons = listOf("Support", "Maintenance"), workDays = setOf(1, 3, 5)
            )
            val original = sample().copy(settings = settings, setupComplete = true,
                active = ActiveShift(1_770_030_000_000, HOME, "Support", "Open shift"))
            val restored = readBackup(backupText(original))
            assertEquals(original, restored)
            assertEquals(calculateDays(original.shifts, settings), calculateDays(restored.shifts, restored.settings))
        }
    }
    @Test fun corruptionIsRejectedBeforeRestore() {
        val backup = JSONObject(backupText(sample()))
        backup.put("payload", backup.getString("payload").replace("fixed-id", "changed-id"))
        try { readBackup(backup.toString()); fail("tampering accepted") } catch (_: IllegalArgumentException) { }
    }
    @Test fun unsupportedVersionsAreRejected() {
        val json = JSONObject(encodeData(sample())).put("version", 999)
        try { decodeData(json.toString()); fail("future version accepted") } catch (_: IllegalArgumentException) { }
    }
    @Test fun openShiftAlsoSurvivesBackup() {
        val original = sample().copy(active = ActiveShift(1_770_030_000_000, OFFICE))
        assertEquals(original, readBackup(backupText(original)))
    }
}
