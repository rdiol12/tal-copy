package com.tal.workhours

import org.json.JSONArray
import org.json.JSONObject

private fun encodeSickTiers(tiers: List<SickTier>) = JSONArray().apply { tiers.forEach { t ->
    put(JSONObject().put("days",t.days ?: JSONObject.NULL).put("pay",t.payPercent).put("debit",t.debitPercent))
} }
private fun decodeSickTiers(a: JSONArray) = (0 until a.length()).map { i -> a.getJSONObject(i).let {
    SickTier(if(it.isNull("days")) null else it.getInt("days").also { n -> require(it.getDouble("days") == n.toDouble()) },it.getDouble("pay"),it.getDouble("debit"))
} }
fun encodeLeaveConfig(c: LeaveConfig) = JSONObject().apply {
    put("openingDate",c.openingDate);put("vacationOpening",c.vacationOpening);put("sickOpening",c.sickOpening)
    put("vacationMonthly",c.vacationMonthly);put("sickMonthly",c.sickMonthly);put("accrualDay",c.accrualDay)
    put("dayHours",c.dayHours);put("vacationPercent",c.vacationPercent);put("allowNegative",c.allowNegative)
    put("calendarSickDays",c.calendarSickDays);put("sickTiers",encodeSickTiers(c.sickTiers))
}
fun decodeLeaveConfig(o: JSONObject) = LeaveConfig(o.getString("openingDate"),o.getDouble("vacationOpening"),o.getDouble("sickOpening"),
    o.getDouble("vacationMonthly"),o.getDouble("sickMonthly"),o.getInt("accrualDay"),o.getDouble("dayHours"),o.getDouble("vacationPercent"),
    o.getBoolean("allowNegative"),o.getBoolean("calendarSickDays"),decodeSickTiers(o.getJSONArray("sickTiers")))
fun encodeLeaveRecord(r: LeaveRecord) = JSONObject().apply {
    put("id",r.id);put("date",r.date);put("type",r.type);put("fraction",r.fraction);put("dayHours",r.dayHours)
    put("vacationPercent",r.vacationPercent);put("eventId",r.eventId);put("eventStart",r.eventStart);put("calendarDays",r.calendarDays)
    put("sickTiers",encodeSickTiers(r.sickTiers));put("payOverride",r.payOverride ?: JSONObject.NULL)
    put("debitOverride",r.debitOverride ?: JSONObject.NULL);put("note",r.note);put("periodFrom",r.periodFrom);put("periodUntil",r.periodUntil)
}
fun decodeLeaveRecord(o: JSONObject) = LeaveRecord(o.getString("id"),o.getString("date"),o.getString("type"),o.getDouble("fraction"),
    o.getDouble("dayHours"),o.getDouble("vacationPercent"),o.getString("eventId"),o.getString("eventStart"),o.getBoolean("calendarDays"),
    decodeSickTiers(o.getJSONArray("sickTiers")),if(o.isNull("payOverride"))null else o.getDouble("payOverride"),
    if(o.isNull("debitOverride"))null else o.getDouble("debitOverride"),o.getString("note"),o.optString("periodFrom",""),o.optString("periodUntil",""))
fun encodeLeaveAdjustment(a: LeaveAdjustment) = JSONObject().put("id",a.id).put("date",a.date).put("type",a.type).put("days",a.days).put("note",a.note)
fun decodeLeaveAdjustment(o: JSONObject) = LeaveAdjustment(o.getString("id"),o.getString("date"),o.getString("type"),o.getDouble("days"),o.getString("note"))
