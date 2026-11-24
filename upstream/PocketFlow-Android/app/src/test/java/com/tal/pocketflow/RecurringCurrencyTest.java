package com.tal.pocketflow;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowToast;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="he-rIL-w411dp-h891dp-xxhdpi")
public class RecurringCurrencyTest {
    Context context;
    Store store;
    Store.Account card;
    static final String COLUMNS=",currency TEXT NOT NULL DEFAULT 'ILS',original_amount INTEGER NOT NULL DEFAULT 0,fx_rate TEXT NOT NULL DEFAULT '1',fx_fee TEXT NOT NULL DEFAULT '0',fx_date TEXT NOT NULL DEFAULT '',fx_source TEXT NOT NULL DEFAULT ''";

    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("pocketflow.db");
        context.getSharedPreferences("prefs",0).edit().clear().commit();
        context.getSharedPreferences("security",0).edit().clear().commit();
        store=new Store(context);
        card=new Store.Account();card.name="כרטיס מט״ח";card.type="credit";card.start="2025-01-01";
        card.closing=31;card.charge=10;card.fxFee="3.2";store.save(card);
    }
    @After public void close(){store.close();}

    Store.Rule rule(String currency,long amount,String rate){
        Store.Rule r=new Store.Rule();r.name="מנוי";r.account=card.id;r.category="חשבונות";
        r.start="2026-11-14";r.end="";r.active=1;r.currency=currency;r.originalAmount=amount;
        r.fxRate=rate;r.fxFee=card.fxFee;r.fxDate="2026-11-13";r.fxSource="manual";
        r.amount=Finance.convert(amount,rate,r.fxFee);store.save(r);return r;
    }
    Store.Tx occurrence(Store.Rule r,int month){
        return store.transactions().stream().filter(t->t.rule==r.id&&LocalDate.parse(t.date).getMonthValue()==month).findFirst().get();
    }
    @Test public void dollarsAndEurosGenerateFullCurrencySnapshotsAndUseCardFee(){
        Store.Rule usd=rule("USD",1499,"3"),eur=rule("EUR",4999,"4");
        store.ensureMonth(YearMonth.of(2026,11));
        Store.Tx a=occurrence(usd,11),b=occurrence(eur,11);
        assertEquals(4641,a.amount);assertEquals(20636,b.amount);
        assertEquals(1499,a.originalAmount);assertEquals("USD",a.currency);assertEquals("3",a.fxRate);
        assertEquals("3.2",a.fxFee);assertEquals("2026-11-13",a.fxDate);
        card.fxFee="5";store.save(card);store.ensureMonth(YearMonth.of(2026,12));
        assertEquals(4641,store.transaction(a.id).amount);
        assertEquals(4722,occurrence(usd,12).amount);
        assertEquals("5",occurrence(usd,12).fxFee);
        assertEquals(4722,store.expectedRuleAmount(usd));
        assertEquals(2,store.month(YearMonth.of(2026,12)).size());
    }
    @Test public void editingForeignRuleRebuildsPendingPreservesActualAndSkippedAndUndoRestores()throws Exception{
        Store.Rule usd=rule("USD",10000,"3");
        store.ensureMonth(YearMonth.of(2027,1));
        Store.Tx actual=occurrence(usd,11),skipped=occurrence(usd,12);
        actual.status=1;store.save(actual);store.remove(skipped);
        usd.originalAmount=20000;usd.fxRate="4";usd.amount=Finance.convert(usd.originalAmount,usd.fxRate,usd.fxFee);
        store.edit("עריכת הוראת קבע",()->store.updateRule(usd,YearMonth.of(2027,1)));
        assertEquals(30960,store.transaction(actual.id).amount);
        Store.Tx january=store.transactions().stream().filter(t->t.rule==usd.id&&t.date.equals("2027-01-14")).findFirst().get();
        assertEquals(82560,january.amount);assertEquals(20000,january.originalAmount);
        assertTrue(store.month(YearMonth.of(2026,12)).isEmpty());
        store.undo();
        assertEquals(10000,store.rules().get(0).originalAmount);
        assertEquals(30960,store.transactions().stream().filter(t->t.date.equals("2027-01-14")).findFirst().get().amount);
    }
    @Test public void backupRoundTripsCurrencyAndRestoresVersionEightRulesAsShekels()throws Exception{
        Store.Rule usd=rule("USD",1499,"3");store.ensureMonth(YearMonth.of(2026,11));
        JSONObject backup=store.backup();store.restore(backup);
        Store.Rule restored=store.rules().get(0);
        assertEquals("USD",restored.currency);assertEquals(1499,restored.originalAmount);
        assertEquals("3.2",restored.fxFee);assertEquals(4641,occurrence(restored,11).amount);
        JSONObject bad=new JSONObject(backup.toString());
        bad.getJSONArray("rules").getJSONObject(0).put("fx_rate","4");
        try{store.restore(bad);fail("Inconsistent currency accepted");}catch(IllegalArgumentException expected){}
        assertEquals(1499,store.rules().get(0).originalAmount);
        JSONObject legacy=new JSONObject(backup.toString()).put("version",8);
        JSONObject row=legacy.getJSONArray("rules").getJSONObject(0);
        for(String key:new String[]{"currency","original_amount","fx_rate","fx_fee","fx_date","fx_source"})row.remove(key);
        store.restore(legacy);restored=store.rules().get(0);
        assertEquals("ILS",restored.currency);assertEquals(4641,restored.originalAmount);
        assertEquals("0",restored.fxFee);
    }
    @Test public void versionEightDatabaseMigrationKeepsOriginalShekelRules(){
        Store.Rule r=new Store.Rule();r.name="מנוי ישן";r.account=card.id;r.category="בית";
        r.start="2026-11-14";r.end="";r.active=1;r.amount=1499;store.save(r);
        List<String> sql=new ArrayList<>();
        try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name IN ('accounts','rules','transactions','undo_action')",null)){
            while(c.moveToNext())sql.add(c.getString(0).replace(COLUMNS,""));
        }
        store.close();context.deleteDatabase("pocketflow.db");
        android.database.sqlite.SQLiteDatabase old=context.openOrCreateDatabase("pocketflow.db",0,null);
        for(String statement:sql)old.execSQL(statement);
        old.execSQL("INSERT INTO accounts(id,name,type,closing,charge,credit_limit,payment,opening,start) VALUES(1,'bank','bank',31,10,0,0,0,'2026-01-01')");
        old.execSQL("INSERT INTO rules(name,amount,account,category,start,end,active) VALUES('old',1499,1,'home','2026-11-14','',1)");
        old.setVersion(8);old.close();store=new Store(context);
        Store.Rule upgraded=store.rules().get(0);assertEquals("ILS",upgraded.currency);
        assertEquals(1499,upgraded.amount);assertEquals(1499,upgraded.originalAmount);
        store.ensureMonth(YearMonth.of(2026,11));assertEquals(1499,store.transactions().get(0).amount);
    }
    @Test public void normalRuleFormCreatesForeignRuleAndEditingKeepsOriginalAmount()throws Exception{
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity activity=controller.get();StoreTest.navigate(activity,3);
            activity.getWindow().getDecorView().findViewWithTag("add-movement").performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();View form=dialog.getWindow().getDecorView();
            ((EditText)form.findViewWithTag("שם הוראת הקבע / בית העסק")).setText("Amazon");
            Spinner account=form.findViewWithTag("חשבון בנק או כרטיס לחיוב");
            for(int i=0;i<account.getCount();i++)if(card.name.equals(account.getItemAtPosition(i)))account.setSelection(i);
            ((Spinner)form.findViewWithTag("מטבע")).setSelection(0);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            ((EditText)form.findViewWithTag("שער המרה")).setText("3");
            ((EditText)form.findViewWithTag("סכום")).setText("14.99");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertFalse(ShadowToast.getTextOfLatestToast(),dialog.isShowing());
            Store.Rule saved=store.rules().get(0);assertEquals("USD",saved.currency);
            assertEquals(1499,saved.originalAmount);assertEquals(4641,saved.amount);
            activity.getWindow().getDecorView().findViewWithTag("edit-rule-"+saved.id).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            dialog=ShadowAlertDialog.getLatestAlertDialog();form=dialog.getWindow().getDecorView();
            assertEquals("14.99",((EditText)form.findViewWithTag("סכום")).getText().toString());
            assertEquals(0,((Spinner)form.findViewWithTag("מטבע")).getSelectedItemPosition());
        }
    }
}
