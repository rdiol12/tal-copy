package com.tal.pocketflow;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.*;
import java.io.*;
import java.time.YearMonth;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="he-rIL-w411dp-h891dp-xxhdpi")
public class DesignTest {
    Context context;
    @Before public void before(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("pocketflow.db");context.getSharedPreferences("prefs",0).edit().clear().commit();}
    @Test public void newMovementUsesTodayEvenWhenDisplayedMonthIsStale() throws Exception {
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();
            java.lang.reflect.Field monthField=MainActivity.class.getDeclaredField("month");monthField.setAccessible(true);monthField.set(a,YearMonth.now().minusMonths(1));
            a.getWindow().getDecorView().findViewWithTag("add-movement").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();View fields=dialog.getWindow().getDecorView();
            assertEquals(java.time.LocalDate.now().toString(),((TextView)fields.findViewWithTag("transaction-date")).getText().toString());
            ((EditText)fields.findViewWithTag("סכום")).setText("25");((EditText)fields.findViewWithTag("קטגוריה")).setText("בית");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertFalse(dialog.isShowing());
            try(Store db=new Store(context)){assertEquals(java.time.LocalDate.now().toString(),db.transactions().get(0).date);}
        }
    }
    @Test public void editingMovementPreservesHistoricalDate() throws Exception {
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            Store.Tx tx=new Store.Tx();
            try(Store db=new Store(context)){tx.account=db.accounts().get(0).id;tx.amount=2500;tx.date="2026-10-01";tx.category="בית";tx.merchant="";tx.note="";tx.tag="";tx.kind="expense";tx.count=1;tx.status=1;db.save(tx);tx=db.transactions().get(0);}
            java.lang.reflect.Method edit=MainActivity.class.getDeclaredMethod("transactionDialog",Store.Tx.class);edit.setAccessible(true);edit.invoke(controller.get(),tx);
            AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertEquals("2026-10-01",((TextView)dialog.getWindow().getDecorView().findViewWithTag("transaction-date")).getText().toString());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            try(Store db=new Store(context)){assertEquals("2026-10-01",db.transactions().get(0).date);}
        }
    }
    @Test public void legacyFixedRepairIsExplicitAndKeepsOpeningAmount()throws Exception{
        long id;try(Store db=new Store(context)){Store.Account card=new Store.Account();card.name="כרטיס לתיקון";card.type="fixed";card.start="2026-10-01";card.closing=31;card.charge=10;card.opening=4364680;card.payment=500000;db.save(card);id=card.id;}
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();StoreTest.navigate(a,4);View repair=a.getWindow().getDecorView().findViewWithTag("repair-fixed-"+id);assertNotNull(repair);repair.performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
            ((TextView)dialog.getWindow().getDecorView().findViewWithTag("fixed-change-date")).setText("2026-11-01");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertFalse(dialog.isShowing());assertNull(a.getWindow().getDecorView().findViewWithTag("repair-fixed-"+id));
            try(Store db=new Store(context)){assertEquals(4364680,db.account(id).opening);assertEquals(4364680,db.creditUsage(db.account(id),java.time.LocalDate.parse("2026-11-01")));}
        }
    }
    @Test public void usedCreditCardCanSwitchBillingModeWithoutLosingTransactions() throws Exception {
        long id;
        try(Store db=new Store(context)){Store.Account a=new Store.Account();a.name="כרטיס בדיקה";a.type="credit";a.start="2026-01-01";a.closing=31;a.charge=10;db.save(a);id=a.id;Store.Tx t=new Store.Tx();t.account=id;t.amount=30000;t.date="2026-01-15";t.category="בית";t.merchant="עסק";t.note="";t.tag="";t.kind="expense";t.count=3;t.status=1;db.save(t);}
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();StoreTest.navigate(a,4);a.getWindow().getDecorView().findViewWithTag("edit-account-"+id).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();View form=dialog.getWindow().getDecorView();
            assertFalse(((Spinner)form.findViewWithTag("סוג אמצעי התשלום")).isEnabled());Spinner billing=form.findViewWithTag("אופן חיוב האשראי");assertTrue(billing.isEnabled());assertEquals(0,billing.getSelectedItemPosition());
            billing.setSelection(1);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();((EditText)form.findViewWithTag("סכום החיוב החודשי הקבוע")).setText("100");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertFalse(dialog.isShowing());
            try(Store db=new Store(context)){assertEquals("fixed",db.account(id).type);assertEquals(10000,db.account(id).payment);assertEquals(1,db.transactions().size());}
            a.getWindow().getDecorView().findViewWithTag("edit-account-"+id).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();dialog=ShadowAlertDialog.getLatestAlertDialog();form=dialog.getWindow().getDecorView();billing=form.findViewWithTag("אופן חיוב האשראי");assertEquals(1,billing.getSelectedItemPosition());billing.setSelection(0);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            ((EditText)form.findViewWithTag("ניצול קודם שאינו מתועד")).setText("500");((EditText)form.findViewWithTag("פירעון חודשי של הניצול הקודם")).setText("200");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertFalse(dialog.isShowing());
            try(Store db=new Store(context)){assertEquals("credit",db.account(id).type);assertEquals(20000,db.account(id).openingPayment);assertEquals(1,db.transactions().size());assertEquals(3,db.transactions().get(0).count);}
        }
    }
    @Test public void drawerContainsAllPagesAndExactFooter(){
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();StoreTest.navigate(a,11);a.getWindow().getDecorView().findViewWithTag("menu-toggle").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Dialog drawer=ShadowDialog.getLatestDialog();
            assertEquals("Powered by Azulay",((TextView)drawer.getWindow().getDecorView().findViewWithTag("drawer-credit")).getText().toString());
            for(int i:new int[]{0,1,2,3,4,5,6,11})assertNotNull(drawer.getWindow().getDecorView().findViewWithTag("nav-"+i));drawer.dismiss();
            StoreTest.navigate(a,5);assertNotNull(a.getWindow().getDecorView().findViewWithTag("appearance-DARK"));StoreTest.navigate(a,6);assertNotNull(StoreTest.find(a.getWindow().getDecorView(),"שמירת גיבוי מלא"));
        }
    }
    @Test public void monthGridSelectsLeapFebruaryAndValidatesYear(){
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();StoreTest.navigate(a,11);a.getWindow().getDecorView().findViewWithTag("period-picker").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();
            EditText year=d.getWindow().getDecorView().findViewWithTag("period-year");year.setText("1899");assertFalse(d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());year.setText("2028");d.getWindow().getDecorView().findViewWithTag("period-month-2").performClick();
            assertTrue(d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertNotNull(StoreTest.find(a.getWindow().getDecorView(),"01/02/2028 – 29/02/2028"));
            a.getWindow().getDecorView().findViewWithTag("period-current").performClick();assertNotNull(StoreTest.find(a.getWindow().getDecorView(),YearMonth.now().atDay(1).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))+" – "+YearMonth.now().atEndOfMonth().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        }
    }
    @Test public void darkPaletteAndBackupRetainAppearance() throws Exception {
        context.getSharedPreferences("prefs",0).edit().putString("appearance","DARK").commit();assertEquals(0xff101720,AppTheme.palette(context).background);assertEquals(0xffADC7FF,AppTheme.palette(context).primary);
        try(Store db=new Store(context)){org.json.JSONObject backup=db.backup();context.getSharedPreferences("prefs",0).edit().putString("appearance","LIGHT").commit();db.restore(backup);assertTrue(AppTheme.dark(context));}
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){StoreTest.navigate(controller.get(),3);assertNotNull(controller.get().getWindow().getDecorView().findViewWithTag("period-picker"));}
    }
    @Test public void categoryAllowsFreeTextAndArrowShowsAllSavedOptions(){
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();a.getWindow().getDecorView().findViewWithTag("add-movement").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog entry=ShadowAlertDialog.getLatestAlertDialog();AutoCompleteTextView input=entry.getWindow().getDecorView().findViewWithTag("קטגוריה");
            input.setText("הוצאות גינה");Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            entry.getWindow().getDecorView().findViewWithTag("category-dropdown").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals("הוצאות גינה",input.getText().toString());assertTrue(input.isPopupShowing());assertTrue(input.getAdapter().getCount()>=13);input.dismissDropDown();
            ((EditText)entry.getWindow().getDecorView().findViewWithTag("סכום")).setText("25");entry.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertFalse(entry.isShowing());
            a.getWindow().getDecorView().findViewWithTag("add-movement").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();entry=ShadowAlertDialog.getLatestAlertDialog();input=entry.getWindow().getDecorView().findViewWithTag("קטגוריה");
            boolean found=false;for(int n=0;n<input.getAdapter().getCount();n++)if("הוצאות גינה".equals(input.getAdapter().getItem(n)))found=true;assertTrue(found);
            input.setText("מצרכים",false);assertEquals("מצרכים",input.getText().toString());entry.dismiss();
        }
    }
    @Test public void dropdownsSortHebrewAndKeepCurrencyAndAccountMapping() throws Exception {
        long bankId;
        try(Store db=new Store(context)){
            Store.Account bank=new Store.Account();bank.name="אמצעי בדיקה";bank.type="bank";bank.start="2026-01-01";db.save(bank);bankId=bank.id;
        }
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();a.getWindow().getDecorView().findViewWithTag("add-movement").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();AlertDialog entry=ShadowAlertDialog.getLatestAlertDialog();View form=entry.getWindow().getDecorView();
            Spinner currency=form.findViewWithTag("מטבע");assertEquals("שקל · ILS ₪",currency.getSelectedItem());assertOrdered(currency.getAdapter());
            Spinner account=form.findViewWithTag("אמצעי תשלום");assertOrdered(account.getAdapter());account.setSelection(0);assertEquals("אמצעי בדיקה",account.getSelectedItem());
            AutoCompleteTextView category=form.findViewWithTag("קטגוריה");assertOrdered(category.getAdapter());category.setText("בית",false);((EditText)form.findViewWithTag("סכום")).setText("15");entry.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            try(Store db=new Store(context)){assertEquals(bankId,db.transactions().get(0).account);assertEquals("ILS",db.transactions().get(0).currency);assertEquals(1500,db.transactions().get(0).amount);}
            java.lang.reflect.Method open=MainActivity.class.getDeclaredMethod("accountDialog");open.setAccessible(true);open.invoke(a);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();AlertDialog add=ShadowAlertDialog.getLatestAlertDialog();View fields=add.getWindow().getDecorView();Spinner type=fields.findViewWithTag("סוג אמצעי התשלום");assertOrdered(type.getAdapter());
            for(int n=0;n<type.getCount();n++)if("אשראי בחיוב קבוע".equals(type.getItemAtPosition(n)))type.setSelection(n);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            ((EditText)fields.findViewWithTag("שם החשבון או הכרטיס")).setText("כרטיס בדיקה");((EditText)fields.findViewWithTag("סכום החיוב החודשי הקבוע")).setText("100");add.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertFalse(add.isShowing());
            try(Store db=new Store(context)){boolean found=false;for(Store.Account item:db.accounts())if(item.name.equals("כרטיס בדיקה")){assertEquals("fixed",item.type);assertEquals(10000,item.payment);found=true;}assertTrue(found);}
        }
    }
    private void assertOrdered(Adapter adapter){
        java.text.Collator order=java.text.Collator.getInstance(java.util.Locale.forLanguageTag("he-IL"));for(int n=1;n<adapter.getCount();n++)assertTrue(order.compare(adapter.getItem(n-1).toString(),adapter.getItem(n).toString())<=0);
    }
    @Test public void editingExistingCreditUpdatesSameAccountAndRetainsTransactions() throws Exception {
        long id;
        try(Store db=new Store(context)){Store.Account a=new Store.Account();a.name="כרטיס קיים";a.type="credit";a.start="2026-01-01";a.closing=31;a.charge=10;a.limit=100000;db.save(a);id=a.id;Store.Tx t=new Store.Tx();t.account=id;t.amount=2000;t.date="2026-01-15";t.category="בית";t.merchant="";t.note="";t.tag="";t.kind="expense";t.count=1;t.status=1;db.save(t);}
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity activity=controller.get();StoreTest.navigate(activity,4);activity.getWindow().getDecorView().findViewWithTag("edit-account-"+id).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();AlertDialog edit=ShadowAlertDialog.getLatestAlertDialog();View fields=edit.getWindow().getDecorView();
            assertFalse(fields.findViewWithTag("סוג אמצעי התשלום").isEnabled());((EditText)fields.findViewWithTag("מסגרת הכרטיס · לבחירה")).setText("2500");((EditText)fields.findViewWithTag("ניצול קודם שאינו מתועד")).setText("500");((EditText)fields.findViewWithTag("יום החיוב בחודש")).setText("15");edit.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertFalse(edit.isShowing());
            try(Store db=new Store(context)){assertEquals(3,db.accounts().size());assertEquals(id,db.transactions().get(0).account);assertEquals(250000,db.account(id).limit);assertEquals(50000,db.account(id).opening);assertEquals(15,db.account(id).charge);}
        }
    }
    @Test public void merchantAndFamilyArrowsOpenSavedHistoryWhenEmptyOrFiltered(){
        try(Store db=new Store(context)){
            for(String[] names:new String[][]{{"עסק זית","תבל"},{"אלון חנות","נוגה"}}){Store.Tx t=new Store.Tx();t.account=db.accounts().get(0).id;t.amount=1000;t.date="2026-01-15";t.category="בית";t.merchant=names[0];t.note="";t.tag=names[1];t.kind="expense";t.count=1;t.status=1;db.save(t);}
        }
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();a.getWindow().getDecorView().findViewWithTag("add-movement").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();View form=dialog.getWindow().getDecorView();form.findViewWithTag("extra-details").performClick();
            for(String label:new String[]{"בית עסק","בן משפחה"}){
                AutoCompleteTextView input=form.findViewWithTag(label);assertEquals("",input.getText().toString());form.findViewWithTag("history-dropdown-"+label).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertTrue(input.isPopupShowing());assertEquals(2,input.getAdapter().getCount());assertOrdered(input.getAdapter());
                String first=input.getAdapter().getItem(0).toString();input.setListSelection(0);input.performCompletion();assertEquals(first,input.getText().toString());input.dismissDropDown();
                input.setText("שם חדש שאינו ברשימה");Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();form.findViewWithTag("history-dropdown-"+label).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertTrue(input.isPopupShowing());assertEquals(2,input.getAdapter().getCount());assertEquals("שם חדש שאינו ברשימה",input.getText().toString());input.dismissDropDown();
            }
            dialog.dismiss();
        }
    }
    @Test public void recurringSummaryCountsOnlyRulesDueInSelectedMonth(){
        try(Store db=new Store(context)){
            Store.Account bank=new Store.Account();bank.name="בנק";bank.type="bank";bank.start="2020-01-01";bank.closing=31;bank.charge=10;db.save(bank);
            for(int n=0;n<5;n++){Store.Rule r=new Store.Rule();r.name="הוראה "+n;r.amount=n==0?1200:n==1?3400:9900;r.account=bank.id;r.category="חשבונות";r.active=n==2?0:1;r.start=(n==3?YearMonth.now().plusMonths(1):YearMonth.now().minusMonths(1)).atDay(1).toString();r.end=n==4?YearMonth.now().minusMonths(1).atEndOfMonth().toString():"";db.save(r);}
        }
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity activity=controller.get();StoreTest.navigate(activity,3);View form=activity.getWindow().getDecorView();assertEquals("\u200E₪ 46.00\u200E",((TextView)form.findViewWithTag("recurring-total")).getText().toString());assertEquals("2 הוראות קבע פעילות בתקופה זו",((TextView)form.findViewWithTag("recurring-count")).getText().toString());
        }
    }
    @Test public void transactionAmountAddsExpressionAndSavesOneMovement(){
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();a.getWindow().getDecorView().findViewWithTag("add-movement").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();View fields=dialog.getWindow().getDecorView();
            ((EditText)fields.findViewWithTag("סכום")).setText("=67+22+33+55");assertEquals("סה״כ: 177.00",((TextView)fields.findViewWithTag("amount-calculation")).getText().toString());((EditText)fields.findViewWithTag("קטגוריה")).setText("בית");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertFalse(dialog.isShowing());
            try(Store db=new Store(context)){assertEquals(1,db.transactions().size());assertEquals(17700,db.transactions().get(0).amount);}
        }
    }
    @Test public void recurringEditPrefillsAndKeepsPausedRuleIdentity(){
        long id;
        try(Store db=new Store(context)){Store.Account bank=new Store.Account();bank.name="בנק";bank.type="bank";bank.start="2020-01-01";bank.closing=31;bank.charge=10;db.save(bank);Store.Rule rule=new Store.Rule();rule.name="מנוי קודם";rule.amount=9900;rule.category="חשבונות";rule.account=bank.id;rule.start=YearMonth.now().atDay(1).toString();rule.end="";rule.active=0;db.save(rule);id=rule.id;}
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();StoreTest.navigate(a,3);a.getWindow().getDecorView().findViewWithTag("edit-rule-"+id).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();View form=dialog.getWindow().getDecorView();
            EditText name=form.findViewWithTag("שם הוראת הקבע / בית העסק");assertEquals("מנוי קודם",name.getText().toString());assertEquals("99.00",((EditText)form.findViewWithTag("סכום")).getText().toString());name.setText("מנוי מעודכן");((EditText)form.findViewWithTag("סכום")).setText("120");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertFalse(dialog.isShowing());
            try(Store db=new Store(context)){assertEquals(1,db.rules().size());Store.Rule saved=db.rules().get(0);assertEquals(id,saved.id);assertEquals("מנוי מעודכן",saved.name);assertEquals(12000,saved.amount);assertEquals(0,saved.active);assertTrue(db.transactions().isEmpty());}
        }
    }
    @Test public void movementsTotalsRespectSearchAndSeparatePendingFromActual(){
        try(Store db=new Store(context)){
            for(int n=0;n<4;n++){Store.Tx t=new Store.Tx();t.account=db.accounts().get(0).id;t.amount=new long[]{10000,3000,2000,5000}[n];t.date=YearMonth.now().atDay(1).toString();t.category="בית";t.merchant=n==1?"אחר":"עסק לבדיקה";t.note="";t.tag="";t.kind=n==3?"income":"expense";t.count=1;t.status=n==2?0:1;db.save(t);}
        }
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();StoreTest.navigate(a,1);View form=a.getWindow().getDecorView();assertEquals("\u200E₪ 130.00\u200E",((TextView)form.findViewWithTag("movements-total")).getText().toString());
            ((EditText)form.findViewWithTag("movement-search")).setText("עסק לבדיקה");Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(250));assertEquals("\u200E₪ 100.00\u200E",((TextView)form.findViewWithTag("movements-total")).getText().toString());assertTrue(((TextView)form.findViewWithTag("movements-pending")).getText().toString().contains("20.00"));assertTrue(((TextView)form.findViewWithTag("movements-net")).getText().toString().contains("50.00"));
            StoreTest.find(form,"לוח חיובים").performClick();assertEquals("\u200E₪ 100.00\u200E",((TextView)a.getWindow().getDecorView().findViewWithTag("charges-total")).getText().toString());
        }
    }
    @Test public void homeShowsOccasionalRecurringAndCombinedAmount(){
        try(Store db=new Store(context)){Store.Account bank=new Store.Account();bank.name="בנק";bank.type="bank";bank.start="2020-01-01";bank.closing=31;bank.charge=10;db.save(bank);Store.Rule rule=new Store.Rule();rule.name="הוראת קבע";rule.amount=9900;rule.account=bank.id;rule.category="חשבונות";rule.start=YearMonth.now().atDay(1).toString();rule.end="";rule.active=1;db.save(rule);Store.Tx t=new Store.Tx();t.account=bank.id;t.amount=10000;t.date=YearMonth.now().atDay(1).toString();t.category="בית";t.merchant="חנות";t.note="";t.tag="";t.kind="expense";t.count=1;t.status=1;db.save(t);}
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            View home=controller.get().getWindow().getDecorView();assertTrue(((TextView)home.findViewWithTag("home-occasional")).getText().toString().contains("100.00"));assertTrue(((TextView)home.findViewWithTag("home-recurring")).getText().toString().contains("99.00"));assertEquals("\u200E₪ 199.00\u200E",((TextView)home.findViewWithTag("home-total")).getText().toString());
        }
    }
    @Test public void englishAndHebrewLegendLabelsShareRightAlignmentAndMarkerPosition(){
        try(Store db=new Store(context)){
            for(String category:new String[]{"AI","חשבונות"}){Store.Tx t=new Store.Tx();t.account=db.accounts().get(0).id;t.amount=1000;t.date=YearMonth.now().atDay(1).toString();t.category=category;t.merchant="";t.note="";t.tag="";t.kind="expense";t.count=1;t.status=1;db.save(t);}
        }
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            StoreTest.navigate(controller.get(),11);View decor=controller.get().getWindow().getDecorView();decor.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY));decor.layout(0,0,1080,1920);
            for(String category:new String[]{"AI","חשבונות"}){TextView label=decor.findViewWithTag("legend-label-"+category);View marker=decor.findViewWithTag("legend-marker-"+category);assertEquals(category,label.getText().toString());assertEquals(Gravity.RIGHT,label.getGravity()&Gravity.HORIZONTAL_GRAVITY_MASK);assertEquals(View.LAYOUT_DIRECTION_RTL,label.getLayoutDirection());assertTrue(marker.getLeft()>=label.getRight());}
        }
    }
    @Test public void backWalksPreviousPagesAndOnlyExitsAtHome(){
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();for(int tab:new int[]{3,4,5,6})StoreTest.navigate(a,tab);
            a.onBackPressed();assertNotNull(a.getWindow().getDecorView().findViewWithTag("entry-lock-setting"));assertFalse(a.isFinishing());
            a.onBackPressed();assertNotNull(StoreTest.find(a.getWindow().getDecorView(),"חשבונות וכרטיסים"));assertFalse(a.isFinishing());
            a.onBackPressed();assertNotNull(StoreTest.find(a.getWindow().getDecorView(),"הוראות קבע"));assertFalse(a.isFinishing());
            a.onBackPressed();assertNotNull(a.getWindow().getDecorView().findViewWithTag("home-total"));assertFalse(a.isFinishing());a.onBackPressed();assertFalse(a.isFinishing());assertEquals("לחץ חזור שוב ליציאה",ShadowToast.getTextOfLatestToast());a.onBackPressed();assertTrue(a.isFinishing());
        }
    }
    @Test public void backClosesDrawerAndFormBeforeExitingHome(){
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();StoreTest.navigate(a,11);a.getWindow().getDecorView().findViewWithTag("menu-toggle").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Dialog drawer=ShadowDialog.getLatestDialog();a.onBackPressed();assertFalse(drawer.isShowing());assertFalse(a.isFinishing());StoreTest.navigate(a,0);
            a.getWindow().getDecorView().findViewWithTag("add-movement").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();AlertDialog form=ShadowAlertDialog.getLatestAlertDialog();a.onBackPressed();assertFalse(form.isShowing());assertFalse(a.isFinishing());assertNotNull(a.getWindow().getDecorView().findViewWithTag("home-total"));
        }
    }
    @Test public void backRestoresSearchAfterRecreationAndSkipsRepeatedSamePage(){
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();StoreTest.navigate(a,1);((EditText)a.getWindow().getDecorView().findViewWithTag("movement-search")).setText("עסק קודם");Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(250));StoreTest.navigate(a,3);controller.recreate();a=controller.get();a.onBackPressed();assertEquals("עסק קודם",((EditText)a.getWindow().getDecorView().findViewWithTag("movement-search")).getText().toString());StoreTest.navigate(a,1);a.onBackPressed();assertNotNull(a.getWindow().getDecorView().findViewWithTag("home-total"));assertFalse(a.isFinishing());
        }
    }
    @Test @Config(sdk=33) public void modernBackCallbackUsesSameNavigation() throws Exception {
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();StoreTest.navigate(a,4);java.lang.reflect.Field field=MainActivity.class.getDeclaredField("backCallback");field.setAccessible(true);android.window.OnBackInvokedCallback callback=(android.window.OnBackInvokedCallback)field.get(a);assertNotNull(callback);callback.onBackInvoked();assertNotNull(a.getWindow().getDecorView().findViewWithTag("home-total"));assertFalse(a.isFinishing());callback.onBackInvoked();assertFalse(a.isFinishing());callback.onBackInvoked();assertTrue(a.isFinishing());
        }
    }
    @Test public void homeChartSelectorShowsOneGraphAndIncludesRecurringInEveryBreakdown(){
        try(Store db=new Store(context)){
            Store.Account bank=new Store.Account();bank.name="בנק";bank.type="bank";bank.start="2020-01-01";bank.closing=31;bank.charge=10;db.save(bank);Store.Rule rule=new Store.Rule();rule.name="עסק";rule.amount=9900;rule.account=bank.id;rule.category="חשבונות";rule.start=YearMonth.now().atDay(1).toString();rule.end="";rule.active=1;db.save(rule);
            for(String merchant:new String[]{"עסק","עסק נוסף"}){Store.Tx t=new Store.Tx();t.account=bank.id;t.amount=1000;t.date=YearMonth.now().atDay(1).toString();t.category="בית";t.merchant=merchant;t.note="";t.tag="";t.kind="expense";t.count=1;t.status=1;db.save(t);}
        }
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();StoreTest.navigate(a,11);View form=a.getWindow().getDecorView();Spinner picker=form.findViewWithTag("chart-selector");assertEquals(10,picker.getCount());
            for(int choice=0;choice<4;choice++){
                picker.setSelection(choice);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();ChartView chart=form.findViewWithTag("home-chart");assertNotNull(chart);assertEquals(choice!=3,chart.donut);LinearLayout content=form.findViewWithTag("chart-content");int charts=0;for(int n=0;n<content.getChildCount();n++)if(content.getChildAt(n) instanceof ChartView)charts++;assertEquals(1,charts);
                if(choice<3)assertEquals(11900,chart.values.values().stream().mapToLong(Long::longValue).sum());else assertEquals(Long.valueOf(11900),new java.util.ArrayList<>(chart.values.values()).get(5));
                if(choice==1)assertEquals(Long.valueOf(10900),chart.values.get("עסק"));if(choice==2)assertEquals(Long.valueOf(11900),chart.values.get("בנק"));
            }
            picker.setSelection(1);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();((ChartView)form.findViewWithTag("home-chart")).pick.select("עסק");form=a.getWindow().getDecorView();assertEquals("\u200E₪ 10.00\u200E",((TextView)form.findViewWithTag("movements-total")).getText().toString());assertNotNull(form.findViewWithTag("clear-merchant-filter"));form.findViewWithTag("clear-merchant-filter").performClick();assertEquals("\u200E₪ 20.00\u200E",((TextView)a.getWindow().getDecorView().findViewWithTag("movements-total")).getText().toString());
            a.onBackPressed();picker=a.getWindow().getDecorView().findViewWithTag("chart-selector");assertEquals(1,picker.getSelectedItemPosition());picker.setSelection(2);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();controller.recreate();a=controller.get();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(2,((Spinner)a.getWindow().getDecorView().findViewWithTag("chart-selector")).getSelectedItemPosition());((ChartView)a.getWindow().getDecorView().findViewWithTag("home-chart")).pick.select("בנק");assertNotNull(a.getWindow().getDecorView().findViewWithTag("movement-search"));
        }
    }
    @Test public void doubleBackExitExpiresAndResetsAfterNavigation(){
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();a.onBackPressed();assertFalse(a.isFinishing());ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(3));a.onBackPressed();assertFalse(a.isFinishing());StoreTest.navigate(a,3);a.onBackPressed();assertFalse(a.isFinishing());a.onBackPressed();assertFalse(a.isFinishing());assertEquals("לחץ חזור שוב ליציאה",ShadowToast.getTextOfLatestToast());a.onBackPressed();assertTrue(a.isFinishing());
        }
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void comparisonSelectorSeparatesMonthlyRecurringAndOccasionalAmounts() throws Exception {
        try(Store db=new Store(context)){Store.Account bank=new Store.Account();bank.name="בנק";bank.type="bank";bank.start="2020-01-01";bank.closing=31;bank.charge=10;db.save(bank);Store.Rule rule=new Store.Rule();rule.name="מנוי";rule.amount=9900;rule.category="חשבונות";rule.account=bank.id;rule.start=YearMonth.now().atDay(1).toString();rule.end="";rule.active=1;db.save(rule);Store.Tx t=new Store.Tx();t.account=bank.id;t.amount=2500;t.date=YearMonth.now().atDay(1).toString();t.category="בית";t.merchant="חנות";t.note="";t.tag="";t.kind="expense";t.count=1;t.status=1;db.save(t);}
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();StoreTest.navigate(a,11);((Spinner)a.getWindow().getDecorView().findViewWithTag("chart-selector")).setSelection(4);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();ChartView chart=a.getWindow().getDecorView().findViewWithTag("home-chart");assertTrue(chart.donut);assertEquals(2,chart.values.size());assertEquals(Long.valueOf(9900),chart.values.get("הוראות קבע"));assertEquals(Long.valueOf(2500),chart.values.get("חיובים מזדמנים"));
            View content=a.getWindow().getDecorView().findViewWithTag("chart-content");content.setBackgroundColor(Color.WHITE);save(content,"comparison-chart");
            a.getWindow().getDecorView().findViewWithTag("period-previous").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();chart=a.getWindow().getDecorView().findViewWithTag("home-chart");assertTrue(chart.donut);assertEquals(Long.valueOf(0),chart.values.get("הוראות קבע"));assertEquals(Long.valueOf(0),chart.values.get("חיובים מזדמנים"));
            try(Store db=new Store(context)){org.json.JSONObject backup=db.backup();assertEquals(4,backup.getJSONObject("settings").getInt("homeChart"));db.restore(backup);assertEquals(4,context.getSharedPreferences("prefs",0).getInt("homeChart",-1));}
        }
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void newChartsRenderAndUseTheirOwnAccountingRules() throws Exception {
        try(Store db=new Store(context)){
            Store.Account bank=new Store.Account();bank.name="בנק";bank.type="bank";bank.start="2020-01-01";bank.closing=31;bank.charge=10;db.save(bank);
            Store.Account card=new Store.Account();card.name="OneZero Direct";card.type="credit";card.start=YearMonth.now().atDay(1).toString();card.closing=31;card.charge=10;card.limit=100000;card.opening=50000;db.save(card);
            for(int n=0;n<3;n++){Store.Tx t=new Store.Tx();t.account=n==0?card.id:bank.id;t.amount=new long[]{2500,3500,8000}[n];t.date=YearMonth.now().atDay(1).toString();t.category=n==0?"AI":"בית";t.merchant=n==0?"Google One":"עסק";t.note="";t.tag=n==0?"נוגה":"";t.kind=n==2?"income":"expense";t.count=1;t.status=n==1?0:1;db.save(t);}
        }
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();StoreTest.navigate(a,11);Spinner picker=a.getWindow().getDecorView().findViewWithTag("chart-selector");assertEquals(10,picker.getCount());
            for(int choice=5;choice<10;choice++){
                picker.setSelection(choice);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();View decor=a.getWindow().getDecorView();View chart=decor.findViewWithTag("home-chart");assertNotNull(chart);
                if(choice==5){assertTrue(chart instanceof SeriesChartView);assertTrue(((SeriesChartView)chart).lines);long[] values=((SeriesChartView)chart).data.series.get("החודש הנבחר");assertEquals(2500,values[values.length-1]);}
                if(choice==6){Spinner category=decor.findViewWithTag("trend-category");for(int n=0;n<category.getCount();n++)if("AI".equals(category.getItemAtPosition(n)))category.setSelection(n);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();ChartView trend=decor.findViewWithTag("home-chart");assertEquals(Long.valueOf(2500),new java.util.ArrayList<>(trend.values.values()).get(5));}
                if(choice==7){assertEquals(Long.valueOf(2500),((ChartView)chart).values.get("נוגה"));assertEquals(Long.valueOf(3500),((ChartView)chart).values.get("ללא שיוך"));}
                if(choice==8){assertEquals(Long.valueOf(52500),((ChartView)chart).values.get("ניצול בתוך המסגרת"));assertEquals(Long.valueOf(47500),((ChartView)chart).values.get("מסגרת פנויה"));assertTrue(((ChartView)chart).centerLabel.contains("מסגרת"));}
                if(choice==9){Store.SeriesData data=((SeriesChartView)chart).data;assertEquals(8000,data.series.get("הכנסות")[5]);assertEquals(2500,data.series.get("הוצאות")[5]);assertFalse(context.getSharedPreferences("prefs",0).getBoolean("income",false));}
                View content=decor.findViewWithTag("chart-content");content.setBackgroundColor(Color.WHITE);save(content,"advanced-"+choice);
            }
        }
    }
    @Test public void englishMovementTextIsRightAlignedInHomeMovementsAndCharges(){
        try(Store db=new Store(context)){Store.Account account=new Store.Account();account.name="OneZero Direct";account.type="debit";account.start="2020-01-01";account.closing=31;account.charge=10;db.save(account);Store.Tx t=new Store.Tx();t.account=account.id;t.amount=2500;t.date=YearMonth.now().atDay(1).toString();t.category="AI";t.merchant="Google One";t.note="";t.tag="";t.kind="expense";t.count=1;t.status=1;db.save(t);}
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();StoreTest.navigate(a,11);TextView home=(TextView)StoreTest.find(a.getWindow().getDecorView(),"Google One");assertEquals(Gravity.RIGHT,home.getGravity()&Gravity.HORIZONTAL_GRAVITY_MASK);assertEquals(View.LAYOUT_DIRECTION_RTL,home.getLayoutDirection());
            StoreTest.navigate(a,1);TextView movement=(TextView)StoreTest.find(a.getWindow().getDecorView(),"Google One");assertEquals(Gravity.RIGHT,movement.getGravity()&Gravity.HORIZONTAL_GRAVITY_MASK);TextView account=(TextView)StoreTest.find(a.getWindow().getDecorView(),"OneZero Direct");assertEquals(Gravity.RIGHT,account.getGravity()&Gravity.HORIZONTAL_GRAVITY_MASK);
            StoreTest.find(a.getWindow().getDecorView(),"לוח חיובים").performClick();TextView charge=(TextView)StoreTest.find(a.getWindow().getDecorView(),"Google One");assertEquals(Gravity.RIGHT,charge.getGravity()&Gravity.HORIZONTAL_GRAVITY_MASK);
        }
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void renderLightAndDarkScreens() throws Exception {
        for(String appearance:new String[]{"LIGHT","DARK"}){
            context.getSharedPreferences("prefs",0).edit().putString("appearance",appearance).commit();
            try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
                MainActivity a=controller.get();save(a.getWindow().getDecorView(),"home-"+appearance);
                for(int screen:new int[]{7,8,9,10}){StoreTest.navigate(a,screen);save(a.getWindow().getDecorView(),"planning-"+screen+"-"+appearance);}
                a.getWindow().getDecorView().findViewWithTag("menu-toggle").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();save(ShadowDialog.getLatestDialog().getWindow().getDecorView(),"drawer-"+appearance);ShadowDialog.getLatestDialog().dismiss();StoreTest.navigate(a,0);
                a.getWindow().getDecorView().findViewWithTag("period-picker").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();save(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView(),"period-"+appearance);ShadowAlertDialog.getLatestAlertDialog().dismiss();
                StoreTest.navigate(a,6);save(a.getWindow().getDecorView(),"backups-"+appearance);StoreTest.navigate(a,5);save(a.getWindow().getDecorView(),"settings-"+appearance);StoreTest.navigate(a,0);a.getWindow().getDecorView().findViewWithTag("add-movement").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();save(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView(),"entry-"+appearance);ShadowAlertDialog.getLatestAlertDialog().dismiss();
            }
        }
    }
    private void save(View view,String name) throws Exception {
        view.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY));view.layout(0,0,1080,1920);Bitmap image=Bitmap.createBitmap(1080,1920,Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));
        File folder=new File("build/design-previews");folder.mkdirs();try(OutputStream out=new FileOutputStream(new File(folder,name+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}image.recycle();
    }
    @Test public void bankTransferIsBuiltInAndSelectableWithoutCreatingAccount()throws Exception{
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity activity=controller.get();activity.getWindow().getDecorView().findViewWithTag("add-movement").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();Spinner accounts=dialog.getWindow().getDecorView().findViewWithTag("אמצעי תשלום");boolean found=false;for(int n=0;n<accounts.getCount();n++)if(accounts.getItemAtPosition(n).equals("העברה בנקאית"))found=true;assertTrue(found);dialog.dismiss();
            StoreTest.navigate(activity,3);activity.getWindow().getDecorView().findViewWithTag("add-movement").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();dialog=ShadowAlertDialog.getLatestAlertDialog();accounts=dialog.getWindow().getDecorView().findViewWithTag("חשבון בנק או כרטיס לחיוב");assertEquals("העברה בנקאית",accounts.getSelectedItem());dialog.dismiss();
            StoreTest.navigate(activity,4);try(Store db=new Store(context)){for(Store.Account account:db.accounts())if(account.type.equals("transfer"))assertNull(activity.getWindow().getDecorView().findViewWithTag("edit-account-"+account.id));}
        }
    }

}
