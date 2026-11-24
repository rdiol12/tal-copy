package com.tal.workhours

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.zip.ZipInputStream
import java.io.ByteArrayInputStream

class LeaveTest {
    private val config=LeaveConfig(openingDate="2026-01-01",vacationOpening=9.2,sickOpening=20.0,vacationMonthly=1.5,sickMonthly=1.5)
    private fun date(s:String)=LocalDate.parse(s)
    private fun data(records:List<LeaveRecord> = emptyList(), c:LeaveConfig=config)=AppData(leaveConfig=c,leaveRecords=records)
    private fun sick(day:String,event:String="a",calendar:Boolean=true,tiers:List<SickTier> = config.sickTiers)=LeaveRecord(date=day,type=SICK,eventId=event,eventStart="2026-01-04",calendarDays=calendar,sickTiers=tiers)
    private val period=PayPeriod(date("2026-01-01"),date("2026-02-01"))
    @Test fun openingBalanceIsExact(){assertEquals(9.2,leaveBalance(data(),VACATION,date("2026-01-01")),1e-9)}
    @Test fun accrualIsStrictlyAfterOpening(){assertEquals(9.2,leaveBalance(data(),VACATION,date("2026-01-31")),1e-9);assertEquals(10.7,leaveBalance(data(),VACATION,date("2026-02-01")),1e-9)}
    @Test fun fourVacationDaysDeductFour(){val d=data((4..7).map {LeaveRecord(date="2026-01-0$it",type=VACATION)});d.validate();assertEquals(5.2,leaveBalance(d,VACATION,date("2026-01-31")),1e-9)}
    @Test fun monthlyPlusUsage(){val d=data(listOf(LeaveRecord(date="2026-02-03",type=VACATION)));assertEquals(9.7,leaveBalance(d,VACATION,date("2026-02-04")),1e-9)}
    @Test fun shortMonthsClampAccrualDay(){val d=data(c=config.copy(accrualDay=31));assertEquals(12.2,leaveBalance(d,VACATION,date("2026-02-28")),1e-9)}
    @Test fun month31StillAccruesInMarch(){val d=data(c=config.copy(accrualDay=31));assertEquals(13.7,leaveBalance(d,VACATION,date("2026-03-31")),1e-9)}
    @Test fun noAccrualBeforeOpening(){assertEquals(0.0,leaveBalance(data(),VACATION,date("2025-12-31")),0.0)}
    @Test fun noConfigHasNoBalance(){assertEquals(0.0,leaveBalance(AppData(),VACATION,date("2026-01-01")),0.0)}
    @Test fun repeatedReadsDoNotDoubleAccrue(){val d=data();assertEquals(leaveMovements(d,date("2026-03-31")),leaveMovements(d,date("2026-03-31")))}
    @Test fun sickPaymentTiersAreSequential(){val d=data((4..8).map {sick("2026-01-0$it")});d.validate();assertEquals(listOf(0.0,50.0,50.0,100.0,100.0),leavePayments(d.leaveRecords).map{it.payPercent})}
    @Test fun firstDayCanBeFullyPaid(){assertEquals(100.0,leavePayments(listOf(sick("2026-01-04",tiers=listOf(SickTier())))).single().payPercent,0.0)}
    @Test fun zeroPayStillDeductsFullDay(){val p=leavePayments(listOf(sick("2026-01-04"))).single();assertEquals(0.0,p.paidHours,0.0);assertEquals(1.0,p.debitDays,0.0)}
    @Test fun deductionIndependentOfPay(){val p=leavePayments(listOf(sick("2026-01-04",tiers=listOf(SickTier(null,50.0,25.0))))).single();assertEquals(4.0,p.paidHours,0.0);assertEquals(.25,p.debitDays,0.0)}
    @Test fun partialDay(){val p=leavePayments(listOf(LeaveRecord(date="2026-01-04",type=VACATION,fraction=.5))).single();assertEquals(4.0,p.hours,0.0);assertEquals(.5,p.debitDays,0.0)}
    @Test fun calendarGapAdvancesTier(){assertEquals(100.0,leavePayments(listOf(sick("2026-01-07"))).single().payPercent,0.0)}
    @Test fun reportedDaysIgnoreGap(){val rows=listOf(sick("2026-01-04",calendar=false),sick("2026-01-09",calendar=false));assertEquals(listOf(0.0,50.0),leavePayments(rows).map{it.payPercent})}
    @Test fun newEventRestartsTiers(){val rows=listOf(sick("2026-01-04"),sick("2026-01-10","b").copy(eventStart="2026-01-10"));assertEquals(listOf(0.0,0.0),leavePayments(rows).map{it.payPercent})}
    @Test fun policySnapshotPreserved(){val d=data(listOf(sick("2026-01-04")),config.copy(sickTiers=listOf(SickTier())));assertEquals(0.0,leavePayments(d.leaveRecords).single().payPercent,0.0)}
    @Test fun dayHoursSnapshotPreserved(){val d=data(listOf(LeaveRecord(date="2026-01-04",type=VACATION,dayHours=7.0)),config.copy(dayHours=9.0));assertEquals(7.0,leavePayments(d.leaveRecords).single().hours,0.0)}
    @Test fun overrides(){val p=leavePayments(listOf(sick("2026-01-04").copy(payOverride=75.0,debitOverride=0.0))).single();assertEquals(6.0,p.paidHours,0.0);assertEquals(0.0,p.debitDays,0.0)}
    @Test fun futureNotDeductedToday(){val d=data(listOf(LeaveRecord(date="2026-02-04",type=VACATION)));assertEquals(9.2,leaveBalance(d,VACATION,date("2026-01-31")),1e-9)}
    @Test fun deleteRestoresBalance(){val r=LeaveRecord(date="2026-01-04",type=VACATION);val d=data(listOf(r));assertEquals(1.0,leaveBalance(d.copy(leaveRecords=emptyList()),VACATION,date("2026-01-31"))-leaveBalance(d,VACATION,date("2026-01-31")),1e-9)}
    @Test fun adjustmentIsDated(){val d=data().copy(leaveAdjustments=listOf(LeaveAdjustment(date="2026-01-08",type=VACATION,days=2.0,note="Correction")));d.validate();assertEquals(9.2,leaveBalance(d,VACATION,date("2026-01-07")),1e-9);assertEquals(11.2,leaveBalance(d,VACATION,date("2026-01-08")),1e-9)}
    @Test fun insufficientBalanceRejected(){val d=data(listOf(LeaveRecord(date="2026-01-04",type=VACATION)),config.copy(vacationOpening=0.0));assertThrows(IllegalArgumentException::class.java){requireLeaveBalance(d)}}
    @Test fun negativeBalanceAllowedExplicitly(){val d=data(listOf(LeaveRecord(date="2026-01-04",type=VACATION)),config.copy(vacationOpening=0.0,allowNegative=true));requireLeaveBalance(d);assertEquals(-1.0,leaveBalance(d,VACATION,date("2026-01-04")),0.0)}
    @Test fun laterAccrualDoesNotHideEarlierDeficit(){val d=data(listOf(LeaveRecord(date="2026-01-04",type=VACATION)),config.copy(vacationOpening=0.0));assertThrows(IllegalArgumentException::class.java){requireLeaveBalance(d)}}
    @Test fun duplicateDayRejected(){val d=data(listOf(LeaveRecord(date="2026-01-04",type=VACATION),sick("2026-01-04")));assertThrows(IllegalArgumentException::class.java){d.validate()}}
    @Test fun twoHalfDaysAllowed(){data(listOf(LeaveRecord(date="2026-01-04",type=VACATION,fraction=.5),sick("2026-01-04").copy(fraction=.5))).validate()}
    @Test fun invalidTierRejected(){assertThrows(IllegalArgumentException::class.java){config.copy(sickTiers=listOf(SickTier(2,150.0))).validate()}}
    @Test fun invalidFractionRejected(){assertThrows(IllegalArgumentException::class.java){data(listOf(LeaveRecord(date="2026-01-04",type=VACATION,fraction=Double.NaN))).validate()}}
    @Test fun noRecordsBeforeOpening(){assertThrows(IllegalArgumentException::class.java){data(listOf(LeaveRecord(date="2025-12-31",type=VACATION))).validate()}}
    @Test fun inconsistentEventRejected(){assertThrows(IllegalArgumentException::class.java){data(listOf(sick("2026-01-04"),sick("2026-01-05",calendar=false))).validate()}}
    @Test fun hourlyPayIncludesLeaveWithoutOvertime(){val d=data(listOf(LeaveRecord(date="2026-01-04",type=VACATION))).copy(settings=AppSettings(hourlyWage=50.0));val s=summarize(d,period);assertEquals(400.0,s.gross,0.0);assertEquals(0.0,s.totalMs,0.0);assertEquals(0.0,s.overtimeMs,0.0)}
    @Test fun globalPaidLeaveNotAddedTwice(){val d=data(listOf(LeaveRecord(date="2026-01-04",type=VACATION))).copy(settings=AppSettings(globalSalary=true,monthlySalary=10000.0,monthlyHours=200.0));assertEquals(10000.0,summarize(d,period).gross,0.0)}
    @Test fun globalSickLeaveDeductsUnpaidPart(){val d=data(listOf(sick("2026-01-04"),sick("2026-01-05"))).copy(settings=AppSettings(globalSalary=true,monthlySalary=10000.0,monthlyHours=200.0));assertEquals(9400.0,summarize(d,period).gross,0.0)}
    @Test fun crossPayPeriodSickSequenceRetained(){val rows=listOf(sick("2026-01-31").copy(eventStart="2026-01-31"),sick("2026-02-01").copy(eventStart="2026-01-31"));val d=data(rows);val s=summarize(d,PayPeriod(date("2026-02-01"),date("2026-03-01")));assertEquals(50.0,s.leave.single().payPercent,0.0)}
    @Test fun fullBackupRoundTrip(){val d=data(listOf(sick("2026-01-04"))).copy(leaveAdjustments=listOf(LeaveAdjustment(date="2026-01-06",type=SICK,days=2.5,note="reason")));assertEquals(d,readBackup(backupText(d)))}
    @Test fun legacyTwentyBackupDefaultsToNoLeave(){val o=org.json.JSONObject(encodeData(AppData()));o.put("version",20);o.remove("leaveConfig");o.remove("leaveRecords");o.remove("leaveAdjustments");assertEquals(AppData(),decodeData(o.toString()))}
    @Test fun allExportAddsSeparateLeaveSheet(){val d=data(listOf(sick("2026-01-04").copy(note="=SUM(A1) & <x>")));val labels=ShiftExportLabels("Shifts","From","To","Date","Start","End","Hours","Total","Note");val bytes=shiftWorkbook(d,period,null,labels,true);val parts=mutableMapOf<String,String>();ZipInputStream(ByteArrayInputStream(bytes)).use{z->while(true){val e=z.nextEntry?:break;parts[e.name]=z.readBytes().toString(Charsets.UTF_8)}};assertTrue(parts.containsKey("xl/worksheets/sheet2.xml"));val xml=parts.getValue("xl/worksheets/sheet2.xml");assertTrue(xml.contains("=SUM(A1) &amp; &lt;x&gt;"));assertTrue(xml.contains("SUM(G3:G3)"));assertTrue(xml.contains("rightToLeft=\"1\""))}
    @Test fun officeExportDoesNotIncludeLeave(){val d=data(listOf(sick("2026-01-04")));val labels=ShiftExportLabels("Shifts","From","To","Date","Start","End","Hours","Total","Note");val names=mutableListOf<String>();ZipInputStream(ByteArrayInputStream(shiftWorkbook(d,period,OFFICE,labels,false))).use{z->while(true){val e=z.nextEntry?:break;names+=e.name}};assertFalse(names.contains("xl/worksheets/sheet2.xml"))}
    @Test fun partialLeaveDayStillShowsRemainingDailyShortage(){val d=data(listOf(LeaveRecord(date="2026-01-04",type=VACATION,fraction=.5)));assertEquals(4*HOUR_MS,summarize(d,period).dailyShortageMs,0.0)}
    @Test fun fullLeaveCreditsMonthlyRequirementWithoutWorkHours(){val d=data(listOf(LeaveRecord(date="2026-01-04",type=VACATION))).copy(settings=AppSettings(monthlyHours=200.0));val s=summarize(d,period);assertEquals(192*HOUR_MS,s.monthlyShortageMs,0.0);assertEquals(0.0,s.totalMs,0.0)}
    @Test fun settingsOnlyBackupPreservesAllRules(){val d=data(c=config.copy(accrualDay=25,dayHours=7.5,allowNegative=true,calendarSickDays=false,sickTiers=listOf(SickTier(2,80.0,25.0),SickTier(null,100.0,50.0))));assertEquals(d,readBackup(backupText(d)))}
    @Test fun firstMonthCanAccrueOnConfiguredDate(){val d=data(c=config.copy(accrualDay=15));assertEquals(9.2,leaveBalance(d,VACATION,date("2026-01-14")),1e-9);assertEquals(10.7,leaveBalance(d,VACATION,date("2026-01-15")),1e-9)}
    @Test fun paidLeaveDoesNotUseWorkBreakDeductions(){val d=data(listOf(LeaveRecord(date="2026-01-04",type=VACATION))).copy(settings=AppSettings(hourlyWage=50.0,breakMode="DAILY",breakMinutes=60));assertEquals(400.0,summarize(d,period).gross,0.0)}
    @Test fun fullLeaveCodecRejectsFractionalTierLength(){val o=encodeLeaveConfig(config);o.getJSONArray("sickTiers").getJSONObject(0).put("days",1.5);assertThrows(IllegalArgumentException::class.java){decodeLeaveConfig(o)}}
    @Test fun modernBackupCannotSilentlyDropLeaveCollections(){val o=org.json.JSONObject(encodeData(data()));o.remove("leaveRecords");assertThrows(org.json.JSONException::class.java){decodeData(o.toString())}}
    @Test fun backupRejectsFutureVersion(){val o=org.json.JSONObject(encodeData(data())).put("version",DATA_VERSION+1);assertThrows(IllegalArgumentException::class.java){decodeData(o.toString())}}
    @Test fun sameDateCorrectionsUseDailyClosingBalance(){val d=data(c=config.copy(vacationOpening=0.0)).copy(leaveAdjustments=listOf(LeaveAdjustment(date="2026-01-03",type=VACATION,days=-2.0,note="a"),LeaveAdjustment(date="2026-01-03",type=VACATION,days=3.0,note="b")));requireLeaveBalance(d);assertEquals(1.0,leaveBalance(d,VACATION,date("2026-01-03")),0.0)}
    @Test fun partialRecordsOnSameSickDateShareOrdinal(){val rows=listOf(sick("2026-01-04",calendar=false).copy(fraction=.5),sick("2026-01-04",calendar=false).copy(fraction=.5),sick("2026-01-07",calendar=false));val p=leavePayments(rows);assertEquals(listOf(1,1,2),p.map{it.ordinal})}
    private fun shortageData()=data().copy(settings=AppSettings(monthlyHours=3.2,hourlyWage=50.0))
    @Test fun threePointTwoHoursConsumeFortyPercentDay(){val d=shortageData();val r=makeLeaveSettlement(d,period,3.2);assertEquals(.4,r.fraction,1e-9);val updated=d.copy(leaveRecords=listOf(r));assertEquals(8.8,leaveBalance(updated,VACATION,period.end),1e-9);assertEquals(0.0,monthlyLeaveShortage(updated,period),1e-9);assertEquals(160.0,summarize(updated,period).gross,1e-9)}
    @Test fun settlementIsNotWorkOrOvertime(){val d=shortageData();val s=summarize(d.copy(leaveRecords=listOf(makeLeaveSettlement(d,period,3.2))),period);assertTrue(s.days.isEmpty());assertEquals(0.0,s.overtimeMs,0.0);assertEquals(0.0,s.totalMs,0.0)}
    @Test fun settlementRejectsMoreThanRemaining(){val d=shortageData();assertThrows(IllegalArgumentException::class.java){makeLeaveSettlement(d,period,3.3)}}
    @Test fun secondSettlementCannotReuseAlreadyCoveredHours(){val d=shortageData();val a=makeLeaveSettlement(d,period,2.0);val updated=d.copy(leaveRecords=listOf(a));assertThrows(IllegalArgumentException::class.java){makeLeaveSettlement(updated,period,2.0)};assertEquals(1.2,monthlyLeaveShortage(updated,period),1e-9)}
    @Test fun settlementCanCoverMultipleDaysWithoutFabricatingDailyLeave(){val d=data().copy(settings=AppSettings(monthlyHours=24.0));val r=makeLeaveSettlement(d,period,24.0);d.copy(leaveRecords=listOf(r)).validate();assertEquals(3.0,r.fraction,0.0)}
    @Test fun settlementEditingExcludesOldRecord(){val d=shortageData();val old=makeLeaveSettlement(d,period,3.2);val u=d.copy(leaveRecords=listOf(old));val edited=makeLeaveSettlement(u,period,2.0,VACATION,old);assertEquals(old.id,edited.id);assertEquals(.25,edited.fraction,1e-9)}
    @Test fun unpaidSettlementDoesNotConsumeVacation(){val d=shortageData();val r=makeLeaveSettlement(d,period,3.2,UNPAID);val u=d.copy(leaveRecords=listOf(r));assertEquals(9.2,leaveBalance(u,VACATION,period.end),1e-9);assertEquals(0.0,summarize(u,period).gross,0.0)}
    @Test fun unpaidSettlementDeductsGlobalSalary(){val d=data().copy(settings=AppSettings(globalSalary=true,monthlySalary=10000.0,monthlyHours=200.0));val r=makeLeaveSettlement(d,period,3.2,UNPAID);assertEquals(9840.0,summarize(d.copy(leaveRecords=listOf(r)),period).gross,1e-9)}
    @Test fun vacationSettlementDoesNotDoubleGlobalSalary(){val d=data().copy(settings=AppSettings(globalSalary=true,monthlySalary=10000.0,monthlyHours=200.0));val r=makeLeaveSettlement(d,period,3.2);assertEquals(10000.0,summarize(d.copy(leaveRecords=listOf(r)),period).gross,1e-9)}
    @Test fun settlementNeedsConfiguredMonthlyTarget(){assertThrows(IllegalArgumentException::class.java){makeLeaveSettlement(data(),period,3.2)}}
    @Test fun settlementDeletionRestoresQuotaAndMissingHours(){val d=shortageData();val r=makeLeaveSettlement(d,period,3.2);val u=d.copy(leaveRecords=listOf(r));assertEquals(.4,leaveBalance(d,VACATION,period.end)-leaveBalance(u,VACATION,period.end),1e-9);assertEquals(3.2,monthlyLeaveShortage(d,period),1e-9)}
    @Test fun holidaysDoNotConsumeLeaveBalances(){val d=data(listOf(LeaveRecord(date="2026-01-04",type=HOLIDAY))).copy(settings=AppSettings(hourlyWage=50.0));d.validate();requireLeaveBalance(d);assertEquals(9.2,leaveBalance(d,VACATION,period.end),1e-9);assertEquals(20.0,leaveBalance(d,SICK,period.end),1e-9);assertEquals(400.0,summarize(d,period).gross,1e-9)}
    @Test fun vacationRangeSkipsWeekendAndHoliday(){val d=data(listOf(LeaveRecord(date="2026-01-05",type=HOLIDAY)));val dates=leaveRangeDates(d,date("2026-01-02"),date("2026-01-08"),true,VACATION).map{it.toString()};assertEquals(listOf("2026-01-04","2026-01-06","2026-01-07","2026-01-08"),dates)}
    @Test fun explicitAllDatesRangeCanIncludeHoliday(){val d=data(listOf(LeaveRecord(date="2026-01-05",type=HOLIDAY)));assertEquals(7,leaveRangeDates(d,date("2026-01-02"),date("2026-01-08"),false,VACATION).size)}
    @Test fun duplicateHolidayAndVacationRejected(){val d=data(listOf(LeaveRecord(date="2026-01-04",type=HOLIDAY),LeaveRecord(date="2026-01-04",type=VACATION)));assertThrows(IllegalArgumentException::class.java){d.validate()}}
    @Test fun holidayAndSettlementRoundTripInBackup(){val d=shortageData();val r=makeLeaveSettlement(d,period,3.2);val u=d.copy(leaveRecords=listOf(r,LeaveRecord(date="2026-01-04",type=HOLIDAY)));assertEquals(u,readBackup(backupText(u)))}
    @Test fun partialVacationFractionIsFortyPercent(){val d=data(listOf(LeaveRecord(date="2026-01-04",type=VACATION,fraction=40.0/100)));assertEquals(8.8,leaveBalance(d,VACATION,period.end),1e-9)}
    @Test fun settlementAndFullDayCanSharePeriodEnd(){val d=shortageData();val r=makeLeaveSettlement(d,period,3.2);d.copy(leaveRecords=listOf(r,LeaveRecord(date=period.end.toString(),type=VACATION))).validate()}
}
