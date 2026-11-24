package com.tal.workhours

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AnalyticsTest {
    private val settings=AppSettings(hourlyWage=50.0,monthlyHours=182.0)
    private fun shift(date:String,hours:Int,location:String=OFFICE,startHour:Int=8,reason:String="")=LocalDate.parse(date).atTime(startHour,0).let {
        Shift(start=timestamp(it),end=timestamp(it.plusHours(hours.toLong())),location=location,reason=reason)
    }
    private fun data(vararg shifts:Shift,s:AppSettings=settings)=AppData(shifts=shifts.toList(),settings=s,setupComplete=true)
    private val p=PayPeriod(LocalDate.parse("2026-11-01"),LocalDate.parse("2026-12-01"))
    @Test fun locationAndReasonPayReconcileToPayrollWithoutRestartingBands(){
        val a=workAnalytics(data(shift("2026-11-02",8),shift("2026-11-02",3,HOME,16,"Project")),p)
        assertEquals(11*HOUR_MS,a.summary.totalMs,.01);assertEquals(3*HOUR_MS,a.summary.overtimeMs,.01)
        assertEquals(600.0,a.locations.sumOf {it.pay},.01);assertEquals(a.summary.hourlyEquivalentPay,a.locations.sumOf {it.pay},.01)
        assertEquals(200.0,a.reasons.single().pay,.01);assertEquals(4*HOUR_MS,a.weightedOvertimeMs,.01)
        assertEquals(3*HOUR_MS,a.locations.single {it.key==HOME}.overtimeMs,.01)
    }
    @Test fun workdaysAreDistinctDatesAndShiftAverageUsesSegments(){
        val a=workAnalytics(data(shift("2026-11-02",4),shift("2026-11-02",4,HOME,14)),p)
        assertEquals(1,a.recordedDays);assertEquals(2,a.shifts);assertEquals(8*HOUR_MS,a.averageDayMs,.01);assertEquals(4*HOUR_MS,a.averageShiftMs,.01);assertEquals(.5,a.homeShare!!,.0001)
    }
    @Test fun emptyPeriodKeepsRatiosUndefinedAndTrendMissingNotZeroSalary(){
        val a=workAnalytics(data(s=settings.copy(globalSalary=true,monthlySalary=12000.0)),p)
        assertNull(a.homeShare);assertNull(a.overtimeShare);assertNull(a.longestDate);assertFalse(a.previousHasRecords)
        assertEquals(12,a.trend.size);assertTrue(a.trend.none {it.hasRecords});assertEquals(0,a.recordedDays)
    }
    @Test fun activeShiftIsExcluded(){
        val d=data().copy(active=ActiveShift(timestamp(LocalDate.parse("2026-11-02").atTime(8,0)),OFFICE))
        val a=workAnalytics(d,p);assertEquals(0,a.shifts);assertEquals(0.0,a.summary.totalMs,.01)
    }
    @Test fun weekdayAverageCountsRecordedDaysNotCalendarOccurrences(){
        val a=workAnalytics(data(shift("2026-11-02",4),shift("2026-11-09",8)),p)
        val monday=a.weekdays.single {it.key=="1"};assertEquals(2,monday.days);assertEquals(6*HOUR_MS,monday.netMs/monday.days,.01)
    }
    @Test fun weeklyBandsUseWorkBeforeDisplayedPeriod(){
        val s=settings.copy(overtimeMode="WEEKLY",weeklyRegularHours=10.0,weekStartDay=1)
        val a=workAnalytics(data(shift("2026-10-30",8),shift("2026-11-01",5,HOME),s=s),p)
        assertEquals(5*HOUR_MS,a.summary.totalMs,.01);assertEquals(3*HOUR_MS,a.summary.overtimeMs,.01);assertEquals(3*HOUR_MS,a.locations.single {it.key==HOME}.overtimeMs,.01)
        assertTrue(a.previousHasRecords)
    }
    @Test fun overnightShiftFollowsAssignedWorkDate(){
        val a=workAnalytics(data(shift("2026-11-02",8,startHour=22)),p)
        assertEquals(LocalDate.parse("2026-11-02"),a.longestDate);assertEquals(1,a.recordedDays)
    }
    @Test fun dailyBreakIsAllocatedBeforeLocationAndReasonCharts(){
        val a=workAnalytics(data(shift("2026-11-02",8),shift("2026-11-02",2,HOME,16,"A"),s=settings.copy(breakMode="DAILY",breakMinutes=30)),p)
        assertEquals(9.5*HOUR_MS,a.summary.totalMs,.01);assertEquals(a.summary.totalMs,a.locations.sumOf {it.netMs},.01);assertEquals(a.summary.homeMs,a.reasons.sumOf {it.netMs},.01)
    }
    @Test fun previousPeriodFollowsCustomPayrollCutoff(){
        val custom=periodFor(LocalDate.parse("2026-11-03"),25)
        val a=workAnalytics(data(shift("2026-10-24",8),shift("2026-10-25",4),s=settings.copy(periodStartDay=25)),custom)
        assertEquals(4*HOUR_MS,a.summary.totalMs,.01);assertEquals(8*HOUR_MS,a.previous.totalMs,.01)
    }
    @Test fun reportModeAvoidsDuplicatedTrendCalculation(){assertTrue(workAnalytics(data(),p,0).trend.isEmpty())}
    @Test fun analyticsDestinationSupportsBackToSnapshot(){assertEquals(listOf(1,6),navigateAppRoutes(listOf(1),6));assertEquals(listOf(1),previousAppRoutes(listOf(1,6)))}
}
