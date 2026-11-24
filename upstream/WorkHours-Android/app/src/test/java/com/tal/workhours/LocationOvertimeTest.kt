package com.tal.workhours

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class LocationOvertimeTest {
    private val settings=AppSettings(hourlyWage=50.0,monthlyHours=200.0,
        tiers=listOf(OvertimeTier(2.0,125.0),OvertimeTier(2.0,150.0),OvertimeTier(null,200.0)))
    private fun at(day: String,hour: Double)=LocalDate.parse(day).atStartOfDay(ZoneId.of("Asia/Jerusalem")).toInstant().toEpochMilli()+(hour*HOUR_MS).toLong()
    private fun shift(from: Double,to: Double,location: String,day: String="2026-10-20")=Shift(start=at(day,from),end=at(day,to),location=location,workDate=day)
    private fun summary(shifts: List<Shift>,s: AppSettings=settings)=summarize(AppData(shifts=shifts,settings=s),periodFromMonth(YearMonth.of(2026,10),1))
    private fun near(expected: Double,actual: Double)=assertEquals(expected,actual,.000001)
    private fun allocation(sum: PeriodSummary,loc: String,hours: List<Double>,pay: Double) {
        val a=sum.overtimeAt(loc);assertEquals(hours,a.tierMs.map {it/HOUR_MS});near(hours.sum(),a.totalMs/HOUR_MS);near(pay,a.totalPay)
    }
    @Test fun officeTenThenHomeTwoSplitsFirstAndSecondBands() {
        val s=summary(listOf(shift(6.0,16.0,OFFICE),shift(18.0,20.0,HOME)))
        allocation(s,OFFICE,listOf(2.0,0.0,0.0),125.0)
        allocation(s,HOME,listOf(0.0,2.0,0.0),150.0)
    }
    @Test fun homeHoursBeforeOfficeContributeToSharedEightHourThreshold() {
        val s=summary(listOf(shift(6.0,10.0,HOME),shift(12.0,18.0,OFFICE)))
        allocation(s,HOME,listOf(0.0,0.0,0.0),0.0)
        allocation(s,OFFICE,listOf(2.0,0.0,0.0),125.0)
        near(0.0,s.officeOvertimeMs) // Existing office-only comparison is NOT the new allocation.
    }
    @Test fun eightOfficeHoursThenThreeHomeHoursAllOvertimeBelongsHome() {
        val s=summary(listOf(shift(8.0,16.0,OFFICE),shift(18.0,21.0,HOME)))
        allocation(s,OFFICE,listOf(0.0,0.0,0.0),0.0)
        allocation(s,HOME,listOf(2.0,1.0,0.0),200.0)
    }
    @Test fun interleavedLocationsPreserveChronologicalBandConsumption() {
        val s=summary(listOf(shift(6.0,12.0,OFFICE),shift(13.0,16.0,HOME),shift(17.0,22.0,OFFICE)))
        allocation(s,OFFICE,listOf(1.0,2.0,2.0),412.5)
        allocation(s,HOME,listOf(1.0,0.0,0.0),62.5)
    }
    @Test fun reversedInputOrderDoesNotReverseDailyAllocation() {
        val shifts=listOf(shift(6.0,16.0,OFFICE),shift(18.0,20.0,HOME))
        val a=summary(shifts);val b=summary(shifts.reversed())
        for(loc in listOf(OFFICE,HOME))assertEquals(a.overtimeAt(loc),b.overtimeAt(loc))
    }
    @Test fun dailyBreakAlreadyDeductedOnlyOnceBeforeLocationAllocation() {
        val s=summary(listOf(shift(6.0,15.0,OFFICE),shift(17.0,21.0,HOME)),settings.copy(breakMode="DAILY",breakMinutes=30,deductHomeBreak=true))
        allocation(s,OFFICE,listOf(.5,0.0,0.0),31.25)
        allocation(s,HOME,listOf(1.5,2.0,.5),293.75)
    }
    @Test fun perSegmentBreaksAreNotDeductedAgainByReport() {
        val s=summary(listOf(shift(6.0,15.0,OFFICE),shift(17.0,21.0,HOME)),settings.copy(breakMode="SEGMENT",breakMinutes=30,deductHomeBreak=true))
        allocation(s,OFFICE,listOf(.5,0.0,0.0),31.25)
        allocation(s,HOME,listOf(1.5,2.0,0.0),243.75)
    }
    @Test fun exemptHomeBreakDoesNotConsumeTheOfficeDailyBreak() {
        val s=summary(listOf(shift(5.0,14.0,HOME),shift(16.0,18.0,OFFICE)),settings.copy(breakMode="DAILY",breakMinutes=30,deductHomeBreak=false))
        allocation(s,HOME,listOf(1.0,0.0,0.0),62.5)
        allocation(s,OFFICE,listOf(1.0,.5,0.0),100.0)
    }
    @Test fun globalSalaryUsesMonthlySalaryDividedByRequiredHoursAndFullBandPay() {
        val s=summary(listOf(shift(6.0,16.0,OFFICE),shift(18.0,20.0,HOME)),settings.copy(globalSalary=true,monthlySalary=10000.0,hourlyWage=999.0))
        allocation(s,OFFICE,listOf(2.0,0.0,0.0),125.0);allocation(s,HOME,listOf(0.0,2.0,0.0),150.0)
        near(10275.0,s.gross)
    }
    @Test fun arbitraryConfiguredTierLengthsAndPercentagesAreUsed() {
        val cfg=settings.copy(regularHours=7.0,tiers=listOf(OvertimeTier(1.5,133.3),OvertimeTier(2.25,175.5),OvertimeTier(null,225.0)))
        val s=summary(listOf(shift(7.0,15.5,OFFICE),shift(17.0,21.25,HOME)),cfg)
        allocation(s,OFFICE,listOf(1.5,0.0,0.0),99.975)
        allocation(s,HOME,listOf(0.0,2.25,2.0),422.4375)
    }
    @Test fun dailyOvertimeBandsResetBetweenDaysNotBetweenLocations() {
        val s=summary(listOf(shift(6.0,16.0,OFFICE),shift(6.0,16.0,HOME,"2026-10-21")))
        allocation(s,OFFICE,listOf(2.0,0.0,0.0),125.0);allocation(s,HOME,listOf(2.0,0.0,0.0),125.0)
    }
    @Test fun recordedExcelDurationIsRespectedInsteadOfRawClockDifference() {
        val excel=shift(8.0,17.0,OFFICE).copy(source="excel",recordedDurationMs=(8*HOUR_MS).toLong())
        val s=summary(listOf(excel,shift(18.0,20.0,HOME)))
        allocation(s,OFFICE,listOf(0.0,0.0,0.0),0.0);allocation(s,HOME,listOf(2.0,0.0,0.0),125.0)
    }
    @Test fun runningShiftUsesSameProvisionalAllocationWithoutAppendingRecord() {
        val data=AppData(shifts=listOf(shift(8.0,16.0,OFFICE)),active=ActiveShift(at("2026-10-20",18.0),HOME),settings=settings)
        val s=summarize(data,periodFromMonth(YearMonth.of(2026,10),1),at("2026-10-20",21.0))
        allocation(s,HOME,listOf(2.0,1.0,0.0),200.0);assertEquals(1,data.shifts.size);assertNotNull(data.active)
    }
    @Test fun selectedPayPeriodExcludesAdjacentPeriods() {
        val data=AppData(shifts=listOf(shift(6.0,16.0,OFFICE,"2026-10-24"),shift(18.0,20.0,HOME,"2026-10-24"),shift(4.0,20.0,HOME,"2026-10-25")),settings=settings.copy(periodStartDay=25))
        val s=summarize(data,periodFromMonth(YearMonth.of(2026,9),25))
        allocation(s,OFFICE,listOf(2.0,0.0,0.0),125.0);allocation(s,HOME,listOf(0.0,2.0,0.0),150.0)
    }
    @Test fun emptyAndRegularOnlyWorkStillHaveAllConfiguredZeroBands() {
        for(s in listOf(summary(emptyList()),summary(listOf(shift(8.0,12.0,OFFICE),shift(13.0,17.0,HOME)))))
            for(loc in listOf(OFFICE,HOME))allocation(s,loc,listOf(0.0,0.0,0.0),0.0)
    }
    @Test fun zeroWageKeepsOvertimeHoursWithoutInventingPay() {
        val s=summary(listOf(shift(6.0,18.0,HOME)),settings.copy(hourlyWage=0.0))
        allocation(s,HOME,listOf(2.0,2.0,0.0),0.0)
    }
    @Test fun locationTotalsConserveEveryOverallBandAndAllOvertimePay() {
        for(mode in listOf("NONE","DAILY","SEGMENT"))for(homeBreak in listOf(false,true)) {
            val s=summary(listOf(shift(5.0,10.0,HOME),shift(11.0,16.0,OFFICE),shift(17.0,22.0,HOME),shift(6.0,19.0,OFFICE,"2026-10-21")),settings.copy(breakMode=mode,deductHomeBreak=homeBreak))
            val o=s.overtimeAt(OFFICE);val h=s.overtimeAt(HOME)
            for(i in settings.tiers.indices)near(s.tierMs[i],o.tierMs[i]+h.tierMs[i])
            near(s.overtimeMs,o.totalMs+h.totalMs);near(s.overtimePay,o.totalPay+h.totalPay)
            near(s.basePay+s.overtimePay,s.gross)
        }
    }
    @Test fun unknownLocationIsRejectedRatherThanSilentlyDroppingOvertime() {
        assertTrue(runCatching{summary(emptyList()).overtimeAt("OTHER")}.isFailure)
    }
}
