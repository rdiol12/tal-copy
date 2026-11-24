package com.tal.pocketflow;

import android.content.Context;
import android.widget.EditText;
import android.widget.Spinner;
import android.app.AlertDialog;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class ForeignFeeTest {
    Context context;
    Store store;
    Store.Account card;

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("pocketflow.db");
        context.getSharedPreferences("prefs",0).edit().clear().commit();
        context.getSharedPreferences("security",0).edit().clear().commit();
        store = new Store(context);
        card = new Store.Account();
        card.name = "כרטיס בדיקה";
        card.type = "credit";
        card.start = "2025-01-01";
        card.closing = 31;
        card.charge = 10;
        card.fxFee = "3.2";
        store.save(card);
    }

    @After public void close() { store.close(); }

    Store.Tx purchase() {
        Store.Tx tx = new Store.Tx();
        tx.account = card.id;
        tx.date = "2026-11-13";
        tx.kind = "expense";
        tx.category = "בית";
        tx.merchant = "חנות";
        tx.note = tx.tag = "";
        tx.count = tx.status = 1;
        tx.currency = "USD";
        tx.originalAmount = 10000;
        tx.fxRate = "3";
        tx.fxFee = card.fxFee;
        tx.amount = Finance.convert(tx.originalAmount,tx.fxRate,tx.fxFee);
        store.save(tx);
        return tx;
    }

    @Test public void cardChangesKeepSavedPurchasesAndBackupPreservesBothRates() throws Exception {
        Store.Tx tx = purchase();
        card.fxFee = "1.5";
        store.edit("עדכון עמלה", () -> store.save(card));
        Store.Tx saved = store.transaction(tx.id);
        assertEquals("3.2", saved.fxFee);
        assertEquals(30960, saved.amount);
        JSONObject backup = store.backup();
        store.restore(backup);
        assertEquals("1.5",store.account(card.id).fxFee);
        assertEquals("3.2",store.transaction(tx.id).fxFee);
        assertEquals(30960,store.transaction(tx.id).amount);
        store.save(store.transaction(tx.id));
        assertEquals(30960,store.transaction(tx.id).amount);
    }

    @Test public void versionSevenRestoreDefaultsFeesToZero() throws Exception {
        Store.Tx tx = purchase();
        JSONObject old = store.backup();
        old.put("version",7);
        for (int i=0;i<old.getJSONArray("accounts").length();i++)
            old.getJSONArray("accounts").getJSONObject(i).remove("fx_fee");
        JSONObject transaction = old.getJSONArray("transactions").getJSONObject(0);
        transaction.remove("fx_fee");
        transaction.put("amount",30000);
        store.restore(old);
        assertEquals("0",store.account(card.id).fxFee);
        assertEquals("0",store.transaction(tx.id).fxFee);
        assertEquals(30000,store.transaction(tx.id).amount);
    }

    @Test public void invalidBackupRollsBackAndUndoRestoresCardFee() throws Exception {
        Store.Tx tx = purchase();
        card.fxFee="2";
        store.edit("שינוי עמלה",()->store.save(card));
        store.undo();
        assertEquals("3.2",store.account(card.id).fxFee);
        JSONObject bad = store.backup();
        bad.getJSONArray("transactions").getJSONObject(0).put("fx_fee","-3");
        try { store.restore(bad); fail("Invalid fee accepted"); }
        catch (IllegalArgumentException expected) {}
        assertEquals(30960,store.transaction(tx.id).amount);
    }

    @Test public void formUsesSelectedCardsFeeAndPreservesSnapshotOnEdit() throws Exception {
        purchase();
        card.fxFee="5";
        store.save(card);
        try (ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity=controller.get();
            activity.getWindow().getDecorView().findViewWithTag("add-movement").performClick();
            AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
            android.view.View form=dialog.getWindow().getDecorView();
            Spinner account=form.findViewWithTag("אמצעי תשלום");
            for (int i=0;i<account.getCount();i++) if(card.name.equals(account.getItemAtPosition(i))) account.setSelection(i);
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Spinner currency=form.findViewWithTag("מטבע");
            currency.setSelection(0);
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            ((EditText)form.findViewWithTag("שער המרה")).setText("3");
            ((EditText)form.findViewWithTag("סכום")).setText("100");
            assertTrue(StoreTest.find(form,"סכום ההמרה: \u200E₪ 300.00\u200E\nעמלת מט״ח 5%: \u200E₪ 15.00\u200E\nסה״כ לחיוב: \u200E₪ 315.00\u200E")!=null);
            ((EditText)form.findViewWithTag("קטגוריה")).setText("בית");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertFalse(dialog.isShowing());
            Store.Tx created = store.transactions().stream().filter(t -> t.id != 1).findFirst().get();
            assertEquals(31500,created.amount);
            assertEquals("5",created.fxFee);
            java.lang.reflect.Method open=MainActivity.class.getDeclaredMethod("transactionDialog",Store.Tx.class);
            open.setAccessible(true);
            open.invoke(activity,store.transaction(1));
            dialog=ShadowAlertDialog.getLatestAlertDialog();
            form=dialog.getWindow().getDecorView();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertNotNull(StoreTest.find(form,"סכום ההמרה: \u200E₪ 300.00\u200E\nעמלת מט״ח 3.2%: \u200E₪ 9.60\u200E\nסה״כ לחיוב: \u200E₪ 309.60\u200E"));
        }
    }
}
