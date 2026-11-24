package com.tal.workhours

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.min

const val VACATION = "VACATION"
const val SICK = "SICK"
const val HOLIDAY = "HOLIDAY"
const val UNPAID = "UNPAID"

data class SickTier(val days: Int? = null, val payPercent: Double = 100.0, val debitPercent: Double = 100.0)
data class LeaveConfig(
    val openingDate: String = "",
    val vacationOpening: Double = 0.0, val sickOpening: Double = 0.0,
    val vacationMonthly: Double = 0.0, val sickMonthly: Double = 0.0,
    val accrualDay: Int = 1, val dayHours: Double = 8.0,
    val vacationPercent: Double = 100.0, val allowNegative: Boolean = false,
    val calendarSickDays: Boolean = true,
    val sickTiers: List<SickTier> = listOf(SickTier(1,0.0), SickTier(2,50.0), SickTier(null,100.0))
) {
    fun validate() {
        require(openingDate.isEmpty() || LocalDate.parse(openingDate).year in 1900..2200)
        require(listOf(vacationOpening,sickOpening).all { it.isFinite() && it in -10000.0..10000.0 })
        require(listOf(vacationMonthly,sickMonthly).all { it.isFinite() && it in 0.0..31.0 })
        require(accrualDay in 1..31 && dayHours.isFinite() && dayHours > 0 && dayHours <= 24)
        require(vacationPercent.isFinite() && vacationPercent in 0.0..100.0)
        validateSickTiers(sickTiers)
    }
}

fun validateSickTiers(tiers: List<SickTier>) {
    require(tiers.isNotEmpty() && tiers.size <= 20 && tiers.last().days == null)
    require(tiers.dropLast(1).all { it.days != null && it.days in 1..3650 })
    require(tiers.all { it.payPercent.isFinite() && it.payPercent in 0.0..100.0 && it.debitPercent.isFinite() && it.debitPercent in 0.0..100.0 })
}

/** Policy and day length are captured at reporting time, preserving historical pay. */
data class LeaveRecord(
    val id: String = UUID.randomUUID().toString(), val date: String, val type: String,
    val fraction: Double = 1.0, val dayHours: Double = 8.0,
    val vacationPercent: Double = 100.0,
    val eventId: String = "", val eventStart: String = "", val calendarDays: Boolean = true,
    val sickTiers: List<SickTier> = emptyList(),
    val payOverride: Double? = null, val debitOverride: Double? = null, val note: String = "",
    val periodFrom: String = "", val periodUntil: String = ""
) { val isSettlement: Boolean get() = periodFrom.isNotEmpty() }
data class LeaveAdjustment(val id: String = UUID.randomUUID().toString(), val date: String, val type: String,
    val days: Double, val note: String)
data class LeavePayment(val record: LeaveRecord, val ordinal: Int, val payPercent: Double, val debitDays: Double) {
    val hours get() = record.dayHours * record.fraction
    val paidHours get() = hours * payPercent / 100.0
}
data class LeaveMovement(val date: LocalDate, val type: String, val days: Double, val kind: String, val note: String = "")

fun leavePayments(records: List<LeaveRecord>): List<LeavePayment> {
    val groups = records.filter { it.type == SICK }.groupBy { it.eventId }.mapValues { (_,event) -> event.map { it.date }.distinct().sorted().withIndex().associate { it.value to it.index+1 } }
    return records.sortedWith(compareBy<LeaveRecord> { it.date }.thenBy { it.id }).map { r ->
        val ordinal = if(r.type != SICK) 0 else if(r.calendarDays)
            (ChronoUnit.DAYS.between(LocalDate.parse(r.eventStart),LocalDate.parse(r.date))+1).toInt()
        else groups.getValue(r.eventId).getValue(r.date)
        var left = ordinal
        val tier = if(r.type != SICK) null else r.sickTiers.first { t ->
            if(t.days == null || left <= t.days) true else { left -= t.days; false }
        }
        LeavePayment(r,ordinal,r.payOverride ?: tier?.payPercent ?: r.vacationPercent,
            if(r.type !in setOf(VACATION,SICK)) 0.0 else r.fraction * (r.debitOverride ?: tier?.debitPercent ?: 100.0)/100.0)
    }
}

