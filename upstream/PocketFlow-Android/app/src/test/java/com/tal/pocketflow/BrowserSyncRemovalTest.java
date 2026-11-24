package com.tal.pocketflow;
import android.app.*;import android.content.*;import android.widget.*;import org.json.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.android.controller.ActivityController;import org.robolectric.annotation.Config;import org.robolectric.shadows.ShadowAlertDialog;import java.time.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class BrowserSyncRemovalTest {
 @Test public void upgradeDisconnectsLegacySyncKeepsLocalDataAndDriveAndAllowsSaving()throws Exception{
  Context c=RuntimeEnvironment.getApplication();c.deleteDatabase("pocketflow.db");c.getSharedPreferences("prefs",0).edit().clear().commit();c.getSharedPreferences("security",0).edit().clear().commit();c.getSharedPreferences("browser-sync",0).edit().putBoolean("enabled",true).putString("accountKey","old-browser-user").putLong("revision",20).commit();
  DriveState drive=new DriveState(c);drive.prefs.edit().clear().commit();drive.connect("test@example.invalid","test-id");Tools.write(c,new JSONObject().put("budgets",new JSONObject().put("",100000)),false);
  try(Store store=new Store(c)){
   Store.Account account=new Store.Account();account.name="בנק";account.type="bank";account.start="2026-01-01";account.closing=31;account.charge=10;store.save(account);
   Store.Tx old=new Store.Tx();old.kind="expense";old.date=LocalDate.now().toString();old.category="בית";old.merchant="חנות";old.tag="";old.note="נתון קיים";old.account=account.id;old.amount=12300;old.originalAmount=12300;old.count=1;old.status=1;store.save(old);
   try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
    MainActivity a=controller.get();assertTrue(c.getSharedPreferences("browser-sync",0).getAll().isEmpty());assertEquals("test@example.invalid",drive.email());assertTrue(drive.automatic());assertEquals(100000,Tools.budgets(c).getLong(""));assertEquals("נתון קיים",store.transactions().get(0).note);
    StoreTest.navigate(a,5);assertNull(a.getWindow().getDecorView().findViewWithTag("browser-sync-connect"));StoreTest.navigate(a,0);
    a.getWindow().getDecorView().findViewWithTag("add-movement").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing());EditText amount=dialog.getWindow().getDecorView().findViewWithTag("סכום");amount.setText("25");AutoCompleteTextView category=dialog.getWindow().getDecorView().findViewWithTag("קטגוריה");category.setText("בית");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertFalse(org.robolectric.shadows.ShadowToast.getTextOfLatestToast(),dialog.isShowing());assertEquals(2,store.transactions().size());assertTrue(c.getSharedPreferences("browser-sync",0).getAll().isEmpty());
   }
  }finally{drive.prefs.edit().clear().commit();}
 }
}
