package com.tal.workhours

import java.io.ByteArrayOutputStream
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.math.BigDecimal

const val XLSX_MIME="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
data class ShiftExportLabels(val sheet: String,val periodStart: String,val periodEnd: String,val date: String,
    val start: String,val end: String,val duration: String,val total: String,val note: String, val leave: LeaveExportLabels = LeaveExportLabels())

/** Closed records only. Calculate the full day BEFORE applying office/home filtering, preserving breaks. */
fun shiftExportParts(data: AppData,period: PayPeriod,location: String?): List<PaidPart> {
    require(location==null || location in setOf(OFFICE,HOME))
    return summarize(data,period).days.flatMap { it.parts }.filter { location==null || it.shift.location==location }.sortedBy { it.shift.start }
}

internal fun excelDateSerial(value: LocalDateTime): Double {
    val day=value.toLocalDate()
    val days=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.of(1899,12,31),day)
    return days.toDouble()+(if(day>=LocalDate.of(1900,3,1))1 else 0)+value.toLocalTime().toNanoOfDay()/86_400_000_000_000.0
}
private fun xlsxXml(value: String): String = buildString {
    value.forEach { c -> when(c) {
        '&' -> append("&amp;"); '<' -> append("&lt;"); '>' -> append("&gt;"); '"' -> append("&quot;"); '\'' -> append("&apos;")
        else -> if(c=='\t' || c=='\n' || c=='\r' || c>=' ') append(c)
    } }
}
private fun xlsxNumber(value: Double): String {
    require(value.isFinite())
    return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}

