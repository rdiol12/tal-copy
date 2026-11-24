package com.tal.workhours

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID

fun leaveTypeLabel(type: String) = tr(when(type) {SICK -> R.string.leave_sick;HOLIDAY -> R.string.leave_holiday;UNPAID -> R.string.leave_salary_choice;else -> R.string.leave_vacation})
private fun leaveNumber(s: String): Double = normalizedNumber(s).trim().replace(',','.').toDoubleOrNull()?.takeIf { it.isFinite() }
    ?: kotlin.error(tr(R.string.leave_invalid))
private fun leaveDraft(n: Double): String = java.math.BigDecimal.valueOf(n).stripTrailingZeros().toPlainString()
private fun leaveFailure(e: Exception) = e.message?.takeIf { it.isNotBlank() && it != "Failed requirement." } ?: tr(R.string.leave_invalid)

@Composable
fun LeaveDate(label: String, date: LocalDate, enabled: Boolean = true, onChange: (LocalDate)->Unit) {
    val context=LocalContext.current
    OutlinedButton(enabled=enabled,onClick={ DatePickerDialog(context,{ _,y,m,d -> onChange(LocalDate.of(y,m+1,d)) },date.year,date.monthValue-1,date.dayOfMonth).show() },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
        Text(label+": "+date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy",appLocale)))
    }
}
@Composable
private fun LeaveInput(label: String, value: String, enabled: Boolean, onChange: (String)->Unit) {
    OutlinedTextField(value,onValueChange={ onChange(it.take(40)) },enabled=enabled,label={ Text(label) },singleLine=true,modifier=Modifier.fillMaxWidth())
}
@Composable
private fun LeaveToggle(label: String, value: Boolean, enabled: Boolean, onChange: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(label,modifier=Modifier.weight(1f).padding(vertical=12.dp));Switch(value,onChange,enabled=enabled)
    }
}
@Composable
private fun LeaveDialog(title: String, busy: Boolean, error: String?, onDismiss: ()->Unit, onSave: ()->Unit, content: @Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest={ if(!busy)onDismiss() }) {
        Surface(shape=MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max=680.dp).padding(18.dp)) {
                Text(title,style=MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
                error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(enabled=!busy,onClick=onDismiss) { Text(tr(R.string.text_a7c55a8d06)) }
                    TextButton(enabled=!busy,onClick=onSave) { Text(tr(R.string.text_e69323393b)) }
                }
            }
        }
    }
}

