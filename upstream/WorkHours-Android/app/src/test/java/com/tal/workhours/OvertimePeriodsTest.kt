package com.tal.workhours

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class OvertimePeriodsTest {
    private fun day(s:String)=LocalDate.parse(s)
    private fun shift(date:String,hours:Double=8.0,where:String=OFFICE,startHour:Int=7):Shift {
        val start=timestamp(day(date).atTime(startHour,0))
        return Shift(start=start,end=start+(hours*HOUR_MS).toLong(),location=where,workDate=date)
    }
    private fun settings(mode:String="WEEKLY",threshold:Double=40.0)=AppSettings(overtimeMode=mode,weeklyRegularHours=threshold,monthlyRegularHours=threshold,weekStartDay=1,hourlyWage=100.0)
    private fun data(rows:List<Shift>,s:AppSettings=settings())=AppData(shifts=rows,settings=s)
    private val october=PayPeriod(day("2026-10-01"),day("2026-11-01"))
    private fun hours(ms:Double)=ms/HOUR_MS
    private fun summary(rows:List<Shift>,s:AppSettings=settings(),p:PayPeriod=october)=summarize(data(rows,s),p)
    private val fourLongDays=listOf("2026-10-19","2026-10-20","2026-10-21","2026-10-22").map{shift(it,10.0)}
    @Test fun dailyRemainsDefault(){assertEquals("DAILY",AppSettings().overtimeMode)}
    @Test fun weeklyLongDaysCanAllBeRegular(){val s=summary(fourLongDays);assertEquals(40.0,hours(s.regularMs),0.0);assertEquals(0.0,s.overtimeMs,0.0)}
    @Test fun sameRecordsHaveDailyOvertime(){val s=summary(fourLongDays,settings("DAILY"));assertEquals(8.0,hours(s.overtimeMs),0.0)}
    @Test fun weeklyCrossingAllocatesToLaterDay(){val s=summary(fourLongDays+shift("2026-10-23",5.0));assertEquals(40.0,hours(s.regularMs),0.0);assertEquals(5.0,hours(s.overtimeMs),0.0);assertTrue(s.days.take(4).all{it.tierMs.sum()==0.0});assertEquals(5.0,hours(s.days.last().tierMs.sum()),0.0)}
    @Test fun weeklyPayUsesConfiguredBands(){val s=summary(fourLongDays+shift("2026-10-23",5.0));assertEquals(2.0,hours(s.tierMs[0]),0.0);assertEquals(3.0,hours(s.tierMs[1]),0.0);assertEquals(4700.0,s.gross,0.0)}
    @Test fun weeklyCounterResetsOnMonday(){val s=summary(fourLongDays+shift("2026-10-23",8.0)+shift("2026-10-26",8.0));assertEquals(8.0,hours(s.overtimeMs),0.0);assertEquals(8.0,hours(s.days.last().regularMs),0.0)}
    @Test fun sundayWeekStartCanBeSelected(){val s=settings().copy(weekStartDay=7);assertEquals(day("2026-10-18"),overtimeBucketStart(day("2026-10-24"),s));assertEquals(day("2026-10-25"),overtimeBucketStart(day("2026-10-25"),s))}
    @Test fun mondayWeekStartDiffersFromSunday(){assertEquals(day("2026-10-19"),overtimeBucketStart(day("2026-10-25"),settings()))}
    @Test fun anyWeekStartDayIsSupported(){for(d in 1..7){val s=settings().copy(weekStartDay=d);val b=overtimeBucketStart(day("2026-10-25"),s);assertEquals(d,b.dayOfWeek.value);assertTrue(day("2026-10-25").toEpochDay()-b.toEpochDay() in 0..6)}}
    @Test fun weeklyContextBeforeDisplayedPayPeriodCounts(){val rows=listOf("2026-10-19","2026-10-20","2026-10-21").map{shift(it,10.0)}+listOf(shift("2026-10-22"),shift("2026-10-23"));val p=PayPeriod(day("2026-10-22"),day("2026-11-22"));val s=summary(rows,p=p);assertEquals(2,s.days.size);assertEquals(16.0,hours(s.totalMs),0.0);assertEquals(6.0,hours(s.overtimeMs),0.0);assertEquals(1850.0,s.gross,0.0)}
    @Test fun contextHoursAreNotPaidAgainInNewPeriod(){val rows=fourLongDays+shift("2026-10-23",8.0);val p=PayPeriod(day("2026-10-23"),day("2026-11-23"));val s=summary(rows,p=p);assertEquals(8.0,hours(s.totalMs),0.0);assertEquals(1150.0,s.gross,0.0)}
    @Test fun weekAcrossYearBoundaryDoesNotReset(){val rows=listOf("2025-12-29","2025-12-30","2025-12-31").map{shift(it,12.0)}+shift("2026-01-01",8.0);val s=summary(rows,p=PayPeriod(day("2026-01-01"),day("2026-02-01")));assertEquals(4.0,hours(s.overtimeMs),0.0)}
    @Test fun monthlyPayPeriodStartsAtConfiguredDay(){val s=settings("MONTHLY").copy(periodStartDay=25);assertEquals(day("2026-10-25"),overtimeBucketStart(day("2026-11-03"),s));assertEquals(day("2026-09-25"),overtimeBucketStart(day("2026-10-24"),s))}
    @Test fun calendarMonthCanBeSelected(){val s=settings("MONTHLY").copy(periodStartDay=25,overtimeMonthBasis="CALENDAR");assertEquals(day("2026-11-01"),overtimeBucketStart(day("2026-11-03"),s))}
    @Test fun calendarContextBeforePayPeriodCounts(){val s=settings("MONTHLY").copy(periodStartDay=25,overtimeMonthBasis="CALENDAR");val rows=listOf("2026-10-19","2026-10-20","2026-10-21","2026-10-22").map{shift(it,10.0)}+listOf(shift("2026-10-25"),shift("2026-11-01"));val result=summary(rows,s,PayPeriod(day("2026-10-25"),day("2026-11-25")));assertEquals(16.0,hours(result.totalMs),0.0);assertEquals(8.0,hours(result.overtimeMs),0.0);assertEquals(8.0,hours(result.days.last().regularMs),0.0)}
    @Test fun payPeriodModeDoesNotUsePriorPayPeriodHours(){val s=settings("MONTHLY").copy(periodStartDay=25);val rows=fourLongDays+listOf(shift("2026-10-25"),shift("2026-11-01"));assertEquals(0.0,summary(rows,s,PayPeriod(day("2026-10-25"),day("2026-11-25"))).overtimeMs,0.0)}
    @Test fun monthlyThresholdSeparateFromAttendanceTarget(){val s=settings("MONTHLY",20.0).copy(monthlyHours=182.0);val result=summary(listOf(shift("2026-10-19",12.0),shift("2026-10-20",12.0)),s);assertEquals(4.0,hours(result.overtimeMs),0.0);assertEquals(158.0,hours(result.monthlyShortageMs),0.0)}
    @Test fun shortMonthPayPeriodResetsOnClampedStart(){val s=settings("MONTHLY",20.0).copy(periodStartDay=31);val rows=listOf(shift("2026-02-27",20.0, startHour=0),shift("2026-02-28",20.0,startHour=0));val result=summary(rows,s,PayPeriod(day("2026-02-01"),day("2026-03-01")));assertEquals(0.0,result.overtimeMs,0.0)}
    @Test fun leapDayCalendarBucket(){val s=settings("MONTHLY").copy(overtimeMonthBasis="CALENDAR");assertEquals(day("2028-02-01"),overtimeBucketStart(day("2028-02-29"),s));assertEquals(day("2028-03-01"),overtimeBucketStart(day("2028-03-01"),s))}
    @Test fun weeklyAndMonthlyRetainDailyBreaks(){for(mode in listOf("WEEKLY","MONTHLY")){val s=settings(mode).copy(breakMode="DAILY",breakMinutes=30);val result=summary(fourLongDays,s);assertEquals(38.0,hours(result.totalMs),0.0);assertEquals(0.0,result.overtimeMs,0.0)}}
    @Test fun segmentBreaksStayPerSegment(){val rows=listOf(shift("2026-10-19",5.0,OFFICE,0),shift("2026-10-19",5.0,HOME,6));val s=settings(threshold=8.0).copy(breakMode="SEGMENT",breakMinutes=30,deductHomeBreak=true);val result=summary(rows,s);assertEquals(9.0,hours(result.totalMs),0.0);assertEquals(1.0,hours(result.overtimeMs),0.0)}
    @Test fun officeAndHomeShareWeeklyCounter(){val result=summary(listOf(shift("2026-10-19",8.0,HOME),shift("2026-10-20",8.0,OFFICE)),settings(threshold=8.0));assertEquals(8.0,hours(result.overtimeAt(OFFICE).totalMs),0.0);assertEquals(0.0,result.overtimeAt(HOME).totalMs,0.0)}
    @Test fun officeOnlyComparisonIsIndependentOfHome(){val result=summary(listOf(shift("2026-10-19",8.0,HOME),shift("2026-10-20",8.0,OFFICE)),settings(threshold=8.0));assertEquals(0.0,result.officeOvertimeMs,0.0)}
    @Test fun officeOnlyComparisonCarriesAcrossPayBoundary(){val rows=listOf(shift("2026-10-19",10.0),shift("2026-10-20",4.0));val result=summary(rows,settings(threshold=8.0),PayPeriod(day("2026-10-20"),day("2026-11-20")));assertEquals(4.0,hours(result.officeOvertimeMs),0.0)}
    @Test fun officeOnlyComparisonDoesNotDeductSharedBreakTwice(){val rows=listOf(shift("2026-10-19",2.0,HOME,0),shift("2026-10-19",8.0,OFFICE,3));val result=summary(rows,settings(threshold=7.0).copy(breakMode="DAILY",breakMinutes=60,deductHomeBreak=true));assertEquals(1.0,hours(result.officeOvertimeMs),0.0)}
    @Test fun filteringOfficeExportCannotRestartWeeklyThreshold(){val rows=listOf(shift("2026-10-19",8.0,HOME),shift("2026-10-20",8.0,OFFICE));val p=shiftExportParts(data(rows,settings(threshold=8.0)),october,OFFICE).single();assertEquals(0.0,p.regularMs,0.0);assertEquals(8.0,hours(p.tierMs.sum()),0.0)}
    @Test fun leaveDoesNotConsumeWeeklyThreshold(){val s=settings(threshold=8.0);val d=data(listOf(shift("2026-10-19",4.0)),s).copy(leaveConfig=LeaveConfig(openingDate="2026-10-01",vacationOpening=10.0),leaveRecords=listOf(LeaveRecord(date="2026-10-20",type=VACATION),LeaveRecord(date="2026-10-21",type=HOLIDAY)));val result=summarize(d,october);assertEquals(0.0,result.overtimeMs,0.0);assertEquals(4.0,hours(result.totalMs),0.0)}
    @Test fun leaveDoesNotConsumeMonthlyThreshold(){val d=data(listOf(shift("2026-10-19",4.0)),settings("MONTHLY",8.0)).copy(leaveConfig=LeaveConfig(openingDate="2026-10-01",vacationOpening=10.0),leaveRecords=listOf(LeaveRecord(date="2026-10-20",type=VACATION)));assertEquals(0.0,summarize(d,october).overtimeMs,0.0)}
    @Test fun activeShiftUsesPriorWeeklyContext(){val d=data(fourLongDays.take(3)).copy(active=ActiveShift(timestamp(LocalDateTime.parse("2026-10-22T07:00")),OFFICE));val result=summarize(d,october,timestamp(LocalDateTime.parse("2026-10-22T19:00")));assertEquals(2.0,hours(result.overtimeMs),0.0);assertEquals(0.0,summarize(d,october).overtimeMs,0.0)}
    @Test fun activeShiftBeforePayPeriodCountsAsContext(){val d=data(emptyList(),settings(threshold=8.0)).copy(active=ActiveShift(timestamp(LocalDateTime.parse("2026-10-19T07:00")),OFFICE));val p=PayPeriod(day("2026-10-20"),day("2026-11-20"));val result=summarize(d,p,timestamp(LocalDateTime.parse("2026-10-19T19:00")));assertEquals(0.0,result.totalMs,0.0)}
    @Test fun recordedExcelDurationStillUsed(){val r=shift("2026-10-19",3.0).copy(source="excel",recordedDurationMs=(2*HOUR_MS).toLong());val result=summary(listOf(r),settings(threshold=2.5));assertEquals(2.0,hours(result.regularMs),0.0);assertEquals(0.0,result.overtimeMs,0.0)}
    @Test fun overnightShiftRetainsAssignedDate(){val r=shift("2026-10-25",3.0,OFFICE,0).copy(workDate="2026-10-24");assertEquals(day("2026-10-19"),overtimeBucketStart(day(r.workDate),settings()))}
    @Test fun reverseInputOrderDoesNotChangeAllocation(){val rows=fourLongDays+shift("2026-10-23",8.0);assertEquals(summary(rows).days,summary(rows.reversed()).days)}
    @Test fun fractionalWeeklyThreshold(){val rows=fourLongDays;val result=summary(rows,settings(threshold=37.5));assertEquals(2.5,hours(result.overtimeMs),1e-9)}
    @Test fun allBandsReconcileWithNetDuration(){val rows=fourLongDays+shift("2026-10-23",10.0);for(mode in listOf("DAILY","WEEKLY","MONTHLY")){val result=summary(rows,settings(mode));assertEquals(result.totalMs,result.regularMs+result.tierMs.sum(),1e-6)}}
    @Test fun tiersRestartEachWeek(){val rows=fourLongDays+shift("2026-10-23",3.0)+fourLongDays.map {it.copy(id=java.util.UUID.randomUUID().toString(),start=it.start+7*86400000L,end=it.end+7*86400000L,workDate=day(it.workDate).plusDays(7).toString())}+shift("2026-10-30",3.0);val result=summary(rows);assertEquals(4.0,hours(result.tierMs[0]),0.0);assertEquals(2.0,hours(result.tierMs[1]),0.0)}
    @Test fun thirdTierCanApplyWeekly(){val s=settings(threshold=8.0).copy(tiers=listOf(OvertimeTier(2.0,125.0),OvertimeTier(2.0,150.0),OvertimeTier(null,200.0)));val result=summary(listOf(shift("2026-10-19",8.0),shift("2026-10-20",8.0)),s);assertEquals(listOf(2.0,2.0,4.0),result.tierMs.map(::hours));assertEquals(2150.0,result.gross,0.0)}
    @Test fun invalidModeRejected(){assertThrows(IllegalArgumentException::class.java){AppSettings(overtimeMode="YEARLY").validate(true)}}
    @Test fun weeklyThresholdMustBeExplicitlyPositive(){assertThrows(IllegalArgumentException::class.java){AppSettings(overtimeMode="WEEKLY").validate(true)}}
    @Test fun monthlyThresholdMustBeExplicitlyPositive(){assertThrows(IllegalArgumentException::class.java){AppSettings(overtimeMode="MONTHLY").validate(true)}}
    @Test fun thresholdsCannotExceedPeriodCapacity(){assertThrows(IllegalArgumentException::class.java){settings(threshold=169.0).validate(true)};assertThrows(IllegalArgumentException::class.java){settings("MONTHLY").copy(monthlyRegularHours=745.0).validate(true)}}
    @Test fun nanThresholdRejected(){assertThrows(IllegalArgumentException::class.java){settings(threshold=Double.NaN).validate(true)}}
    @Test fun invalidWeekStartRejected(){assertThrows(IllegalArgumentException::class.java){settings().copy(weekStartDay=0).validate(true)}}
    @Test fun invalidMonthBasisRejected(){assertThrows(IllegalArgumentException::class.java){settings().copy(overtimeMonthBasis="UNKNOWN").validate(true)}}
    @Test fun fullBackupRetainsAllMethodsAndLeave(){for(mode in listOf("DAILY","WEEKLY","MONTHLY")){val d=data(fourLongDays,settings(mode).copy(overtimeMonthBasis="CALENDAR",weekStartDay=2)).copy(leaveConfig=LeaveConfig(openingDate="2026-10-01",vacationOpening=2.0),leaveRecords=listOf(LeaveRecord(date="2026-10-24",type=VACATION)));assertEquals(d,readBackup(backupText(d)))}}
    @Test fun legacyTwentyOneDefaultsToDaily(){val o=org.json.JSONObject(encodeData(data(fourLongDays,settings("DAILY"))));o.put("version",21);val s=o.getJSONObject("settings");listOf("overtimeMode","weeklyRegularHours","monthlyRegularHours","weekStartDay","overtimeMonthBasis").forEach{s.remove(it)};val restored=decodeData(o.toString());assertEquals("DAILY",restored.settings.overtimeMode);assertEquals(8.0,hours(summarize(restored,october).overtimeMs),0.0)}
    @Test fun modernBackupCannotDropNewMode(){val o=org.json.JSONObject(encodeData(data(emptyList())));o.getJSONObject("settings").remove("overtimeMode");assertThrows(org.json.JSONException::class.java){decodeData(o.toString())}}
    @Test fun changingMethodRecalculatesExistingRecords(){val d=data(fourLongDays,settings("DAILY"));assertEquals(8.0,hours(summarize(d,october).overtimeMs),0.0);assertEquals(0.0,summarize(d.copy(settings=settings()),october).overtimeMs,0.0)}
    @Test fun globalSalaryOnlyAddsOvertimePay(){val s=settings().copy(globalSalary=true,monthlySalary=10000.0,monthlyHours=100.0);val result=summary(fourLongDays+shift("2026-10-23",5.0),s);assertEquals(10700.0,result.gross,0.0)}
    @Test fun partialPayPeriodNeverProratesThreshold(){val rows=listOf(shift("2026-10-30",8.0));val result=summary(rows,settings("MONTHLY",160.0),PayPeriod(day("2026-10-25"),day("2026-11-25")));assertEquals(0.0,result.overtimeMs,0.0)}
}
