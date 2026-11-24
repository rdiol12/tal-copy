// Links into the app (iOS only):
//   pocketflow://income?amount=8450.00&month=2026-11&date=2026-12-01&done=1&wage=5512   salary sent by ShiftHours
//   pocketflow://expense?amount=45.90&merchant=Cafe&card=Visa                            a Wallet tap, via Shortcuts
//   pocketflow://widget/movement | rule | account                                        the home-screen widgets
// Either one opens the new-movement form filled in; nothing is saved until the user confirms.
// "wage" (agorot per hour) lets expenses also be shown as hours of work.
package com.tal.pocketflow

import android.app.Screen
import android.content.Context
import android.widget.Toast
import compat.time.LocalDate

object IncomingLinks {
    private var pending: String? = null

    /** Called by the app shell with the opened URL; waits for the screen if the app is still starting. */
    fun handle(url: String) { pending = url; deliver() }

    fun deliver() {
        val url = pending ?: return
        val activity = Screen.activity as? MainActivity ?: return
        pending = null
        android.os.Handler.main.post {
            runCatching { open(activity, url) }.onFailure { Toast.makeText(activity, "לא ניתן לפתוח את הקישור: " + (it.message ?: ""), Toast.LENGTH_LONG).show() }
        }
    }

    private fun open(activity: MainActivity, url: String) {
        val action = url.substringAfter("://", "").substringBefore('?').trim('/').lowercase()
        val query = parseQuery(url.substringAfter('?', ""))
        val wage = query["wage"]?.toLongOrNull()
        if (wage != null && wage > 0) Context.app.getSharedPreferences("shifthours", Context.MODE_PRIVATE).edit().putLong("wage", wage).apply()
        val today = LocalDate.now().toString()
        val date = query["date"]?.takeIf { runCatching { LocalDate.parse(it) }.isSuccess } ?: today
        when (action) {
            "income", "salary" -> {
                val cents = parseCents(query["amount"].orEmpty())
                require(cents > 0) { "חסר סכום" }
                val month = query["month"].orEmpty()
                val marker = if (month.isEmpty()) "" else "ShiftHours $month"
                val note = listOf(marker, query["note"].orEmpty()).filter { it.isNotEmpty() }.joinToString(" · ")
                activity.openLinkedMovement("income", cents, date, query["merchant"] ?: "משכורת", note, "", marker, query["done"] != "0")
            }
            "expense", "card", "add" -> {
                val cents = parseCents(query["amount"].orEmpty())
                require(cents > 0) { "חסר סכום" }
                activity.openLinkedMovement("expense", cents, date, query["merchant"].orEmpty().trim(), query["note"].orEmpty(), query["card"].orEmpty(), "", true)
            }
            // The home-screen widgets: the same quick actions as the Android widgets.
            "widget/movement", "widget/rule", "widget/account" ->
                activity.hostNewIntent(android.content.Intent().putExtra(WidgetActions.EXTRA, action.substringAfter('/')))
            "shifthours" -> Toast.makeText(activity, "הנתונים מ־ShiftHours עודכנו", Toast.LENGTH_SHORT).show()
            else -> throw IllegalArgumentException(action)
        }
    }

    internal fun parseQuery(query: String): Map<String, String> {
        val result = HashMap<String, String>()
        for (part in query.split('&')) {
            if (part.isEmpty()) continue
            val key = decode(part.substringBefore('=')); val value = decode(part.substringAfter('=', ""))
            result[key] = value
        }
        return result
    }

    /** Percent-decoding (UTF-8). */
    internal fun decode(text: String): String {
        val bytes = ArrayList<Byte>(text.length)
        var index = 0
        while (index < text.length) {
            val c = text[index]
            val hex = if (c == '%' && index + 2 < text.length) text.substring(index + 1, index + 3).toIntOrNull(16) else null
            if (hex != null) { bytes.add(hex.toByte()); index += 3 }
            else { c.toString().encodeToByteArray().forEach { bytes.add(it) }; index++ }
        }
        return bytes.toByteArray().decodeToString()
    }

    /**
     * Amount in agorot from text such as "45.90", "₪1,504.00", "45,90" or "ILS 12": the last
     * separator followed by one or two digits is the decimal point, the others group thousands.
     */
    internal fun parseCents(text: String): Long {
        val kept = text.filter { it.isDigit() || it == '.' || it == ',' }
        if (kept.none { it.isDigit() }) return 0
        val last = kept.indexOfLast { it == '.' || it == ',' }
        val decimals = if (last >= 0) kept.length - last - 1 else 0
        val whole: String; val fraction: String
        if (last >= 0 && decimals in 1..2) { whole = kept.substring(0, last).filter { it.isDigit() }; fraction = kept.substring(last + 1).padEnd(2, '0') }
        else { whole = kept.filter { it.isDigit() }; fraction = "00" }
        return (whole.ifEmpty { "0" }.toLongOrNull() ?: return 0) * 100 + fraction.toLong()
    }
}
