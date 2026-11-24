package com.tal.pocketflow;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowAlertDialog;
import java.io.*;
import java.time.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="he-rIL-w411dp-h891dp-xxhdpi")
public class CreditCycleTest {
    Context context;Store store;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("pocketflow.db");context.getSharedPreferences("prefs",0).edit().clear().commit();store=new Store(context);store.getWritableDatabase();}
    @After public void cleanup(){store.close();}
    Store.Account card(boolean fixed){Store.Account a=new Store.Account();a.name="כאל";a.type=fixed?"fixed":"credit";a.start="2026-10-01";a.closing=9;a.charge=10;a.payment=fixed?500000:0;a.cycleConfigured=true;store.save(a);return a;}
    Store.Tx buy(Store.Account a,String date,long amount,int count){Store.Tx t=new Store.Tx();t.date=date;t.amount=amount;t.account=a.id;t.kind="expense";t.count=count;t.status=1;t.category="בית";t.merchant="חנות";t.note="";t.tag="";store.save(t);return t;}
    @Test public void octoberCycleRunsOct10ThroughNov9AndPaysNov10(){
        YearMonth m=YearMonth.of(2026,10);
        assertEquals(LocalDate.parse("2026-10-10"),Finance.creditCycleStart(m,9));assertEquals(LocalDate.parse("2026-11-09"),Finance.creditCycleEnd(m,9));
        Store.Account a=card(false);assertEquals(LocalDate.parse("2026-11-10"),store.creditCyclePaymentDate(a,m));
        assertEquals(LocalDate.parse("2026-10-10"),Finance.due(LocalDate.parse("2026-10-09"),"credit",9,10));
        for(String date:new String[]{"2026-10-10","2026-10-31","2026-11-09"})assertEquals(LocalDate.parse("2026-11-10"),Finance.due(LocalDate.parse(date),"credit",9,10));
        assertEquals(LocalDate.parse("2026-12-10"),Finance.due(LocalDate.parse("2026-11-10"),"credit",9,10));
        buy(a,"2026-10-09",100,1);buy(a,"2026-10-10",200,1);buy(a,"2026-11-09",300,1);buy(a,"2026-11-10",400,1);
        assertEquals(2,store.creditCycleTransactions(a,m).size());assertEquals(500,store.creditCycleTransactions(a,m).stream().mapToLong(t->t.amount).sum());
        assertEquals(500,store.charges(YearMonth.of(2026,11)).stream().filter(c->c.account==a.id).mapToLong(c->c.amount).sum());
        assertEquals(300,store.total(m,"expense")); // Ordinary monthly reporting remains calendar-based.
    }
    @Test public void fixedCycleDoesNotRepayNewCyclePurchasesOnTheSameChargeDay(){
        Store.Account a=card(true);buy(a,"2026-10-10",10000,1);buy(a,"2026-11-09",20000,1);buy(a,"2026-11-10",90000,1);
        assertEquals(30000,store.creditUsage(a,LocalDate.parse("2026-11-09")));
        assertEquals(90000,store.creditUsage(a,LocalDate.parse("2026-11-10")));
        assertEquals(30000,store.charges(YearMonth.of(2026,11)).stream().filter(c->c.account==a.id).mapToLong(c->c.amount).sum());
        assertEquals(0,store.creditUsage(a,LocalDate.parse("2026-12-10")));
    }
    @Test public void fixedCycleKeepsFutureInstallmentsReservedAndHonorsExplicitFirstDue(){
        Store.Account a=card(true);buy(a,"2026-10-10",30001,3);Store.Tx postponed=buy(a,"2026-10-20",20000,1);postponed.firstDue="2027-01-10";store.save(postponed);
        assertEquals(40000,store.creditUsage(a,LocalDate.parse("2026-11-10")));
        assertEquals(10001,store.charges(YearMonth.of(2026,11)).stream().filter(c->c.account==a.id).mapToLong(c->c.amount).sum());
        assertEquals(30000,store.creditUsage(a,LocalDate.parse("2026-12-10")));assertEquals(0,store.creditUsage(a,LocalDate.parse("2027-01-10")));
    }
    @Test public void settingCycleKeepsExistingDebtUntilFutureChargeAndSurvivesBackup()throws Exception{
        Store.Account a=card(true);a.cycleConfigured=false;a.closing=31;a.opening=4364680;store.save(a);
        long before=store.creditUsage(a,LocalDate.parse("2026-11-01"));a.cycleConfigured=true;a.closing=9;store.save(a,LocalDate.parse("2026-11-01"));
        assertEquals(before,store.creditUsage(a,LocalDate.parse("2026-11-01")));assertEquals(before,store.creditUsage(a,LocalDate.parse("2026-11-09")));assertEquals(before-500000,store.creditUsage(a,LocalDate.parse("2026-11-10")));
        JSONObject backup=store.backup();store.restore(backup);Store.Account restored=store.account(a.id);assertTrue(restored.cycleConfigured);assertEquals(9,restored.closing);assertEquals(before,store.creditUsage(restored,LocalDate.parse("2026-11-01")));
        backup.put("version",4);for(int i=0;i<backup.getJSONArray("accounts").length();i++)backup.getJSONArray("accounts").getJSONObject(i).remove("cycle_configured");store.restore(backup);assertFalse(store.account(a.id).cycleConfigured);
    }
    @Test public void upgradedDatabaseRetainsExistingAccountAndAddsDefaultPolicyFlag(){
        java.util.List<String> schema=new java.util.ArrayList<>();
        try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name IN ('accounts','rules','transactions')",null)){while(c.moveToNext())schema.add(c.getString(0).replace(",currency TEXT NOT NULL DEFAULT 'ILS',original_amount INTEGER NOT NULL DEFAULT 0,fx_rate TEXT NOT NULL DEFAULT '1',fx_fee TEXT NOT NULL DEFAULT '0',fx_date TEXT NOT NULL DEFAULT '',fx_source TEXT NOT NULL DEFAULT ''","").replace(",fx_fee TEXT NOT NULL DEFAULT '0'","").replace(",refund_of INTEGER NOT NULL DEFAULT 0","").replace(",cycle_configured INTEGER NOT NULL DEFAULT 0","").replace(",interval_months INTEGER NOT NULL DEFAULT 1,reminder_days INTEGER NOT NULL DEFAULT 7,renewal TEXT NOT NULL DEFAULT ''",""));}
        store.close();context.deleteDatabase("pocketflow.db");android.database.sqlite.SQLiteDatabase db=context.openOrCreateDatabase("pocketflow.db",0,null);
        for(String sql:schema)db.execSQL(sql);
        db.execSQL("INSERT INTO accounts(name,type,closing,charge,credit_limit,payment,opening,start) VALUES('כרטיס ישן','fixed',31,10,5000000,500000,4364680,'2026-10-01')");db.setVersion(4);db.close();
        store=new Store(context);assertEquals("כרטיס ישן",store.accounts().get(0).name);assertEquals(4364680,store.accounts().get(0).opening);assertFalse(store.accounts().get(0).cycleConfigured);assertEquals(9,store.getReadableDatabase().getVersion());
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void fixedCardEditorShowsCycleFieldAndSavesIt()throws Exception{
        Store.Account a=card(true);a.closing=31;store.save(a);
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity activity=controller.get();StoreTest.navigate(activity,4);activity.getWindow().getDecorView().findViewWithTag("edit-account-"+a.id).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();EditText closing=d.getWindow().getDecorView().findViewWithTag("יום סגירת מחזור האשראי");assertNotNull(closing);assertEquals(View.VISIBLE,closing.getVisibility());closing.setText("9");
            TextView preview=d.getWindow().getDecorView().findViewWithTag("credit-cycle-preview");assertTrue(preview.getText().toString().contains("10/"));
            save(d.getWindow().getDecorView(),"credit-cycle-editor");d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertFalse(d.isShowing());assertEquals(9,store.account(a.id).closing);assertTrue(store.account(a.id).cycleConfigured);
            activity.getWindow().getDecorView().findViewWithTag("credit-cycle-"+a.id).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();AlertDialog cycle=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(cycle.getWindow().getDecorView().findViewWithTag("credit-cycle-total"));save(cycle.getWindow().getDecorView(),"credit-cycle-details");
        }
    }
    void save(View view,String name)throws Exception{view.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY));view.layout(0,0,1080,1920);Bitmap bitmap=Bitmap.createBitmap(1080,1920,Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));File dir=new File("build/design-previews");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
}
