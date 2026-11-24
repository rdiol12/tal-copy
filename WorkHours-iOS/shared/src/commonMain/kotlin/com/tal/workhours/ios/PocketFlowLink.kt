// iOS addition: hands the period's salary to the PocketFlow app as an income entry.
// The amount can be changed before sending, and PocketFlow asks for confirmation before saving.
// While the pay period is still open it is sent as an expected income (for PocketFlow's forecast).
// The hourly pay goes along, so PocketFlow can show expenses as hours of work.
package com.tal.workhours

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.content.Context
import compat.android.platformOpenUrl
import compat.time.LocalDate
import compat.time.YearMonth
import kotlin.math.roundToLong

private fun hebrew(): Boolean = AppText.language == "he"
private fun text(he: String, en: String): String = if (hebrew()) he else en

/** "8450.00" for 8450.0; a plain number the other app can parse whatever the language. */
private fun plainAmount(value: Double): String {
    val cents = (value * 100).roundToLong().coerceAtLeast(0)
    return "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}

private fun parseAmount(text: String): Double? = text.trim().replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 }

private fun encode(text: String): String = buildString {
    for (byte in text.encodeToByteArray()) {
        val c = byte.toInt() and 0xFF
        if (c.toChar().isLetterOrDigit() && c < 128 || c.toChar() in "-._~") append(c.toChar())
        else append('%').append(c.toString(16).uppercase().padStart(2, '0'))
    }
}

/** The link PocketFlow understands; see IncomingLinks in the PocketFlow port. */
internal fun pocketFlowSalaryLink(amount: Double, month: YearMonth, date: LocalDate, final: Boolean, hours: Double): String {
    val wage = if (hours > 0) (amount / hours * 100).roundToLong() else 0L
    return "pocketflow://income?amount=${plainAmount(amount)}&month=$month&date=$date&done=${if (final) 1 else 0}" +
        (if (wage > 0) "&wage=$wage" else "") + "&merchant=${encode(text("משכורת", "Salary"))}"
}

@Composable
fun PocketFlowSalaryButton(summary: PeriodSummary, today: LocalDate) {
    val month = YearMonth.from(summary.period.from)
    val final = today >= summary.period.until
    val prefs = remember { Context.app.getSharedPreferences("pocketflow_link", Context.MODE_PRIVATE) }
    var sent by remember(month) { mutableStateOf(prefs.getBoolean("sent-$month", false)) }
    var open by remember { mutableStateOf(false) }
    var missing by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true; missing = false }, modifier = Modifier.fillMaxWidth()) {
        Text(when {
            final && sent -> text("המשכורת נשלחה ל־PocketFlow ✓", "Salary sent to PocketFlow ✓")
            final -> text("שליחת המשכורת ל־PocketFlow", "Send salary to PocketFlow")
            else -> text("שליחת משכורת צפויה ל־PocketFlow", "Send expected salary to PocketFlow")
        })
    }
    if (!open) return
    val estimate = remember(summary) { summary.net?.net ?: summary.gross }
    val netKnown = remember(summary) { summary.net != null }
    val hours = summary.totalMs / HOUR_MS
    var amount by remember(summary) { mutableStateOf(plainAmount(estimate)) }
    val parsed = parseAmount(amount)
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(text("משכורת ל־PocketFlow", "Salary to PocketFlow")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text("תקופה: ", "Period: ") + summary.period.from + " – " + summary.period.end, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(text("סכום", "Amount")) }, isError = parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text(
                    if (netKnown) text("הסכום המוצע הוא אומדן הנטו. אפשר לשנות אותו לפי תלוש השכר.", "The suggested amount is the estimated net pay. Change it to match your payslip.")
                    else text("הסכום המוצע הוא הברוטו. אפשר לשנות אותו לפי תלוש השכר.", "The suggested amount is the gross pay. Change it to match your payslip."),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (final) text("PocketFlow ייפתח עם הכנסה מוכנה לאישור.", "PocketFlow opens with the income ready to confirm.")
                    else text("התקופה עדיין פתוחה, ולכן הסכום יישלח כהכנסה צפויה. שליחה נוספת בסוף החודש תעדכן אותה.", "The period is still open, so this is sent as an expected income. Sending again at month end updates it."),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (missing) Text(text("PocketFlow אינו מותקן במכשיר הזה.", "PocketFlow is not installed on this device."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = {
                val value = parsed ?: return@TextButton
                val date = if (final) today else summary.period.until
                if (platformOpenUrl(pocketFlowSalaryLink(value, month, date, final, hours))) {
                    if (final) { prefs.edit().putBoolean("sent-$month", true).apply(); sent = true }
                    open = false
                } else missing = true
            }) { Text(text("שליחה", "Send")) }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text(text("ביטול", "Cancel")) } },
    )
}