@Composable
fun LeaveDashboardCard(data: AppData, today: LocalDate, period: PayPeriod, onOpen: ()->Unit) {
    val payments=remember(data,period) { leavePayments(data.leaveRecords).filter { period.contains(LocalDate.parse(it.record.date)) } }
    InfoCard(tr(R.string.leave_title)) {
        if(data.leaveConfig.openingDate.isEmpty()) Text(tr(R.string.leave_setup_hint)) else {
            Text(tr(R.string.leave_asof)+" "+today.toString(),style=MaterialTheme.typography.bodySmall)
            listOf(VACATION,SICK).forEach { type -> Metric(leaveTypeLabel(type),number(leaveBalance(data,type,today))+" "+tr(R.string.leave_days)) }
            Metric(tr(R.string.leave_period_used),number(payments.sumOf { it.debitDays })+" "+tr(R.string.leave_days))
            Metric(tr(R.string.leave_pay),money(payments.sumOf { it.paidHours }*data.settings.wage,taxCurrency(data.settings.taxCountry)))
            Text(tr(R.string.leave_pay_hint),style=MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick=onOpen,modifier=Modifier.fillMaxWidth()) { Text(tr(R.string.leave_manage)) }
    }
}

@Composable
fun LeaveScreen(data: AppData, busy: Boolean, onChange: ((AppData)->AppData,()->Unit)->Unit, onMessage: (String)->Unit) {
    val today=LocalDate.now(israelZone)
    var month by rememberSaveable { mutableStateOf(YearMonth.from(periodFor(today,data.settings.periodStartDay).from).toString()) }
    val period=periodFromMonth(YearMonth.parse(month),data.settings.periodStartDay)
    var configOpen by remember { mutableStateOf(false) }
    var entryOpen by remember { mutableStateOf(false) }
    var settlementOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LeaveRecord?>(null) }
    var deleting by remember { mutableStateOf<LeaveRecord?>(null) }
    var adjustmentOpen by remember { mutableStateOf(false) }
    var deleteAdjustment by remember { mutableStateOf<LeaveAdjustment?>(null) }
    val payments=remember(data,period) { leavePayments(data.leaveRecords).filter { period.contains(LocalDate.parse(it.record.date)) }.sortedByDescending { it.record.date } }
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { LeaveDashboardCard(data,today,period) { configOpen=true } }
        item { OutlinedButton(enabled=!busy,onClick={ configOpen=true },modifier=Modifier.fillMaxWidth()) { Text(tr(R.string.leave_config)) } }
        item { PeriodChooser(period,data.settings.periodStartDay,periodFor(today,data.settings.periodStartDay)) { month=YearMonth.from(it.from).toString() } }
        item {
            Button(enabled=!busy && data.leaveConfig.openingDate.isNotEmpty(),onClick={ editing=null;entryOpen=true },modifier=Modifier.fillMaxWidth()) { Text(tr(R.string.leave_add)) }
            OutlinedButton(enabled=!busy && data.leaveConfig.openingDate.isNotEmpty() && data.settings.monthlyHours>0,onClick={editing=null;settlementOpen=true},modifier=Modifier.fillMaxWidth()) { Text(tr(R.string.leave_settlement)) }
            Metric(tr(R.string.leave_missing_hours),hoursText(monthlyLeaveShortage(data,period)*HOUR_MS))
            if(data.settings.monthlyHours<=0)Text(tr(R.string.leave_target_needed),style=MaterialTheme.typography.bodySmall)
            Text(tr(R.string.leave_record_hint),style=MaterialTheme.typography.bodySmall)
            ShiftExportButton(data,period,null,!busy && (payments.isNotEmpty() || shiftExportParts(data,period,null).isNotEmpty()),onMessage)
        }
        if(payments.isEmpty())item { Text(tr(R.string.leave_empty)) }
        items(payments.size,key={payments[it].record.id}) { i ->
            val p=payments[i];val r=p.record
            InfoCard((if(r.isSettlement)tr(R.string.leave_settlement)+" · " else "")+leaveTypeLabel(r.type)+" · "+r.date) {
                Metric(tr(R.string.leave_fraction),number(r.fraction))
                Metric(tr(R.string.leave_hours),number(p.hours))
                Metric(tr(R.string.leave_pay_percent),number(p.payPercent)+"%")
                Metric(tr(R.string.leave_debited),number(p.debitDays))
                Metric(tr(R.string.leave_pay),money(p.paidHours*data.settings.wage,taxCurrency(data.settings.taxCountry)))
                if(r.isSettlement && r.dayHours*r.fraction > monthlyLeaveShortage(data,PayPeriod(LocalDate.parse(r.periodFrom),LocalDate.parse(r.periodUntil)),r.id)+0.0000001)Text(tr(R.string.leave_settlement_review),color=MaterialTheme.colorScheme.error)
                if(r.type == SICK) Text(tr(R.string.leave_event_start)+": "+r.eventStart+" · "+tr(R.string.leave_day_number)+" "+p.ordinal)
                if(r.note.isNotBlank())Text(r.note)
                Row {
                    TextButton(enabled=!busy,onClick={ editing=r;if(r.isSettlement)settlementOpen=true else entryOpen=true }) { Text(tr(R.string.text_39fe25939b)) }
                    TextButton(enabled=!busy,onClick={ deleting=r }) { Text(tr(R.string.text_7c8173fafa),color=MaterialTheme.colorScheme.error) }
                }
            }
        }
        item { ExpandableCard(tr(R.string.leave_ledger),summary={ Text(tr(R.string.leave_ledger_hint),style=MaterialTheme.typography.bodySmall) }) {
            leaveMovements(data,period.end).filter { period.contains(it.date) }.asReversed().forEach { m ->
                val label=when(m.kind) { "OPENING" -> R.string.leave_opening;"ACCRUAL" -> R.string.leave_accrual;"USED" -> R.string.leave_used;else -> R.string.leave_adjustment }
                Metric(m.date.toString()+" · "+leaveTypeLabel(m.type)+" · "+tr(label),number(m.days))
                if(m.note.isNotBlank())Text(m.note,style=MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(enabled=!busy && data.leaveConfig.openingDate.isNotEmpty(),onClick={ adjustmentOpen=true }) { Text(tr(R.string.leave_adjustment)) }
            data.leaveAdjustments.filter { period.contains(LocalDate.parse(it.date)) }.forEach { a ->
                TextButton(enabled=!busy,onClick={ deleteAdjustment=a }) { Text(tr(R.string.text_7c8173fafa)+": "+a.date+" · "+number(a.days)+" · "+a.note) }
            }
        } }
    }
    if(configOpen) LeaveConfigEditor(data,busy,{configOpen=false}) { config ->
        onChange({ current -> require(current.leaveConfig==data.leaveConfig) { tr(R.string.leave_changed) };current.copy(leaveConfig=config).also { it.validate();requireLeaveBalance(it) } },{configOpen=false;onMessage(tr(R.string.text_e0aad7a0c2))})
    }
    if(entryOpen) LeaveEntryEditor(data,editing,busy,{entryOpen=false}) { added ->
        val original=editing
        onChange({ current ->
            require(current.leaveConfig==data.leaveConfig) { tr(R.string.leave_changed) }
            if(original!=null)require(current.leaveRecords.find { it.id==original.id }==original) { tr(R.string.leave_changed) }
            current.copy(leaveRecords=current.leaveRecords.filterNot { it.id==original?.id }+added).also { it.validate();requireLeaveBalance(it) }
        },{entryOpen=false;onMessage(tr(R.string.text_e0aad7a0c2))})
    }
    if(settlementOpen) LeaveSettlementEditor(data,period,editing,busy,{settlementOpen=false}) { proposed ->
        val original=editing
        val selected=PayPeriod(LocalDate.parse(proposed.periodFrom),LocalDate.parse(proposed.periodUntil))
        onChange({current ->
            require(current.leaveConfig==data.leaveConfig) {tr(R.string.leave_changed)}
            if(original!=null)require(current.leaveRecords.find {it.id==original.id}==original) {tr(R.string.leave_changed)}
            val record=makeLeaveSettlement(current,selected,proposed.dayHours*proposed.fraction,proposed.type,original)
            current.copy(leaveRecords=current.leaveRecords.filterNot {it.id==original?.id}+record)
        },{settlementOpen=false;onMessage(tr(R.string.text_e0aad7a0c2))})
    }
    if(adjustmentOpen) LeaveAdjustmentEditor(data,busy,{adjustmentOpen=false}) { a ->
        onChange({ current -> current.copy(leaveAdjustments=current.leaveAdjustments+a).also { it.validate();requireLeaveBalance(it) } },{adjustmentOpen=false;onMessage(tr(R.string.text_e0aad7a0c2))})
    }
    val removal=deleting;val adjustment=deleteAdjustment
    if(removal!=null || adjustment!=null) AlertDialog(onDismissRequest={if(!busy){deleting=null;deleteAdjustment=null}},title={Text(tr(R.string.text_7c8173fafa))},text={Text(tr(R.string.leave_delete_hint))},confirmButton={
        TextButton(enabled=!busy,onClick={ onChange({ current ->
            if(removal!=null)require(current.leaveRecords.find { it.id==removal.id }==removal) { tr(R.string.leave_changed) }
            if(adjustment!=null)require(current.leaveAdjustments.find { it.id==adjustment.id }==adjustment) { tr(R.string.leave_changed) }
            current.copy(leaveRecords=current.leaveRecords.filterNot { it.id==removal?.id },leaveAdjustments=current.leaveAdjustments.filterNot { it.id==adjustment?.id }).also { it.validate();requireLeaveBalance(it) }
        },{deleting=null;deleteAdjustment=null;onMessage(tr(R.string.text_e0aad7a0c2))}) }) { Text(tr(R.string.text_7c8173fafa)) }
    },dismissButton={TextButton(enabled=!busy,onClick={deleting=null;deleteAdjustment=null}){Text(tr(R.string.text_a7c55a8d06))}})
}

@Composable
private fun LeaveConfigEditor(data: AppData,busy: Boolean,onDismiss: ()->Unit,onSave: (LeaveConfig)->Unit) {
    val c=data.leaveConfig
    var date by remember { mutableStateOf(c.openingDate.takeIf { it.isNotEmpty() }?.let(LocalDate::parse) ?: LocalDate.now(israelZone)) }
    var vacation by remember { mutableStateOf(leaveDraft(c.vacationOpening)) };var sick by remember { mutableStateOf(leaveDraft(c.sickOpening)) }
    var vacationMonthly by remember { mutableStateOf(leaveDraft(c.vacationMonthly)) };var sickMonthly by remember { mutableStateOf(leaveDraft(c.sickMonthly)) }
    var accrual by remember { mutableStateOf(c.accrualDay.toString()) };var hours by remember { mutableStateOf(leaveDraft(if(c.openingDate.isEmpty())data.settings.regularHours else c.dayHours)) }
    var percent by remember { mutableStateOf(leaveDraft(c.vacationPercent)) };var negative by remember { mutableStateOf(c.allowNegative) }
    var calendar by remember { mutableStateOf(c.calendarSickDays) }
    var tiers by remember { mutableStateOf(c.sickTiers) }
    // Text drafts must survive incomplete decimal input; validation occurs on save.
    val days=remember { mutableStateListOf<String>().apply { addAll(c.sickTiers.map { it.days?.toString().orEmpty() }) } }
    val pay=remember { mutableStateListOf<String>().apply { addAll(c.sickTiers.map { leaveDraft(it.payPercent) }) } }
    val debit=remember { mutableStateListOf<String>().apply { addAll(c.sickTiers.map { leaveDraft(it.debitPercent) }) } }
    var error by remember { mutableStateOf<String?>(null) }
    LeaveDialog(tr(R.string.leave_config),busy,error,onDismiss,{
        try {
            val parsed=tiers.indices.map { i -> SickTier(if(tiers[i].days==null)null else days[i].asLocalizedInt() ?: kotlin.error(tr(R.string.leave_invalid)),leaveNumber(pay[i]),leaveNumber(debit[i])) }
            val result=LeaveConfig(date.toString(),leaveNumber(vacation),leaveNumber(sick),leaveNumber(vacationMonthly),leaveNumber(sickMonthly),accrual.asLocalizedInt() ?: 0,leaveNumber(hours),leaveNumber(percent),negative,calendar,parsed)
            data.copy(leaveConfig=result).also { it.validate();requireLeaveBalance(it) };onSave(result)
        } catch(e:Exception){error=leaveFailure(e)}
    }) {
        Text(tr(R.string.leave_config_hint),style=MaterialTheme.typography.bodySmall)
        LeaveDate(tr(R.string.leave_opening_date),date,!busy){date=it}
        LeaveInput(tr(R.string.leave_vacation)+" · "+tr(R.string.leave_opening),vacation,!busy){vacation=it}
        LeaveInput(tr(R.string.leave_vacation)+" · "+tr(R.string.leave_monthly),vacationMonthly,!busy){vacationMonthly=it}
        LeaveInput(tr(R.string.leave_sick)+" · "+tr(R.string.leave_opening),sick,!busy){sick=it}
        LeaveInput(tr(R.string.leave_sick)+" · "+tr(R.string.leave_monthly),sickMonthly,!busy){sickMonthly=it}
        LeaveInput(tr(R.string.leave_accrual_day),accrual,!busy){accrual=it}
        Text(tr(R.string.leave_accrual_hint),style=MaterialTheme.typography.bodySmall)
        LeaveInput(tr(R.string.leave_day_hours),hours,!busy){hours=it}
        LeaveInput(tr(R.string.leave_vacation_percent),percent,!busy){percent=it}
        LeaveToggle(tr(R.string.leave_allow_negative),negative,!busy){negative=it}
        LeaveToggle(tr(R.string.leave_calendar_days),calendar,!busy){calendar=it}
        Text(tr(R.string.leave_calendar_hint),style=MaterialTheme.typography.bodySmall)
        Text(tr(R.string.leave_sick_tiers),style=MaterialTheme.typography.titleMedium)
        Text(tr(R.string.leave_tier_hint),style=MaterialTheme.typography.bodySmall)
        tiers.forEachIndexed { i,t ->
            HorizontalDivider();Text((i+1).toString()+" · "+tr(R.string.leave_tier))
            if(t.days!=null)LeaveInput(tr(R.string.leave_number_days),days[i],!busy){days[i]=it}
            LeaveToggle(tr(R.string.leave_remaining),t.days==null,!busy){ unlimited ->
                if(unlimited) { tiers=tiers.take(i)+t.copy(days=null);while(days.size>i+1){days.removeAt(days.lastIndex);pay.removeAt(pay.lastIndex);debit.removeAt(debit.lastIndex)} }
                else { tiers=tiers.toMutableList().also { it[i]=t.copy(days=1);if(i==it.lastIndex)it.add(SickTier()) };days[i]="1";if(days.size<tiers.size){days.add("");pay.add("100");debit.add("100")} }
            }
            LeaveInput(tr(R.string.leave_pay_percent),pay[i],!busy){pay[i]=it}
            LeaveInput(tr(R.string.leave_debit_percent),debit[i],!busy){debit[i]=it}
            if(t.days!=null)TextButton(enabled=!busy,onClick={tiers=tiers.filterIndexed { n,_ -> n!=i };days.removeAt(i);pay.removeAt(i);debit.removeAt(i)}) { Text(tr(R.string.text_7c8173fafa)) }
        }
        OutlinedButton(enabled=!busy && tiers.size<20,onClick={val i=tiers.lastIndex;tiers=tiers.dropLast(1)+SickTier(1,100.0)+tiers.last();days.add(i,"1");pay.add(i,"100");debit.add(i,"100")}) { Text(tr(R.string.leave_add_tier)) }
    }
}

@Composable
fun LeaveEntryEditor(data: AppData,existing: LeaveRecord?,busy: Boolean,onDismiss: ()->Unit,initialType: String = VACATION,initialDate: LocalDate? = null,onSave: (List<LeaveRecord>)->Unit) {
    val c=data.leaveConfig
    var type by remember { mutableStateOf(existing?.type ?: initialType) }
    var from by remember { mutableStateOf(existing?.date?.let(LocalDate::parse) ?: initialDate ?: LocalDate.now(israelZone)) }
    var until by remember { mutableStateOf(from) }
    var fraction by remember { mutableStateOf(leaveDraft(existing?.fraction ?: 1.0)) }
    var hours by remember { mutableStateOf(leaveDraft(existing?.dayHours ?: c.dayHours)) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var onlyWorkDays by remember { mutableStateOf(existing==null && initialDate==null) }
    var percentage by remember { mutableStateOf(false) }
    var eventId by remember { mutableStateOf(existing?.eventId.orEmpty()) }
    val events=remember(data) { data.leaveRecords.filter { it.type==SICK }.distinctBy { it.eventId }.sortedByDescending { it.eventStart } }
    var eventMenu by remember { mutableStateOf(false) }
    var start by remember { mutableStateOf(existing?.eventStart?.takeIf { it.isNotBlank() }?.let(LocalDate::parse) ?: from) }
    var overridePay by remember { mutableStateOf(existing?.payOverride?.let(::leaveDraft).orEmpty()) }
    var overrideDebit by remember { mutableStateOf(existing?.debitOverride?.let(::leaveDraft).orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<List<LeaveRecord>?>(null) }
    fun build(): List<LeaveRecord> {
        val end = if(existing!=null)from else until
        require(end>=from && ChronoUnitDays(from,end)<=366) { tr(R.string.leave_date_error) }
        val selectedEvent=events.find { it.eventId==eventId }
        val id=selectedEvent?.eventId ?: UUID.randomUUID().toString()
        val rows=leaveRangeDates(data,from,end,onlyWorkDays,type,existing?.id).map { date ->
            LeaveRecord(id=existing?.id ?: UUID.randomUUID().toString(),date=date.toString(),type=type,fraction=leaveNumber(fraction)/(if(percentage)100.0 else 1.0),dayHours=leaveNumber(hours),
                vacationPercent=existing?.vacationPercent ?: if(type==HOLIDAY)100.0 else c.vacationPercent,eventId=if(type==SICK)id else "",eventStart=if(type==SICK)(selectedEvent?.eventStart ?: start.toString()) else "",
                calendarDays=selectedEvent?.calendarDays ?: c.calendarSickDays,sickTiers=if(type==SICK)selectedEvent?.sickTiers ?: c.sickTiers else emptyList(),
                payOverride=overridePay.takeIf { it.isNotBlank() }?.let(::leaveNumber),debitOverride=overrideDebit.takeIf { it.isNotBlank() }?.let(::leaveNumber),note=note.trim())
        }.toList()
        require(rows.isNotEmpty()) { tr(R.string.leave_no_dates) }
        data.copy(leaveRecords=data.leaveRecords.filterNot { it.id==existing?.id }+rows).also { it.validate();requireLeaveBalance(it) }
        return rows
    }
    LeaveDialog(tr(if(existing==null)R.string.leave_add else R.string.text_39fe25939b),busy,error,onDismiss,{
        try { preview=build();error=null } catch(e:Exception){error=leaveFailure(e)}
    }) {
        Column { listOf(VACATION,SICK,HOLIDAY).forEach { v -> FilterChip(selected=type==v,enabled=!busy && existing==null,onClick={type=v},label={Text(leaveTypeLabel(v))}) } }
        LeaveDate(tr(R.string.leave_from),from,!busy){val previous=from;from=it;if(eventId.isEmpty() && start==previous)start=from;if(until<from)until=from}
        if(existing==null)LeaveDate(tr(R.string.leave_until),until,!busy){until=it}
        if(existing==null)LeaveToggle(tr(R.string.leave_workdays_only),onlyWorkDays,!busy){onlyWorkDays=it}
        LeaveToggle(tr(R.string.leave_use_percent),percentage,!busy){new ->
            val n=runCatching {leaveNumber(fraction)}.getOrNull();if(n!=null)fraction=leaveDraft(if(new)n*100 else n/100);percentage=new
        }
        LeaveInput(tr(if(percentage)R.string.leave_day_percent else R.string.leave_fraction),fraction,!busy){fraction=it}
        LeaveInput(tr(R.string.leave_day_hours),hours,!busy){hours=it}
        if(type==HOLIDAY)Text(tr(R.string.leave_holiday_hint),style=MaterialTheme.typography.bodySmall)
        if(type==SICK) {
            Box {
                OutlinedButton(enabled=!busy,onClick={eventMenu=true},modifier=Modifier.fillMaxWidth()) { Text(events.find { it.eventId==eventId }?.let { tr(R.string.leave_continue)+" · "+it.eventStart } ?: tr(R.string.leave_new_event)) }
                DropdownMenu(expanded=eventMenu,onDismissRequest={eventMenu=false}) {
                    DropdownMenuItem(text={Text(tr(R.string.leave_new_event))},onClick={eventId="";eventMenu=false})
                    events.forEach { event -> DropdownMenuItem(text={Text(event.eventStart+" · "+event.note.take(25))},onClick={eventId=event.eventId;eventMenu=false}) }
                }
            }
            if(eventId.isEmpty())LeaveDate(tr(R.string.leave_event_start),start,!busy){start=it}
        }
        LeaveInput(tr(R.string.leave_override_pay),overridePay,!busy){overridePay=it}
        LeaveInput(tr(R.string.leave_override_debit),overrideDebit,!busy){overrideDebit=it}
        Text(tr(R.string.leave_override_hint),style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(note,{note=it.take(2000)},enabled=!busy,label={Text(tr(R.string.leave_note))},modifier=Modifier.fillMaxWidth())
        Text(tr(R.string.leave_record_hint),style=MaterialTheme.typography.bodySmall)
    }
    preview?.let { added ->
        val proposed=data.copy(leaveRecords=data.leaveRecords.filterNot { it.id==existing?.id }+added)
        val paid=leavePayments(proposed.leaveRecords).filter { p -> added.any { it.id==p.record.id } }
        AlertDialog(onDismissRequest={if(!busy)preview=null},title={Text(tr(R.string.leave_preview))},text={Column(Modifier.verticalScroll(rememberScrollState())) {
            Metric(tr(R.string.leave_number_days),number(added.sumOf { it.fraction }))
            Metric(tr(R.string.leave_debited),number(paid.sumOf { it.debitDays }))
            Metric(tr(R.string.leave_pay),money(paid.sumOf { it.paidHours }*data.settings.wage,taxCurrency(data.settings.taxCountry)))
            val workDates=data.shifts.map { it.workDate }.toSet()+listOfNotNull(data.active?.let { localTime(it.start).toLocalDate().toString() })
            if(added.any { it.date in workDates })Text(tr(R.string.leave_work_warning),color=MaterialTheme.colorScheme.error)
            Text(tr(R.string.leave_pay_hint),style=MaterialTheme.typography.bodySmall)
            added.forEach { Text(it.date+" · "+number(it.fraction)) }
        }},confirmButton={TextButton(enabled=!busy,onClick={onSave(added)}) {Text(tr(R.string.text_e69323393b))}},dismissButton={TextButton(enabled=!busy,onClick={preview=null}) {Text(tr(R.string.text_a7c55a8d06))}})
    }
}
private fun ChronoUnitDays(from: LocalDate,until: LocalDate) = java.time.temporal.ChronoUnit.DAYS.between(from,until)

@Composable
private fun LeaveAdjustmentEditor(data: AppData,busy: Boolean,onDismiss: ()->Unit,onSave: (LeaveAdjustment)->Unit) {
    var date by remember { mutableStateOf(LocalDate.now(israelZone)) };var type by remember { mutableStateOf(VACATION) }
    var days by remember { mutableStateOf("") };var note by remember { mutableStateOf("") };var error by remember { mutableStateOf<String?>(null) }
    LeaveDialog(tr(R.string.leave_adjustment),busy,error,onDismiss,{
        try { val a=LeaveAdjustment(date=date.toString(),type=type,days=leaveNumber(days),note=note.trim());data.copy(leaveAdjustments=data.leaveAdjustments+a).also {it.validate();requireLeaveBalance(it)};onSave(a) }
        catch(e:Exception){error=leaveFailure(e)}
    }) {
        Row { listOf(VACATION,SICK).forEach { v -> FilterChip(type==v,{type=v},enabled=!busy,label={Text(leaveTypeLabel(v))}) } }
        LeaveDate(tr(R.string.leave_date),date,!busy){date=it}
        LeaveInput(tr(R.string.leave_adjust_days),days,!busy){days=it}
        OutlinedTextField(note,{note=it.take(2000)},enabled=!busy,label={Text(tr(R.string.leave_note))},modifier=Modifier.fillMaxWidth())
    }
}


@Composable
private fun LeaveSettlementEditor(data: AppData,selected: PayPeriod,existing: LeaveRecord?,busy: Boolean,onDismiss: ()->Unit,onSave: (LeaveRecord)->Unit) {
    val period=remember { if(existing?.isSettlement==true)PayPeriod(LocalDate.parse(existing.periodFrom),LocalDate.parse(existing.periodUntil)) else selected }
    val missing=monthlyLeaveShortage(data,period,existing?.id)
    var type by remember { mutableStateOf(existing?.type ?: VACATION) }
    var percentage by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf(leaveDraft(existing?.let {it.dayHours*it.fraction} ?: missing)) }
    var error by remember { mutableStateOf<String?>(null) }
    fun hours()=leaveNumber(amount)*(if(percentage)data.leaveConfig.dayHours/100.0 else 1.0)
    LeaveDialog(tr(R.string.leave_settlement),busy,error,onDismiss,{
        try {onSave(makeLeaveSettlement(data,period,hours(),type,existing))}catch(e:Exception){error=leaveFailure(e)}
    }) {
        Text(period.from.toString()+" — "+period.end.toString())
        Metric(tr(R.string.leave_missing_hours),hoursText(missing*HOUR_MS))
        Text(tr(R.string.leave_settlement_hint),style=MaterialTheme.typography.bodySmall)
        if(period.end>=LocalDate.now(israelZone))Text(tr(R.string.leave_incomplete_period),color=MaterialTheme.colorScheme.error)
        Column {listOf(VACATION,UNPAID).forEach {v -> FilterChip(type==v,{type=v},enabled=!busy,label={Text(leaveTypeLabel(v))})}}
        LeaveToggle(tr(R.string.leave_use_percent),percentage,!busy){new ->
            val n=runCatching{leaveNumber(amount)}.getOrNull();if(n!=null)amount=leaveDraft(if(new)n/data.leaveConfig.dayHours*100 else n/100*data.leaveConfig.dayHours);percentage=new
        }
        LeaveInput(tr(if(percentage)R.string.leave_day_percent else R.string.leave_fill_hours),amount,!busy){amount=it}
        val h=runCatching{hours()}.getOrNull()
        if(h!=null && h.isFinite() && h>=0) {
            Metric(tr(R.string.leave_hours),hoursText(h*HOUR_MS))
            Metric(tr(R.string.leave_day_percent),number(h/data.leaveConfig.dayHours*100)+"%")
            Metric(tr(R.string.leave_debited),number(if(type==VACATION)h/data.leaveConfig.dayHours else 0.0))
            Metric(tr(if(type==UNPAID)R.string.leave_salary_adjustment else R.string.leave_pay),money(h*data.settings.wage*(if(type==UNPAID)1.0 else data.leaveConfig.vacationPercent/100),taxCurrency(data.settings.taxCountry)))
        }
        Text(tr(R.string.leave_pay_hint),style=MaterialTheme.typography.bodySmall)
    }
}