/** Derived ledger: no scheduled job or repeated writes; first accrual is strictly after the opening date. */
fun leaveMovements(data: AppData, asOf: LocalDate): List<LeaveMovement> {
    val c = data.leaveConfig
    if(c.openingDate.isEmpty()) return emptyList()
    val start = LocalDate.parse(c.openingDate)
    if(asOf < start) return emptyList()
    val result = mutableListOf(LeaveMovement(start,VACATION,c.vacationOpening,"OPENING"),LeaveMovement(start,SICK,c.sickOpening,"OPENING"))
    var month = YearMonth.from(start)
    while(month <= YearMonth.from(asOf)) {
        val date = month.atDay(min(c.accrualDay,month.lengthOfMonth()))
        if(date > start && date <= asOf) {
            result += LeaveMovement(date,VACATION,c.vacationMonthly,"ACCRUAL")
            result += LeaveMovement(date,SICK,c.sickMonthly,"ACCRUAL")
        }
        month = month.plusMonths(1)
    }
    result += leavePayments(data.leaveRecords).filter { it.record.type in setOf(VACATION,SICK) && LocalDate.parse(it.record.date) <= asOf }.map {
        LeaveMovement(LocalDate.parse(it.record.date),it.record.type,-it.debitDays,"USED",it.record.note)
    }
    result += data.leaveAdjustments.filter { LocalDate.parse(it.date) <= asOf }.map { LeaveMovement(LocalDate.parse(it.date),it.type,it.days,"ADJUSTMENT",it.note) }
    return result.sortedWith(compareBy<LeaveMovement> { it.date }.thenBy { when(it.kind) { "OPENING" -> 0; "ACCRUAL" -> 1; "ADJUSTMENT" -> 2; else -> 3 } })
}
fun leaveBalance(data: AppData, type: String, asOf: LocalDate): Double = leaveMovements(data,asOf).filter { it.type == type }.sumOf { it.days }

fun validateLeaveData(data: AppData) {
    val c = data.leaveConfig; c.validate()
    require(data.leaveRecords.size <= 20000 && data.leaveAdjustments.size <= 20000)
    require(data.leaveRecords.map { it.id }.toSet().size == data.leaveRecords.size)
    require(data.leaveAdjustments.map { it.id }.toSet().size == data.leaveAdjustments.size)
    require(c.openingDate.isNotEmpty() || (data.leaveRecords.isEmpty() && data.leaveAdjustments.isEmpty()))
    data.leaveRecords.forEach { r ->
        val date = LocalDate.parse(r.date)
        require(date.year in 1900..2200 && r.date >= c.openingDate && r.type in setOf(VACATION,SICK,HOLIDAY,UNPAID))
        require(r.id.isNotBlank() && r.id.length <= 100 && r.note.length <= 2000)
        require(r.fraction.isFinite() && r.fraction > 0 && (if(r.isSettlement)r.fraction*r.dayHours<=744 else r.fraction<=1))
        if(r.isSettlement) {
            val from=LocalDate.parse(r.periodFrom);val until=LocalDate.parse(r.periodUntil)
            require(r.type in setOf(VACATION,UNPAID) && until>from && ChronoUnit.DAYS.between(from,until)<=62 && r.date==until.minusDays(1).toString())
        } else require(r.periodUntil.isEmpty())
        require(r.dayHours.isFinite() && r.dayHours > 0 && r.dayHours <= 24)
        require(listOfNotNull(r.payOverride,r.debitOverride,r.vacationPercent).all { it.isFinite() && it in 0.0..100.0 })
        if(r.type == UNPAID) require(r.vacationPercent==0.0 && (r.payOverride==null || r.payOverride==0.0))
        if(r.type == SICK) {
            require(r.eventId.isNotBlank() && r.eventId.length <= 100 && LocalDate.parse(r.eventStart) <= date)
            require(LocalDate.parse(r.eventStart).year in 1900..2200)
            validateSickTiers(r.sickTiers)
        }
    }
    require(data.leaveRecords.filterNot { it.isSettlement }.groupBy { it.date }.values.all { day -> day.sumOf { it.fraction } <= 1.00000001 }) { tr(R.string.leave_duplicate) }
    data.leaveRecords.filter { it.type == SICK }.groupBy { it.eventId }.values.forEach { event ->
        val first=event.first()
        require(event.all { it.eventStart == first.eventStart && it.calendarDays == first.calendarDays && it.sickTiers == first.sickTiers })
    }
    data.leaveAdjustments.forEach {
        require(LocalDate.parse(it.date).year in 1900..2200 && it.date >= c.openingDate && it.type in setOf(VACATION,SICK))
        require(it.days.isFinite() && it.days in -10000.0..10000.0 && it.note.isNotBlank() && it.note.length <= 2000 && it.id.isNotBlank() && it.id.length <= 100)
    }
}

