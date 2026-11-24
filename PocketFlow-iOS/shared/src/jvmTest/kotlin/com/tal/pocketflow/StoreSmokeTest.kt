package com.tal.pocketflow

import compat.android.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end check of the converted data layer on a real SQLite database. */
class StoreSmokeTest {
    private fun freshStore(): Store {
        installSqlite()
        val context = Context.app
        context.getDatabasePath("pocketflow.db").delete()
        context.getSharedPreferences("prefs", 0).edit().clear().commit()
        return Store(context)
    }

    @Test
    fun savesReadsAndRestores() {
        val store = freshStore()
        try {
            val account = Store.Account()
            account.name = "credit"; account.type = "credit"; account.closing = 31; account.charge = 10; account.start = "2026-01-01"
            store.save(account)
            assertTrue(account.id > 0)

            val purchase = Store.Tx()
            purchase.date = "2026-10-15"; purchase.amount = 90000; purchase.account = account.id; purchase.category = "בית"
            purchase.merchant = "עסק"; purchase.note = ""; purchase.tag = ""; purchase.kind = "expense"; purchase.count = 3; purchase.status = 1
            store.save(purchase)

            assertEquals(1, store.transactions()!!.size)
            assertEquals(90000L, store.transactions()!![0].amount)
            // Cash and bank transfer are created automatically, plus the card above.
            assertEquals(3, store.accounts()!!.size)
            assertEquals("credit", store.account(account.id)!!.name)

            val backup = store.backup()
            store.restore(backup)
            assertEquals(1, store.transactions()!!.size)
            assertEquals(3, store.accounts()!!.size)
        } finally {
            store.close()
        }
    }

    /** The Android project's own main()-style checks for the money and date rules. */
    @Test
    fun financeRules() {
        FinanceTest.main(arrayOf())
    }
}
