package com.tal.workhours

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

/** Match the web reminder: any report, even partial, satisfies the day. */
fun isMissingCalendarReport(date: LocalDate, today: LocalDate, shifts: Set<LocalDate>,
    leave: Set<LocalDate>, activeDate: LocalDate?): Boolean =
    date < today && date.dayOfWeek != DayOfWeek.FRIDAY && date.dayOfWeek != DayOfWeek.SATURDAY &&
        date !in shifts && date !in leave && date != activeDate

@Composable
fun SnapshotWorkCalendar(data: AppData, summary: PeriodSummary, today: LocalDate, onMissingDay: (LocalDate) -> Unit, onEditShift: (Shift) -> Unit, onEditLeave: (LeaveRecord) -> Unit, canSelectDate: () -> Boolean = { true }) {
    val period = summary.period
    val reportedDates = remember(data.shifts) { data.shifts.map { LocalDate.parse(it.workDate) }.toSet() }
    val leaveByDate = remember(data.leaveRecords) { data.leaveRecords.groupBy { LocalDate.parse(it.date) } }
    val days = remember(summary.days) { summary.days.associateBy { it.day } }
    val paidVacationDates = remember(summary.leave) { summary.leave.filter { it.record.type == VACATION && it.payPercent > 0 && it.hours > 0 }.map { LocalDate.parse(it.record.date) }.toSet() }
    val activeDate = data.active?.let { localTime(it.start).toLocalDate() }
    val homeDates = remember(data.shifts, data.active) {
        buildSet {
            data.shifts.filter { it.location == HOME }.forEach { add(LocalDate.parse(it.workDate)) }
            if (data.active?.location == HOME) activeDate?.let { add(it) }
        }
    }
    val homeLabel = tr(R.string.calendar_includes_home)
    val dates = remember(period) { generateSequence(period.from) { it.plusDays(1) }.takeWhile { it < period.until }.toList() }
    val missing = dates.count { isMissingCalendarReport(it,today,reportedDates,leaveByDate.keys,activeDate) }
    var selected by remember(period) { mutableStateOf<LocalDate?>(null) }
    val orange = Color(0xFFFFF2DF)
    val orangeText = Color(0xFF915219)
    InfoCard(tr(R.string.calendar_title)) {
        Text(period.from.format(dateFormat)+" — "+period.end.format(dateFormat),style=MaterialTheme.typography.bodySmall)
        Text(tr(R.string.calendar_missing_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(tr(R.string.calendar_missing_count,missing.toString()),color=MaterialTheme.colorScheme.secondary,style=MaterialTheme.typography.labelMedium)
        val fontScale = LocalDensity.current.fontScale
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gridWidth = maxOf(maxWidth,280.dp * fontScale)
            val scrollState = rememberScrollState()
            // Leave horizontal drags to the pager when the calendar already fits.
            val gridScroll = if (gridWidth > maxWidth) Modifier.horizontalScroll(scrollState) else Modifier
            Row(gridScroll) {
                Column(Modifier.width(gridWidth),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                        listOf(DayOfWeek.SUNDAY,DayOfWeek.MONDAY,DayOfWeek.TUESDAY,DayOfWeek.WEDNESDAY,DayOfWeek.THURSDAY,DayOfWeek.FRIDAY,DayOfWeek.SATURDAY).forEach { day ->
                            Text(day.getDisplayName(TextStyle.NARROW_STANDALONE,appLocale),modifier=Modifier.weight(1f),fontSize=11.sp,fontWeight=FontWeight.Bold)
                        }
                    }
                    val offset = period.from.dayOfWeek.value % 7
                    val cells = List<LocalDate?>(offset) { null } + dates
                    cells.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                            (week + List<LocalDate?>(7-week.size) { null }).forEach { date ->
                                if (date == null) Spacer(Modifier.weight(1f)) else {
                                    val day = days[date]
                                    val workedAtHome = date in homeDates
                                    val leaves = leaveByDate[date].orEmpty()
                                    val absent = isMissingCalendarReport(date,today,reportedDates,leaveByDate.keys,activeDate)
                                    val state = when { absent -> tr(R.string.calendar_missing);date == activeDate -> tr(R.string.shift_active);else -> "" }
                                    val weekend = date.dayOfWeek == DayOfWeek.FRIDAY || date.dayOfWeek == DayOfWeek.SATURDAY
                                    val tint = when {
                                        leaves.any { it.type == HOLIDAY } -> Color(0xFFF6F0FC)
                                        leaves.any { it.type == SICK } -> Color(0xFFEEF5FC)
                                        date in paidVacationDates -> Color(0xFFF0F8F1)
                                        absent -> orange
                                        weekend -> Color(0xFFF4F4F5)
                                        else -> null
                                    }
                                    val textColor = if(absent)orangeText else if(tint!=null)Color(0xFF263342) else MaterialTheme.colorScheme.onSurface
                                    Surface(onClick = {
                                        if (canSelectDate()) {
                                            if (date !in reportedDates && date !in leaveByDate && date != activeDate) onMissingDay(date)
                                            else selected = date
                                        }
                                    },modifier=Modifier.weight(1f).heightIn(min=84.dp).fillMaxHeight().semantics {
                                        contentDescription=date.format(dateFormat)+" · "+state+" · "+leaves.joinToString { leaveTypeLabel(it.type) }+(day?.let { " · "+hoursText(it.totalMs) } ?: "") + if (workedAtHome) " · $homeLabel" else ""
                                    },shape=RoundedCornerShape(7.dp),color=tint ?: MaterialTheme.colorScheme.surface,
                                        contentColor=textColor,border=BorderStroke(if(date==today)2.dp else 1.dp,if(date==today)MaterialTheme.colorScheme.primary else if(absent)Color(0xFFE6B779) else MaterialTheme.colorScheme.outlineVariant)) {
                                        Column(Modifier.padding(3.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically) {
                                                Text(date.dayOfMonth.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                if (workedAtHome) CalendarHomeIcon(
                                                    if (tint != null) Color(0xFF00766F) else MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                            if(date.dayOfMonth==1 || date==period.from)Text(date.month.getDisplayName(TextStyle.SHORT,appLocale),fontSize=9.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                            if(day!=null)Text(hoursText(day.totalMs),fontSize=10.sp,maxLines=1)
                                            if(state.isNotEmpty())Text(state,fontSize=9.sp)
                                            leaves.map { it.type }.distinct().forEach { Text(leaveTypeLabel(it),fontSize=9.sp,maxLines=2,overflow=TextOverflow.Ellipsis) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CalendarHomeIcon(MaterialTheme.colorScheme.secondary)
            Text(homeLabel, style = MaterialTheme.typography.bodySmall)
        }
    }
    selected?.let { date ->
        AlertDialog(onDismissRequest={selected=null},title={Text(date.format(dateFormat))},text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                val day=days[date]
                if(day!=null)Metric(tr(R.string.calendar_paid_time),hoursText(day.totalMs))
                data.shifts.filter { it.workDate==date.toString() }.sortedBy { it.start }.forEach { shift ->
                    Text(locationLabel(shift.location)+" · "+localTime(shift.start).format(clockFormat)+" — "+localTime(shift.end).format(clockFormat))
                    if(shift.reason.isNotBlank())Text(shift.reason)
                    if(shift.note.isNotBlank())Text(shift.note)
                    TextButton(onClick={selected=null;onEditShift(shift)}){Text(tr(R.string.text_39fe25939b))}
                }
                if(date==activeDate)Text(tr(R.string.shift_active))
                leaveByDate[date].orEmpty().forEach { leave -> Text(leaveTypeLabel(leave.type));if(leave.note.isNotBlank())Text(leave.note);if(!leave.isSettlement)TextButton(onClick={selected=null;onEditLeave(leave)}){Text(tr(R.string.text_39fe25939b))} }
                if(date !in reportedDates && date !in leaveByDate && date!=activeDate)Text(tr(R.string.calendar_no_report))
            }
        },confirmButton={TextButton(onClick={selected=null;onMissingDay(date)}){Text(tr(R.string.manual_entry))}},dismissButton={TextButton(onClick={selected=null}){Text(tr(R.string.shift_done))}})
    }
}

@Composable
private fun CalendarHomeIcon(tint: Color) {
    Icon(painterResource(R.drawable.ic_calendar_home), contentDescription = null,
        modifier = Modifier.size(12.dp), tint = tint)
}
