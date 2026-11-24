package com.tal.pocketflow;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class StoreTest {
    Store store;Context context;
    @Before public void before(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("pocketflow.db");context.getSharedPreferences("prefs",0).edit().clear().commit();store=new Store(context);store.getWritableDatabase();}
    @After public void after(){store.close();}
    Store.Account account(String type){Store.Account a=new Store.Account();a.name=type;a.type=type;a.closing=31;a.charge=31;a.start="2026-01-01";a.payment=30000;store.save(a);return a;}
    Store.Tx expense(Store.Account a,String date,long amount){Store.Tx t=new Store.Tx();t.date=date;t.amount=amount;t.account=a.id;t.category="בית";t.merchant="עסק";t.note="";t.tag="";t.kind="expense";t.count=1;t.status=1;return t;}
    Store.Rule rule(Store.Account a){Store.Rule r=new Store.Rule();r.name="מנוי";r.amount=9900;r.account=a.id;r.category="חשבונות";r.start="2026-01-31";r.end="";r.active=1;store.save(r);return r;}
    @Test public void explicitRepairReversesPrematureFixedPaymentWithoutChangingOpeningOrTransactions()throws Exception{
        Store.Account a=account("fixed");a.start="2026-10-01";a.charge=10;a.opening=4364680;a.payment=500000;store.save(a);
        assertEquals(3864680,store.creditUsage(a,LocalDate.parse("2026-11-01")));
        store.correctLegacyFixedStart(a,LocalDate.parse("2026-11-01"),31);
        assertEquals(4364680,store.creditUsage(a,LocalDate.parse("2026-11-01")));assertEquals(4364680,store.creditUsage(a,LocalDate.parse("2026-11-09")));assertEquals(3864680,store.creditUsage(a,LocalDate.parse("2026-11-10")));
        assertEquals(4364680,store.account(a.id).opening);assertEquals(500000,store.account(a.id).payment);assertEquals("2026-10-01",store.account(a.id).start);assertTrue(store.transactions().isEmpty());
        try{store.correctLegacyFixedStart(a,LocalDate.parse("2026-11-01"),31);fail();}catch(IllegalArgumentException expected){}
        JSONObject saved=store.backup();store.restore(saved);assertEquals(4364680,store.creditUsage(store.account(a.id),LocalDate.parse("2026-11-01")));
    }
    @Test public void newFixedPaymentDoesNotRepayPastChargeDatesAndKeepsPolicyHistory()throws Exception{
        Store.Account a=account("credit");a.charge=10;a.opening=4364680;a.start="2026-10-01";a.payment=0;store.save(a);
        Store.Tx split=expense(a,"2026-10-15",90000);split.count=3;store.save(split);
        long before=store.creditUsage(a,LocalDate.parse("2026-11-01"));assertEquals(4454680,before);
        a.type="fixed";a.payment=500000;store.save(a,LocalDate.parse("2026-11-01"));
        assertEquals(before,store.creditUsage(a,LocalDate.parse("2026-11-01")));
        assertEquals(before,store.creditUsage(a,LocalDate.parse("2026-11-09")));
        assertEquals(before-500000,store.creditUsage(a,LocalDate.parse("2026-11-10")));
        assertEquals(500000,store.charges(YearMonth.of(2026,11)).stream().filter(c->c.account==a.id).mapToLong(c->c.amount).sum());
        assertEquals(0,store.charges(YearMonth.of(2026,10)).stream().filter(c->c.account==a.id).mapToLong(c->c.amount).sum());
        a.payment=600000;store.save(a,LocalDate.parse("2026-11-02"));assertEquals(before,store.creditUsage(a,LocalDate.parse("2026-11-02")));assertEquals(before-600000,store.creditUsage(a,LocalDate.parse("2026-11-10")));
        a.payment=700000;store.save(a,LocalDate.parse("2026-12-01"));assertEquals(before-600000,store.creditUsage(a,LocalDate.parse("2026-12-01")));assertEquals(before-1300000,store.creditUsage(a,LocalDate.parse("2026-12-10")));
        JSONObject backup=store.backup();store.restore(backup);assertEquals(a.billingHistory,store.account(a.id).billingHistory);assertEquals(before-1300000,store.creditUsage(store.account(a.id),LocalDate.parse("2026-12-10")));
    }
    @Test public void fixedAmountChangedOnChargeDateAppliesOnlyToFollowingCharge(){
        Store.Account a=account("fixed");a.opening=1000000;a.payment=100000;a.charge=10;a.start="2026-10-01";store.save(a);
        assertEquals(800000,store.creditUsage(a,LocalDate.parse("2026-11-10")));
        a.payment=500000;store.save(a,LocalDate.parse("2026-11-10"));assertEquals(800000,store.creditUsage(a,LocalDate.parse("2026-11-10")));assertEquals(800000,store.creditUsage(a,LocalDate.parse("2026-12-09")));assertEquals(300000,store.creditUsage(a,LocalDate.parse("2026-12-10")));
    }
    @Test public void switchingToFullBillingKeepsFutureInstallmentsReserved(){
        Store.Account a=account("fixed");a.opening=0;a.payment=10000;a.charge=10;a.start="2026-01-01";store.save(a);
        Store.Tx split=expense(a,"2026-01-15",90000);split.count=3;store.save(split);
        assertEquals(80000,store.creditUsage(a,LocalDate.parse("2026-02-11")));
        a.type="credit";a.payment=0;store.save(a,LocalDate.parse("2026-02-11"));assertEquals(80000,store.creditUsage(a,LocalDate.parse("2026-02-11")));assertEquals(80000,store.creditUsage(a,LocalDate.parse("2026-03-09")));assertEquals(30000,store.creditUsage(a,LocalDate.parse("2026-03-10")));assertEquals(0,store.creditUsage(a,LocalDate.parse("2026-04-10")));
        assertEquals(10000,store.charges(YearMonth.of(2026,2)).stream().filter(c->c.account==a.id).mapToLong(c->c.amount).sum());assertEquals(50000,store.charges(YearMonth.of(2026,3)).stream().filter(c->c.account==a.id).mapToLong(c->c.amount).sum());
    }
    @Test public void fullBillingReleasesOnlyDueInstallmentsAndOpeningRepayments() throws Exception {
        Store.Account a=account("credit");a.start="2026-01-01";a.closing=31;a.charge=10;a.opening=50000;a.openingPayment=20000;store.save(a);
        Store.Tx split=expense(a,"2026-01-15",30001);split.count=3;store.save(split);store.save(expense(a,"2026-01-20",15000));
        Store.Tx pending=expense(a,"2026-01-20",9000);pending.status=0;store.save(pending);
        assertEquals(50000,store.creditUsage(a,LocalDate.parse("2026-01-09")));
        assertEquals(75001,store.creditUsage(a,LocalDate.parse("2026-02-09")));
        assertEquals(30000,store.creditUsage(a,LocalDate.parse("2026-02-10")));
        assertEquals(10000,store.creditUsage(a,LocalDate.parse("2026-03-10")));
        assertEquals(0,store.creditUsage(a,LocalDate.parse("2026-04-10")));
        assertEquals(45001,store.charges(YearMonth.of(2026,2)).stream().filter(c->c.account==a.id&&c.tx!=pending.id).mapToLong(c->c.amount).sum());
        assertEquals(45001,store.total(YearMonth.of(2026,1),"expense"));
        JSONObject backup=store.backup();assertEquals(9,backup.getInt("version"));store.restore(backup);assertEquals(20000,store.account(a.id).openingPayment);
        assertEquals(30000,store.creditUsage(store.account(a.id),LocalDate.parse("2026-02-10")));
    }
    @Test public void openingRepaymentHandlesShortMonthsBaselineAndFinalPartialPayment(){
        LocalDate start=LocalDate.parse("2026-01-31");
        assertEquals(50001,Finance.openingRemaining(50001,20000,start,31,start));
        assertEquals(50001,Finance.openingRemaining(50001,20000,start,31,LocalDate.parse("2026-02-27")));
        assertEquals(30001,Finance.openingRemaining(50001,20000,start,31,LocalDate.parse("2026-02-28")));
        assertEquals(10001,Finance.openingRemaining(50001,20000,start,31,LocalDate.parse("2026-03-31")));
        assertEquals(0,Finance.openingRemaining(50001,20000,start,31,LocalDate.parse("2026-04-30")));
        assertEquals(0,Finance.openingRemaining(Long.MAX_VALUE,Long.MAX_VALUE,start,31,LocalDate.parse("2026-02-28")));
    }
    @Test public void oldBackupRetainsManualOpeningAndNegativeRepaymentRollsBack() throws Exception {
        Store.Account a=account("credit");a.opening=50000;a.openingPayment=20000;store.save(a);
        JSONObject old=store.backup();old.put("version",2);for(int n=0;n<old.getJSONArray("accounts").length();n++)old.getJSONArray("accounts").getJSONObject(n).remove("opening_payment");
        store.restore(old);assertEquals(0,store.account(a.id).openingPayment);assertEquals(50000,store.creditUsage(store.account(a.id),LocalDate.parse("2027-01-01")));
        JSONObject invalid=store.backup();invalid.getJSONArray("accounts").getJSONObject(0).put("opening_payment",-1);
        try{store.restore(invalid);fail();}catch(IllegalArgumentException expected){}
        assertEquals(50000,store.account(a.id).opening);assertEquals(0,store.account(a.id).openingPayment);
    }
    @Test public void recurringBackfillsShortMonthsAndDoesNotDuplicateOrResurrect(){
        Store.Account a=account("bank");rule(a);store.ensureMonth(YearMonth.of(2026,3));store.ensureMonth(YearMonth.of(2026,3));
        assertEquals(3,store.transactions().size());assertEquals("2026-02-28",store.month(YearMonth.of(2026,2)).get(0).date);
        Store.Tx t=store.month(YearMonth.of(2026,2)).get(0);store.remove(t);store.ensureMonth(YearMonth.of(2026,3));assertEquals(2,store.transactions().size());assertEquals(0,store.total(YearMonth.of(2026,3),"expense"));
    }
    @Test public void installmentSchedulePreservesAgorotAndReturnsToChargeDay(){
        Store.Account a=account("credit");Store.Tx t=expense(a,"2026-01-10",10000);t.count=3;store.save(t);
        List<Store.Charge> feb=store.charges(YearMonth.of(2026,2)),mar=store.charges(YearMonth.of(2026,3)),apr=store.charges(YearMonth.of(2026,4));
        assertEquals(LocalDate.of(2026,2,28),feb.get(0).date);assertEquals(LocalDate.of(2026,3,31),mar.get(0).date);assertEquals(10000,feb.get(0).amount+mar.get(0).amount+apr.get(0).amount);
        assertEquals(10000,store.total(YearMonth.of(2026,1),"expense"));assertEquals(0,store.total(YearMonth.of(2026,2),"expense"));
    }
    @Test public void priorCreditUsageIsNotExpenseAndNewInstallmentsReleaseTheirFullReservation() throws Exception {
        Store.Account a=account("credit");a.opening=50000;a.limit=200000;a.start="2026-01-01";a.closing=31;a.charge=10;store.save(a);
        Store.Tx t=expense(a,"2026-01-15",30000);t.count=3;store.save(t);
        Store.Tx expected=expense(a,"2026-01-20",9000);expected.status=0;store.save(expected);
        assertEquals(80000,store.creditUsage(a,java.time.LocalDate.parse("2026-01-31")));
        assertEquals(70000,store.creditUsage(a,java.time.LocalDate.parse("2026-02-10")));
        assertEquals(50000,store.creditUsage(a,java.time.LocalDate.parse("2026-04-10")));
        assertEquals(30000,store.total(YearMonth.of(2026,1),"expense"));
        JSONObject backup=store.backup();store.restore(backup);assertEquals(50000,store.account(a.id).opening);
    }
    @Test public void creditFrameReleasePreservesDay31AfterShortFebruary(){
        Store.Account a=account("credit");a.closing=31;a.charge=31;a.start="2026-01-01";store.save(a);Store.Tx t=expense(a,"2026-01-15",30000);t.count=3;store.save(t);
        assertEquals(20000,store.creditUsage(a,java.time.LocalDate.parse("2026-03-28")));
        assertEquals(10000,store.creditUsage(a,java.time.LocalDate.parse("2026-03-31")));
    }
    @Test public void fixedCreditUsageIncludesOpeningAndOnlyCompletedPurchases(){
        Store.Account a=account("fixed");a.opening=50000;a.payment=10000;a.charge=10;a.start="2026-01-01";store.save(a);
        Store.Tx t=expense(a,"2026-01-15",30000);t.count=3;store.save(t);Store.Tx expected=expense(a,"2026-01-20",9000);expected.status=0;store.save(expected);
        assertEquals(50000,store.creditUsage(a,java.time.LocalDate.parse("2026-01-09")));
        assertEquals(70000,store.creditUsage(a,java.time.LocalDate.parse("2026-01-31")));
        assertEquals(60000,store.creditUsage(a,java.time.LocalDate.parse("2026-02-10")));
        assertEquals(0,store.creditUsage(a,java.time.LocalDate.parse("2027-01-10")));
    }
    @Test public void editRecurringRebuildsPendingButPreservesActualAndSkippedMonths(){
        Store.Account a=account("bank"),b=account("debit");Store.Rule r=rule(a);store.ensureMonth(YearMonth.of(2026,4));
        Store.Tx january=store.month(YearMonth.of(2026,1)).get(0);january.status=1;store.save(january);store.remove(store.month(YearMonth.of(2026,3)).get(0));
        r.name="שם מעודכן";r.amount=15000;r.category="בית";r.account=b.id;r.start="2026-01-10";r.end="2026-03-20";store.updateRule(r,YearMonth.of(2026,4));store.ensureMonth(YearMonth.of(2026,4));
        assertEquals(1,store.month(YearMonth.of(2026,1)).size());Store.Tx kept=store.month(YearMonth.of(2026,1)).get(0);assertEquals(january.id,kept.id);assertEquals(9900,kept.amount);assertEquals(a.id,kept.account);assertEquals("2026-01-31",kept.date);
        assertEquals(1,store.month(YearMonth.of(2026,2)).size());Store.Tx pending=store.month(YearMonth.of(2026,2)).get(0);assertEquals(15000,pending.amount);assertEquals(b.id,pending.account);assertEquals("2026-02-10",pending.date);assertEquals("שם מעודכן",pending.merchant);
        assertTrue(store.month(YearMonth.of(2026,3)).isEmpty());assertTrue(store.month(YearMonth.of(2026,4)).isEmpty());
    }
    @Test public void invalidRecurringEditRollsBackRuleAndPendingCharges(){
        Store.Rule r=rule(account("bank"));store.ensureMonth(YearMonth.of(2026,2));r.amount=-1;
        try{store.updateRule(r,YearMonth.of(2026,2));fail("Invalid edit saved");}catch(IllegalArgumentException expected){}
        assertEquals(9900,store.rules().get(0).amount);assertEquals(9900,store.month(YearMonth.of(2026,2)).get(0).amount);
    }
    @Test public void homeOverviewIncludesRecurringOnceAndMatchesItsCategoryChart(){
        Store.Account bank=account("bank");Store.Rule rule=rule(bank);store.ensureMonth(YearMonth.of(2026,2));
        Store.Tx actual=expense(bank,"2026-02-12",10000);store.save(actual);Store.Tx expected=expense(bank,"2026-02-14",2000);expected.status=0;store.save(expected);Store.Tx income=expense(bank,"2026-02-15",5000);income.kind="income";store.save(income);
        Store.Overview overview=store.overview(YearMonth.of(2026,2));assertEquals(12000,overview.occasional);assertEquals(9900,overview.recurring);assertEquals(21900,overview.total());assertEquals(11900,overview.planned);assertEquals(overview.total(),overview.categories.values().stream().mapToLong(Long::longValue).sum());
        Store.Tx recurring=null;for(Store.Tx t:store.month(YearMonth.of(2026,2)))if(t.rule==rule.id)recurring=t;assertNotNull(recurring);recurring.status=1;recurring.amount=11000;store.save(recurring);store.ensureMonth(YearMonth.of(2026,2));overview=store.overview(YearMonth.of(2026,2));assertEquals(23000,overview.total());assertEquals(11000,overview.recurring);assertEquals(2000,overview.planned);
        store.remove(recurring);overview=store.overview(YearMonth.of(2026,2));assertEquals(12000,overview.total());assertEquals(0,overview.recurring);
    }
    @Test public void paymentBreakdownIncludesEveryAccountAndChartChoiceSurvivesBackup() throws Exception {
        Store.Account bank=account("bank"),card=account("credit");bank.name="בנק";card.name="כרטיס";store.save(bank);store.save(card);Store.Rule r=rule(bank);store.ensureMonth(YearMonth.of(2026,2));store.save(expense(card,"2026-02-15",12000));
        Store.Overview result=store.overview(YearMonth.of(2026,2));assertEquals(Long.valueOf(9900),result.payments.get("בנק"));assertEquals(Long.valueOf(12000),result.payments.get("כרטיס"));assertEquals(result.total(),result.payments.values().stream().mapToLong(Long::longValue).sum());
        context.getSharedPreferences("prefs",0).edit().putInt("homeChart",2).commit();JSONObject backup=store.backup();context.getSharedPreferences("prefs",0).edit().putInt("homeChart",0).commit();store.restore(backup);assertEquals(2,context.getSharedPreferences("prefs",0).getInt("homeChart",0));backup.getJSONObject("settings").remove("homeChart");store.restore(backup);assertEquals(0,context.getSharedPreferences("prefs",0).getInt("homeChart",-1));
    }
    @Test public void cumulativeComparesSameDaysAndExcludesPendingIncomeAndFutureDates(){
        Store.Account a=account("bank");for(String[] data:new String[][]{{"2026-03-02","1000"},{"2026-03-09","2000"},{"2026-03-20","9000"},{"2026-02-02","500"},{"2026-02-09","1500"},{"2026-02-12","8000"}})store.save(expense(a,data[0],Long.parseLong(data[1])));
        Store.Tx pending=expense(a,"2026-03-05",9999);pending.status=0;store.save(pending);Store.Tx income=expense(a,"2026-03-04",6000);income.kind="income";store.save(income);
        Store.SeriesData data=store.cumulative(YearMonth.of(2026,3),java.time.LocalDate.parse("2026-03-10"));assertEquals(10,data.labels.size());assertEquals(3000,data.series.get("החודש הנבחר")[9]);assertEquals(2000,data.series.get("החודש הקודם")[9]);assertTrue(store.cumulative(YearMonth.of(2026,4),java.time.LocalDate.parse("2026-03-10")).labels.isEmpty());
    }
    @Test public void cumulativeHandlesShortMonthsAndLeapDay(){
        Store.Account a=account("bank");store.save(expense(a,"2026-02-28",700));Store.SeriesData march=store.cumulative(YearMonth.of(2026,3),java.time.LocalDate.parse("2026-04-10"));assertEquals(31,march.labels.size());assertEquals(700,march.series.get("החודש הקודם")[30]);
        store.save(expense(a,"2028-02-29",900));store.save(expense(a,"2028-01-30",3000));Store.SeriesData feb=store.cumulative(YearMonth.of(2028,2),java.time.LocalDate.parse("2028-03-01"));assertEquals(29,feb.labels.size());assertEquals(900,feb.series.get("החודש הנבחר")[28]);assertEquals(0,feb.series.get("החודש הקודם")[28]);
    }
    @Test public void familyBreakdownIncludesUnassignedAndPendingButNotIncome(){
        Store.Account a=account("bank");Store.Tx t=expense(a,"2026-02-01",1200);t.tag="נוגה";store.save(t);Store.Tx pending=expense(a,"2026-02-02",2300);pending.tag="תבל";pending.status=0;store.save(pending);store.save(expense(a,"2026-02-03",500));Store.Tx income=expense(a,"2026-02-04",10000);income.kind="income";income.tag="נוגה";store.save(income);
        Store.Overview totals=store.overview(YearMonth.of(2026,2));assertEquals(Long.valueOf(1200),totals.families.get("נוגה"));assertEquals(Long.valueOf(2300),totals.families.get("תבל"));assertEquals(Long.valueOf(500),totals.families.get("ללא שיוך"));assertEquals(totals.total(),totals.families.values().stream().mapToLong(Long::longValue).sum());
    }
    @Test public void incomeChartAndAdvancedChartPreferencesSurviveBackup() throws Exception {
        Store.Account a=account("bank");store.save(expense(a,"2026-02-01",3000));Store.Tx income=expense(a,"2026-02-02",10000);income.kind="income";store.save(income);Store.Tx pending=expense(a,"2026-02-03",9000);pending.status=0;store.save(pending);Store.SeriesData data=store.incomeVsExpenses(YearMonth.of(2026,2));assertEquals(6,data.labels.size());assertEquals(10000,data.series.get("הכנסות")[5]);assertEquals(3000,data.series.get("הוצאות")[5]);
        context.getSharedPreferences("prefs",0).edit().putInt("homeChart",9).putString("trendCategory","AI").putLong("chartAccount",a.id).commit();JSONObject backup=store.backup();context.getSharedPreferences("prefs",0).edit().clear().commit();store.restore(backup);assertEquals(9,context.getSharedPreferences("prefs",0).getInt("homeChart",-1));assertEquals("AI",context.getSharedPreferences("prefs",0).getString("trendCategory",""));assertEquals(a.id,context.getSharedPreferences("prefs",0).getLong("chartAccount",0));
    }
    @Test public void invalidRestoreRollsBackExistingData() throws Exception{
        Store.Account a=account("bank");store.save(expense(a,"2026-01-10",1200));JSONObject backup=store.backup();backup.getJSONArray("transactions").getJSONObject(0).put("amount",-1);
        try{store.restore(backup);fail("Invalid restore succeeded");}catch(Exception expected){}
        assertEquals(1,store.transactions().size());assertEquals(1200,store.total(YearMonth.of(2026,1),"expense"));
    }
    @Test public void backupPreservesSkippedRecurringOccurrence() throws Exception{
        Store.Account a=account("bank");rule(a);store.ensureMonth(YearMonth.of(2026,2));store.remove(store.month(YearMonth.of(2026,2)).get(0));JSONObject backup=store.backup();store.restore(backup);store.ensureMonth(YearMonth.of(2026,2));assertEquals(1,store.transactions().size());
    }
    @Test public void fixedPaymentCannotExceedDebtAndDoesNotDoubleCountExpenses(){
        Store.Account a=account("fixed");a.opening=10000;a.charge=10;store.save(a);store.save(expense(a,"2026-01-10",5000));
        assertEquals(15000,store.charges(YearMonth.of(2026,1)).get(0).amount);assertTrue(store.charges(YearMonth.of(2026,2)).isEmpty());assertEquals(5000,store.total(YearMonth.of(2026,1),"expense"));
    }
    @Test public void activityTabsAndExpenseDialogSaveRealData(){
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity activity=controller.get();for(int index:new int[]{1,3,4,0})navigate(activity,index);
            find(activity.getWindow().getDecorView(),"＋  הוספת תנועה").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);
            ((EditText)dialog.getWindow().getDecorView().findViewWithTag("סכום")).setText("12.34");((EditText)dialog.getWindow().getDecorView().findViewWithTag("קטגוריה")).setText("מצרכים");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(1234,store.total(YearMonth.now(),"expense"));
        }
    }
    @Test public void foreignCurrencyPersistsFrozenRateAndUnicodeHistory() throws Exception{
        Store.Tx t=expense(store.accounts().get(0),"2026-10-30",3114);t.currency="USD";t.originalAmount=1000;t.fxRate="3.114";t.fxDate="2026-10-29";t.fxSource="Bank of Israel";t.merchant="חנות בעברית";t.tag="נוגה";store.save(t);
        JSONObject backup=store.backup();store.restore(backup);Store.Tx saved=store.transactions().get(0);
        assertEquals(3114,saved.amount);assertEquals(1000,saved.originalAmount);assertEquals("3.114",saved.fxRate);assertEquals("2026-10-29",saved.fxDate);
        assertTrue(store.suggestions(false).contains("חנות בעברית"));assertTrue(store.suggestions(true).contains("נוגה"));
    }
    @Test public void oldBackupUpgradesWithoutChangingAmounts() throws Exception{
        store.save(expense(store.accounts().get(0),"2026-01-01",1234));JSONObject backup=store.backup();backup.put("version",1);JSONObject tx=backup.getJSONArray("transactions").getJSONObject(0);
        for(String key:new String[]{"currency","original_amount","fx_rate","fx_date","fx_source","first_due"})tx.remove(key);
        store.restore(backup);assertEquals(1234,store.transactions().get(0).originalAmount);assertEquals("ILS",store.transactions().get(0).currency);
    }
    @Test public void chequeDatesAndFixedInstallmentsFollowMonthlyAmounts(){
        Store.Account cheque=account("check");Store.Tx t=expense(cheque,"2026-01-10",10000);t.count=3;t.firstDue="2026-02-28";store.save(t);
        assertTrue(store.charges(YearMonth.of(2026,1)).isEmpty());assertEquals(3334,store.charges(YearMonth.of(2026,2)).get(0).amount);assertEquals(LocalDate.of(2026,3,28),store.charges(YearMonth.of(2026,3)).get(0).date);
        Store.Account fixed=account("fixed");fixed.charge=15;store.save(fixed);Store.Tx split=expense(fixed,"2026-01-10",90000);split.count=3;store.save(split);
        assertEquals(30000,store.charges(YearMonth.of(2026,1)).get(0).amount);assertEquals(30000,store.charges(YearMonth.of(2026,2)).stream().filter(c->c.account==fixed.id).findFirst().get().amount);
    }
    @Test public void inputsAcceptHebrewAndInstallmentsHideForCash(){
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity activity=controller.get();navigate(activity,4);activity.getWindow().getDecorView().findViewWithTag("add-movement").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();EditText name=dialog.getWindow().getDecorView().findViewWithTag("שם החשבון או הכרטיס");assertEquals(android.text.InputType.TYPE_CLASS_TEXT,name.getInputType()&android.text.InputType.TYPE_MASK_CLASS);
            name.onCreateInputConnection(new android.view.inputmethod.EditorInfo()).commitText("כרטיס משפחתי",1);assertEquals("כרטיס משפחתי",name.getText().toString());dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertTrue(store.accounts().stream().anyMatch(a->a.name.equals("כרטיס משפחתי")));
            navigate(activity,0);activity.getWindow().getDecorView().findViewWithTag("add-movement").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();dialog=ShadowAlertDialog.getLatestAlertDialog();dialog.getWindow().getDecorView().findViewWithTag("extra-details").performClick();
            EditText count=dialog.getWindow().getDecorView().findViewWithTag("מספר תשלומים");assertFalse(count.isShown());
            assertTrue(dialog.getWindow().getDecorView().findViewWithTag("בית עסק") instanceof android.widget.AutoCompleteTextView);assertTrue(dialog.getWindow().getDecorView().findViewWithTag("בן משפחה") instanceof android.widget.AutoCompleteTextView);
        }
    }
    @Test public void officialRateParserUsesUnitsAndRejectsMissingCurrencies() throws Exception{
        String payload="{\"exchangeRates\":[{\"key\":\"USD\",\"currentExchangeRate\":3.114,\"unit\":1,\"lastUpdate\":\"2026-10-29T12:00:00Z\"},{\"key\":\"EUR\",\"currentExchangeRate\":3.4718,\"unit\":1,\"lastUpdate\":\"2026-10-29T12:00:00Z\"}]}";
        Map<String,ExchangeRates.Rate> parsed=ExchangeRates.parse(payload);assertEquals("3.114",parsed.get("USD").value);assertEquals("2026-10-29",parsed.get("EUR").date);
        try{ExchangeRates.parse("{\"exchangeRates\":[]}");fail();}catch(Exception expected){}
    }
    @Test public void databaseVersionOneMigratesWithoutLosingHebrewOrAmounts() throws Exception{
        store.close();context.deleteDatabase("pocketflow.db");
        android.database.sqlite.SQLiteDatabase legacy=context.openOrCreateDatabase("pocketflow.db",0,null);
        try(java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(getClass().getResourceAsStream("/v1-schema.sql"),java.nio.charset.StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null)if(!line.trim().isEmpty())legacy.execSQL(line);}
        legacy.execSQL("INSERT INTO accounts VALUES(1,'מזומן','cash',31,10,0,0,0,'2026-01-01')");
        legacy.execSQL("INSERT INTO transactions VALUES(1,'2026-01-10',1234,'בית','חנות','תיאור','נוגה',1,'expense',1,1,NULL)");legacy.setVersion(1);legacy.close();
        store=new Store(context);Store.Tx tx=store.transactions().get(0);assertEquals(1234,tx.amount);assertEquals(1234,tx.originalAmount);assertEquals("ILS",tx.currency);assertEquals("נוגה",tx.tag);assertEquals(9,store.getReadableDatabase().getVersion());
    }
    static void navigate(MainActivity activity,int index){
        View decor = activity.getWindow().getDecorView();
        View menu = decor.findViewWithTag("menu-toggle");
        if (menu == null) {
            if (index == 0) return;
            View shortcut = decor.findViewWithTag("launcher-app-" + index);
            assertNotNull(shortcut);
            assertTrue(shortcut.performClick());
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
            return;
        }
        menu.performClick();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        android.app.Dialog drawer = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        View item = drawer.getWindow().getDecorView().findViewWithTag("nav-" + index);
        assertNotNull(item);
        item.performClick();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
    }
    static View find(View root,String text){if(root instanceof TextView&&((TextView)root).getText().toString().equals(text))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=find(((ViewGroup)root).getChildAt(i),text);if(found!=null)return found;}return null;}
    static void collect(View root,List<EditText> out){if(root instanceof EditText)out.add((EditText)root);if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)collect(((ViewGroup)root).getChildAt(i),out);}
    @Test public void bankTransferUsesTransactionDateAndSurvivesRecurringAndRestore()throws Exception{
        Store.Account transfer=store.accounts().stream().filter(a->a.type.equals("transfer")).findFirst().get();Store.Tx tx=expense(transfer,"2026-01-15",12500);store.save(tx);
        assertEquals(LocalDate.of(2026,1,15),Finance.due(LocalDate.of(2026,1,15),"transfer",31,31));assertFalse(Finance.supportsInstallments("transfer"));
        Store.Rule recurring=rule(transfer);store.ensureMonth(YearMonth.of(2026,1));assertEquals(2,store.charges(YearMonth.of(2026,1)).size());assertEquals(LocalDate.of(2026,1,15),store.charges(YearMonth.of(2026,1)).get(0).date);
        org.json.JSONObject backup=store.backup();store.restore(backup);assertEquals("transfer",store.account(transfer.id).type);assertEquals(2,store.transactions().size());assertEquals(1,store.rules().size());assertEquals(22400,store.overview(YearMonth.of(2026,1)).payments.get("העברה בנקאית").longValue());
    }

    @Test public void bankTransferAppearsForExistingDatabaseAndOldBackupsWithoutDuplicates()throws Exception{
        long cashId=store.accounts().get(0).id;store.save(expense(store.account(cashId),"2026-01-10",1000));
        org.json.JSONObject backup=store.backup();org.json.JSONArray accounts=backup.getJSONArray("accounts");for(int i=accounts.length()-1;i>=0;i--)if(accounts.getJSONObject(i).getString("type").equals("transfer"))accounts.remove(i);
        store.restore(backup);assertEquals(2,store.accounts().size());assertEquals(cashId,store.transactions().get(0).account);
        store.getWritableDatabase().delete("accounts","type=?",new String[]{"transfer"});store.close();store=new Store(context);assertEquals(2,store.accounts().size());long transferId=store.accounts().stream().filter(a->a.type.equals("transfer")).findFirst().get().id;
        store.close();store=new Store(context);assertEquals(2,store.accounts().size());assertEquals(transferId,store.accounts().stream().filter(a->a.type.equals("transfer")).findFirst().get().id);assertEquals(1000,store.transactions().get(0).amount);
    }

}
