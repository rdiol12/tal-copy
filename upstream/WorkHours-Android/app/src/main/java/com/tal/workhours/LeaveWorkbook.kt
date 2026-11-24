package com.tal.workhours

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

data class LeaveExportLabels(val sheet: String = "Leave and sickness", val headers: List<String> = listOf("Date","Type","Days","Hours","Pay %","Days deducted","Pay value","Event start","Note"),
    val vacation: String = "Paid vacation", val sick: String = "Sick leave", val total: String = "Total", val holiday: String = "Holiday", val unpaid: String = "Unpaid leave")
private fun leaveXml(s: String) = s.filter { it >= ' ' || it in "\n\r\t" }.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")

/** Keep shifts untouched; add a separate sheet only to the All export. */
fun appendLeaveSheet(original: ByteArray,data: AppData,period: PayPeriod,labels: LeaveExportLabels,rtl: Boolean): ByteArray {
    val rows=leavePayments(data.leaveRecords).filter { period.contains(LocalDate.parse(it.record.date)) }
    if(rows.isEmpty())return original
    require(labels.headers.size==9)
    val parts=linkedMapOf<String,ByteArray>()
    ZipInputStream(ByteArrayInputStream(original)).use { zip -> while(true) { val e=zip.nextEntry ?: break;parts[e.name]=zip.readBytes() } }
    fun change(name: String,from: String,to: String) { parts[name]=parts.getValue(name).toString(Charsets.UTF_8).replace(from,to).toByteArray(Charsets.UTF_8) }
    val styles=parts.getValue("xl/styles.xml").toString(Charsets.UTF_8)
    val styleCount=Regex("<cellXfs count=\"(\\d+)\">").find(styles)!!.groupValues[1].toInt()
    val moneyStyle="<xf numFmtId=\"4\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
    val moneyTotal="<xf numFmtId=\"4\" fontId=\"1\" fillId=\"3\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
    parts["xl/styles.xml"]=styles.replace("<cellXfs count=\"$styleCount\">","<cellXfs count=\"${styleCount+2}\">").replace("</cellXfs>",moneyStyle+moneyTotal+"</cellXfs>").toByteArray(Charsets.UTF_8)
    val clean=labels.sheet.replace(Regex("[\\\\/?:*\\[\\]]")," ").trim().take(31).ifBlank { "Leave" }
    // A generated numeric suffix makes the sheet name distinct from the chosen work-sheet name.
    val sheet=clean.take(27)+" (2)"
    change("[Content_Types].xml","</Types>","<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>")
    change("xl/workbook.xml","</sheets>","<sheet name=\"${leaveXml(sheet)}\" sheetId=\"2\" r:id=\"rId3\"/></sheets>")
    change("xl/_rels/workbook.xml.rels","</Relationships>","<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/></Relationships>")
    fun text(ref: String,s: String,style: Int=0)="<c r=\"$ref\" s=\"$style\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${leaveXml(s)}</t></is></c>"
    fun num(ref: String,n: Double,style: Int=0)="<c r=\"$ref\" s=\"$style\" t=\"n\"><v>${java.math.BigDecimal.valueOf(n).stripTrailingZeros().toPlainString()}</v></c>"
    val last=rows.size+2;val totalRow=last+1
    val xml=buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0" rightToLeft="${if(rtl)1 else 0}"><pane ySplit="2" topLeftCell="A3" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><cols><col min="1" max="2" width="23" customWidth="1"/><col min="3" max="7" width="18" customWidth="1"/><col min="8" max="8" width="23" customWidth="1"/><col min="9" max="9" width="45" customWidth="1"/></cols><sheetData>""")
        append("<row r=\"1\">"+text("A1",period.from.toString(),1)+text("B1",period.end.toString(),1)+"</row>")
        append("<row r=\"2\" ht=\"42\" customHeight=\"1\">");labels.headers.forEachIndexed { i,h -> append(text(('A'+i).toString()+"2",h,5)) };append("</row>")
        rows.forEachIndexed { i,p ->
            val n=i+3;val r=p.record
            append("<row r=\"$n\">")
            append(num("A$n",excelDateSerial(LocalDate.parse(r.date).atStartOfDay()),2))
            append(text("B$n",when(r.type) {SICK -> labels.sick;HOLIDAY -> labels.holiday;UNPAID -> labels.unpaid;else -> labels.vacation}))
            append(num("C$n",r.fraction));append(num("D$n",p.hours));append(num("E$n",p.payPercent));append(num("F$n",p.debitDays));append(num("G$n",p.paidHours*data.settings.wage,styleCount))
            append(text("H$n",r.eventStart));append(text("I$n",r.note));append("</row>")
        }
        append("<row r=\"$totalRow\">"+text("A$totalRow",labels.total,9))
        val sums=mapOf('C' to rows.sumOf {it.record.fraction},'D' to rows.sumOf {it.hours},'F' to rows.sumOf {it.debitDays},'G' to rows.sumOf {it.paidHours}*data.settings.wage)
        sums.forEach { (col,value) -> append("<c r=\"$col$totalRow\" s=\"${if(col=='G')styleCount+1 else 9}\" t=\"n\"><f>SUM(${col}3:$col$last)</f><v>$value</v></c>") }
        append("</row></sheetData><autoFilter ref=\"A2:I$last\"/></worksheet>")
    }
    parts["xl/worksheets/sheet2.xml"]=xml.toByteArray(Charsets.UTF_8)
    val output=ByteArrayOutputStream()
    ZipOutputStream(output).use { zip -> parts.forEach { (name,bytes) -> zip.putNextEntry(ZipEntry(name));zip.write(bytes);zip.closeEntry() } }
    return output.toByteArray()
}