/** Real OpenXML workbook with typed dates/times, cached SUM and elapsed-time format exceeding24h. */
fun shiftWorkbook(data: AppData,period: PayPeriod,location: String?,labels: ShiftExportLabels,rtl: Boolean): ByteArray {
    val rows=shiftExportParts(data,period,location)
    val last=rows.size+2
    val totalRow=last+1
    val noteRow=totalRow+2
    val sheet=labels.sheet.replace(Regex("[\\\\/?:*\\[\\]]")," ").trim().take(31).ifBlank { "Shifts" }
    val output=ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        fun write(value: String) { zip.write(value.toByteArray(Charsets.UTF_8)) }
        fun entry(name: String,value: String) { zip.putNextEntry(ZipEntry(name));write(value);zip.closeEntry() }
        fun text(ref: String,value: String,style: Int) = "<c r=\"$ref\" s=\"$style\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xlsxXml(value)}</t></is></c>"
        fun numeric(ref: String,value: Double,style: Int) = "<c r=\"$ref\" s=\"$style\" t=\"n\"><v>${xlsxNumber(value)}</v></c>"
        fun dateCell(ref: String,value: LocalDate,style: Int)=if(value.year<1900)text(ref,value.format(DateTimeFormatter.ofPattern("dd/MM/yyyy",Locale.ROOT)),style)
            else numeric(ref,excelDateSerial(value.atStartOfDay()),style)
        fun timeValue(epoch: Long): Double {
            val t=Instant.ofEpochMilli(epoch).atZone(ZoneId.of("Asia/Jerusalem")).toLocalDateTime()
            return if(t.year<1900)t.toLocalTime().toNanoOfDay()/86_400_000_000_000.0 else excelDateSerial(t)
        }
        entry("[Content_Types].xml","""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""")
        entry("_rels/.rels","""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
        entry("xl/workbook.xml","""<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><bookViews><workbookView/></bookViews><sheets><sheet name="${xlsxXml(sheet)}" sheetId="1" r:id="rId1"/></sheets><definedNames><definedName name="_xlnm.Print_Titles" localSheetId="0">${xlsxXml("'"+sheet.replace("'","''")+"'!\$1:\$2")}</definedName></definedNames><calcPr calcId="191029" fullCalcOnLoad="1"/></workbook>""")
        entry("xl/_rels/workbook.xml.rels","""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
        val border="""<border><left style="thin"><color rgb="FFDBE3ED"/></left><right style="thin"><color rgb="FFDBE3ED"/></right><top style="thin"><color rgb="FFDBE3ED"/></top><bottom style="thin"><color rgb="FFDBE3ED"/></bottom><diagonal/></border>"""
        val xfs=mutableListOf<String>()
        fun style(font: Int,fill: Int,num: Int=0) { xfs+="""<xf numFmtId="$num" fontId="$font" fillId="$fill" borderId="1" xfId="0" applyAlignment="1" applyFont="1" applyFill="1" applyBorder="1" applyNumberFormat="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>""" }
        style(0,0) //0
        style(1,2);style(1,2,164);style(1,3);style(1,3,164) //1–4period
        style(2,4) //5headers
        style(0,0,165);style(0,0,166);style(0,0,167) //6–8body
        style(1,3);style(1,3,167) //9–10total
        style(0,5,165);style(0,5,166);style(0,5,167) //11–13zebra
        entry("xl/styles.xml","""<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="4"><numFmt numFmtId="164" formatCode="dd/mm/yyyy"/><numFmt numFmtId="165" formatCode="dddd dd mmmm yyyy"/><numFmt numFmtId="166" formatCode="hh:mm"/><numFmt numFmtId="167" formatCode="[h]:mm:ss"/></numFmts><fonts count="3"><font><sz val="11"/><color rgb="FF182333"/><name val="Arial"/></font><font><b/><sz val="11"/><color rgb="FF15356C"/><name val="Arial"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Arial"/></font></fonts><fills count="6"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFC5EDF7"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFDDF3EF"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FF15356C"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFF4F7FB"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border>$border</borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="${xfs.size}">${xfs.joinToString("")}</cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>""")
        zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
        write("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetPr><pageSetUpPr fitToPage="1"/></sheetPr><dimension ref="A1:D$noteRow"/><sheetViews><sheetView workbookViewId="0" rightToLeft="${if(rtl)1 else 0}" showGridLines="0"><pane ySplit="2" topLeftCell="A3" activePane="bottomLeft" state="frozen"/><selection pane="bottomLeft" activeCell="A3" sqref="A3"/></sheetView></sheetViews><sheetFormatPr defaultRowHeight="22"/><cols><col min="1" max="1" width="42" customWidth="1"/><col min="2" max="3" width="19" customWidth="1"/><col min="4" max="4" width="21" customWidth="1"/></cols><sheetData>""")
        write("<row r=\"1\" ht=\"26\" customHeight=\"1\">"+text("A1",labels.periodStart,1)+dateCell("B1",period.from,2)+text("C1",labels.periodEnd,3)+dateCell("D1",period.end,4)+"</row>")
        write("<row r=\"2\" ht=\"28\" customHeight=\"1\">"+text("A2",labels.date,5)+text("B2",labels.start,5)+text("C2",labels.end,5)+text("D2",labels.duration,5)+"</row>")
        rows.forEachIndexed { i,p ->
            val row=i+3;val alt=if(i%2==0)0 else 5
            write("<row r=\"$row\">"+dateCell("A$row",LocalDate.parse(p.shift.workDate),6+alt)+numeric("B$row",timeValue(p.shift.start),7+alt)+numeric("C$row",timeValue(p.shift.end),7+alt)+numeric("D$row",p.netMs/86_400_000.0,8+alt)+"</row>")
        }
        val total=rows.sumOf { it.netMs }/86_400_000.0
        val formula=if(rows.isEmpty())"0" else "SUM(D3:D$last)"
        write("<row r=\"$totalRow\" ht=\"27\" customHeight=\"1\">"+text("A$totalRow",labels.total,9)+text("B$totalRow","",9)+text("C$totalRow","",9)+"<c r=\"D$totalRow\" s=\"10\" t=\"n\"><f>$formula</f><v>${xlsxNumber(total)}</v></c></row>")
        write("<row r=\"$noteRow\" ht=\"36\" customHeight=\"1\">"+text("A$noteRow",labels.note,0)+"</row></sheetData>")
        if(rows.isNotEmpty())write("<autoFilter ref=\"A2:D$last\"/>")
        write("""<mergeCells count="2"><mergeCell ref="A$totalRow:C$totalRow"/><mergeCell ref="A$noteRow:D$noteRow"/></mergeCells><printOptions horizontalCentered="1"/><pageMargins left="0.3" right="0.3" top="0.4" bottom="0.4" header="0.2" footer="0.2"/><pageSetup paperSize="9" orientation="portrait" fitToWidth="1" fitToHeight="0"/></worksheet>""")
        zip.closeEntry()
    }
    return if(location==null)appendLeaveSheet(output.toByteArray(),data,period,labels.leave,rtl) else output.toByteArray()
}
