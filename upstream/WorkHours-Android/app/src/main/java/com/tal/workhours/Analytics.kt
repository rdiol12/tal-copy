package com.tal.workhours

import java.time.LocalDate
import java.time.YearMonth

/** Closed-shift analytics reuse the payroll allocation; thresholds are never restarted by a chart. */
data class AnalyticsGroup(val key: String, val days: Int, val netMs: Double, val overtimeMs: Double, val pay: Double, val tierMs: List<Double>)
data class AnalyticsTrend(val period: PayPeriod, val hasRecords: Boolean, val totalMs: Double, val overtimeMs: Double, val gross: Double)
data class WorkAnalytics(val summary: PeriodSummary, val previous: PeriodSummary, val previousHasRecords: Boolean,
    val recordedDays: Int, val shifts: Int, val averageDayMs: Double, val averageShiftMs: Double,
    val homeShare: Double?, val overtimeShare: Double?, val weightedOvertimeMs: Double,
    val longestDate: LocalDate?, val longestMs: Double, val mostOvertimeDate: LocalDate?, val mostOvertimeMs: Double,
    val locations: List<AnalyticsGroup>, val reasons: List<AnalyticsGroup>, val weekdays: List<AnalyticsGroup>, val trend: List<AnalyticsTrend>)

fun workAnalytics(data: AppData, period: PayPeriod, trendCount: Int = 12): WorkAnalytics {
    require(trendCount in 0..12)
    val sum=summarize(data,period)
    val prev=periodFromMonth(YearMonth.from(period.from).minusMonths(1),data.settings.periodStartDay)
    val previous=summarize(data,prev)
    val days=sum.days.filter { it.totalMs>0 }
    val parts=sum.days.flatMap { it.parts }
    fun group(key: String, selected: List<PaidPart>, count: Int) = AnalyticsGroup(key,count,selected.sumOf {it.netMs},selected.sumOf {it.tierMs.sum()},selected.sumOf {it.pay},data.settings.tiers.indices.map {i->selected.sumOf {it.tierMs[i]}})
    val locations=listOf(OFFICE,HOME).map { place -> group(place,parts.filter {it.shift.location==place},sum.days.count {day->day.parts.any {it.shift.location==place&&it.netMs>0}}) }
    val reasons=parts.filter {it.shift.location==HOME}.groupBy {it.shift.reason.trim()}.map {(reason,selected)->group(reason,selected,selected.map {it.shift.workDate}.distinct().size)}.sortedWith(compareByDescending<AnalyticsGroup> {it.netMs}.thenBy {it.key})
    val weekdays=(0..6).map { offset -> val day=(data.settings.weekStartDay-1+offset)%7+1;val selected=days.filter {it.day.dayOfWeek.value==day};group(day.toString(),selected.flatMap {it.parts},selected.size) }
    val trend=(trendCount-1 downTo 0).map { offset -> val p=periodFromMonth(YearMonth.from(period.from).minusMonths(offset.toLong()),data.settings.periodStartDay);val s=if(p==period)sum else if(p==prev)previous else summarize(data,p);AnalyticsTrend(p,s.days.isNotEmpty()||s.leave.isNotEmpty(),s.totalMs,s.overtimeMs,s.gross) }
    val longest=days.maxByOrNull {it.totalMs};val mostOvertime=days.filter {it.tierMs.sum()>0}.maxByOrNull {it.tierMs.sum()}
    return WorkAnalytics(sum,previous,previous.days.isNotEmpty()||previous.leave.isNotEmpty(),days.size,parts.size,
        if(days.isEmpty())0.0 else sum.totalMs/days.size,if(parts.isEmpty())0.0 else sum.totalMs/parts.size,
        if(sum.totalMs>0)sum.homeMs/sum.totalMs else null,if(sum.totalMs>0)sum.overtimeMs/sum.totalMs else null,
        sum.tierMs.mapIndexed {i,ms->ms*data.settings.tiers[i].percent/100}.sum(),longest?.day,longest?.totalMs?:0.0,
        mostOvertime?.day,mostOvertime?.tierMs?.sum()?:0.0,locations,reasons,weekdays,trend)
}