/** Check after a proposed edit, not on legacy-data loading. Opening negatives remain an explicit adjustment choice. */
fun requireLeaveBalance(data: AppData) {
    if(data.leaveConfig.allowNegative || data.leaveConfig.openingDate.isEmpty()) return
    val balances = mutableMapOf(VACATION to 0.0,SICK to 0.0)
    val last = (data.leaveRecords.map { it.date } + data.leaveAdjustments.map { it.date } + data.leaveConfig.openingDate).maxOrNull()!!
    leaveMovements(data,LocalDate.parse(last)).groupBy { it.date }.values.forEach { day ->
        day.forEach { m -> balances[m.type] = balances.getValue(m.type) + m.days }
        require(balances.values.all { it >= -0.0000001 }) { tr(R.string.leave_insufficient) }
    }
}


/** Remaining monthly target after work, reported leave, holidays and other confirmed settlements. */
fun monthlyLeaveShortage(data: AppData,period: PayPeriod,excludingId: String? = null): Double =
    summarize(data.copy(leaveRecords=data.leaveRecords.filterNot { it.id==excludingId }),period).monthlyShortageMs/HOUR_MS

fun leaveRangeDates(data: AppData,from: LocalDate,until: LocalDate,workdaysOnly: Boolean,type: String,excludingId: String? = null): List<LocalDate> {
    require(until>=from && ChronoUnit.DAYS.between(from,until)<=366) { tr(R.string.leave_date_error) }
    val holidays=data.leaveRecords.filter { it.type==HOLIDAY && it.id!=excludingId }.map { it.date }.toSet()
    return generateSequence(from){it.plusDays(1)}.takeWhile { it<=until }.filter {
        !workdaysOnly || (it.dayOfWeek.value in data.settings.workDays && (type==HOLIDAY || it.toString() !in holidays))
    }.toList()
}

fun makeLeaveSettlement(data: AppData,period: PayPeriod,hours: Double,type: String = VACATION,existing: LeaveRecord? = null): LeaveRecord {
    require(data.settings.monthlyHours > 0 && type in setOf(VACATION,UNPAID)) { tr(R.string.leave_target_needed) }
    require(hours.isFinite() && hours>0 && hours<=744 && hours<=monthlyLeaveShortage(data,period,existing?.id)+0.0000001) { tr(R.string.leave_too_many_hours) }
    val c=data.leaveConfig
    val record=LeaveRecord(id=existing?.id ?: UUID.randomUUID().toString(),date=period.end.toString(),type=type,
        fraction=hours/c.dayHours,dayHours=c.dayHours,vacationPercent=if(type==UNPAID)0.0 else c.vacationPercent,
        note=tr(if(type==UNPAID)R.string.leave_salary_choice else R.string.leave_settlement),periodFrom=period.from.toString(),periodUntil=period.until.toString())
    data.copy(leaveRecords=data.leaveRecords.filterNot { it.id==existing?.id }+record).also {it.validate();requireLeaveBalance(it)}
    return record
}
