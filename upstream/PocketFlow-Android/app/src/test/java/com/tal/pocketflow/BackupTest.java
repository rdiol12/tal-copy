package com.tal.pocketflow;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class BackupTest {
    Context context;Uri drive=Uri.parse("content://com.google.android.apps.docs.storage/document/test");
    @Before public void before(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("pocketflow.db");for(String key:new String[]{"prefs","security","backup-history"})context.getSharedPreferences(key,0).edit().clear().commit();new File(context.getCacheDir(),"restore-draft.json").delete();new File(context.getCacheDir(),"export-draft").delete();}
    void waitForTransfer(MainActivity activity)throws Exception{
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("backupTransfer");field.setAccessible(true);long end=System.nanoTime()+5_000_000_000L;
        while(field.get(activity)!=null&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5);}
        Shadows.shadowOf(Looper.getMainLooper()).idle();assertNull("Transfer did not complete",field.get(activity));
    }
    JSONObject snapshot()throws Exception{try(Store db=new Store(context)){return db.backup();}}
    void addExpense()throws Exception{try(Store db=new Store(context)){Store.Tx tx=new Store.Tx();tx.account=db.accounts().get(0).id;tx.amount=1234;tx.date="2026-10-01";tx.kind="expense";tx.category="בית";tx.merchant="Amazon";tx.note="";tx.tag="";tx.count=1;tx.status=1;db.save(tx);}}
    void selectRestore(MainActivity a,JSONObject backup)throws Exception{
        Shadows.shadowOf(context.getContentResolver()).registerInputStream(drive,new ByteArrayInputStream(backup.toString().getBytes(StandardCharsets.UTF_8)));
        a.onActivityResult(104,Activity.RESULT_OK,new Intent().setData(drive));waitForTransfer(a);
    }
    @Test public void driveExportUsesSystemPickerAndWritesFullBackupOnlyAfterSelection()throws Exception{
        addExpense();
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=c.get();StoreTest.navigate(a,6);a.findViewById(android.R.id.content).findViewWithTag("drive-backup").performClick();
            ShadowActivity.IntentForResult next=Shadows.shadowOf(a).getNextStartedActivityForResult();assertEquals(103,next.requestCode);assertEquals(Intent.ACTION_CREATE_DOCUMENT,next.intent.getAction());assertEquals("application/json",next.intent.getType());assertTrue(next.intent.hasCategory(Intent.CATEGORY_OPENABLE));assertTrue(next.intent.getStringExtra(Intent.EXTRA_TITLE).matches("PocketFlow-backup-.*\\.json"));assertEquals(0,context.getSharedPreferences("backup-history",0).getLong("savedAt",0));
            ByteArrayOutputStream out=new ByteArrayOutputStream();Shadows.shadowOf(context.getContentResolver()).registerOutputStream(drive,out);a.onActivityResult(103,Activity.RESULT_OK,new Intent().setData(drive));
            java.lang.reflect.Method csv=MainActivity.class.getDeclaredMethod("exportCsv");csv.setAccessible(true);csv.invoke(a);assertNull(Shadows.shadowOf(a).getNextStartedActivityForResult());waitForTransfer(a);
            JSONObject backup=new JSONObject(out.toString("UTF-8"));assertEquals("PocketFlow",backup.getString("format"));assertEquals(1,backup.getJSONArray("transactions").length());assertEquals(1234,backup.getJSONArray("transactions").getJSONObject(0).getLong("amount"));assertTrue(context.getSharedPreferences("backup-history",0).getLong("savedAt",0)>0);assertNotNull(a.findViewById(android.R.id.content).findViewWithTag("backup-last-saved"));assertFalse(new File(context.getCacheDir(),"export-draft").exists());
        }
    }
    @Test public void cancelOrFailedWriteDoesNotRecordSuccessOrModifyData()throws Exception{
        addExpense();
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=c.get();StoreTest.navigate(a,6);a.findViewById(android.R.id.content).findViewWithTag("drive-backup").performClick();Shadows.shadowOf(a).getNextStartedActivityForResult();a.onActivityResult(103,Activity.RESULT_CANCELED,null);assertFalse(new File(context.getCacheDir(),"export-draft").exists());
            a.findViewById(android.R.id.content).findViewWithTag("drive-backup").performClick();Shadows.shadowOf(a).getNextStartedActivityForResult();Shadows.shadowOf(context.getContentResolver()).registerOutputStream(drive,new OutputStream(){public void write(int value)throws IOException{throw new IOException("offline");}});a.onActivityResult(103,Activity.RESULT_OK,new Intent().setData(drive));waitForTransfer(a);
            assertEquals(0,context.getSharedPreferences("backup-history",0).getLong("savedAt",0));assertEquals(1,snapshot().getJSONArray("transactions").length());
        }
    }
    @Test public void restoreShowsPreviewAndCancellationPreservesCurrentData()throws Exception{
        JSONObject empty=snapshot();addExpense();
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=c.get();StoreTest.navigate(a,6);a.findViewById(android.R.id.content).findViewWithTag("drive-restore").performClick();ShadowActivity.IntentForResult picker=Shadows.shadowOf(a).getNextStartedActivityForResult();assertEquals(Intent.ACTION_OPEN_DOCUMENT,picker.intent.getAction());assertEquals(104,picker.requestCode);
            selectRestore(a,empty);AlertDialog preview=ShadowAlertDialog.getLatestAlertDialog();assertTrue(preview.isShowing());assertEquals(1,snapshot().getJSONArray("transactions").length());assertTrue(Shadows.shadowOf(preview).getMessage().toString().contains("תנועות: 0"));preview.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(1,snapshot().getJSONArray("transactions").length());assertFalse(new File(context.getCacheDir(),"restore-draft.json").exists());
        }
    }
    @Test public void confirmedRestoreReplacesDataAndInvalidRestoreRollsBack()throws Exception{
        JSONObject empty=snapshot();addExpense();JSONObject invalid=snapshot();invalid.getJSONArray("transactions").getJSONObject(0).put("amount",-1);
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=c.get();selectRestore(a,invalid);ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(1,snapshot().getJSONArray("transactions").length());
            selectRestore(a,empty);ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(0,snapshot().getJSONArray("transactions").length());
        }
    }
    @Test public void previewSurvivesRecreationWithoutRestoringAutomatically()throws Exception{
        JSONObject empty=snapshot();addExpense();
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
            selectRestore(c.get(),empty);c.recreate();assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing());assertEquals(1,snapshot().getJSONArray("transactions").length());ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        }
    }
    @Test public void transferCompletionWaitsForReattachedActivity()throws Exception{
        new File(context.getCacheDir(),"export-draft").createNewFile();ByteArrayOutputStream out=new ByteArrayOutputStream();Shadows.shadowOf(context.getContentResolver()).registerOutputStream(drive,out);
        BackupTransfer transfer=new BackupTransfer(103,drive);final int[] deliveries={0};transfer.attach(t->deliveries[0]++);transfer.detach();transfer.start(context);
        long end=System.nanoTime()+3_000_000_000L;java.lang.reflect.Field done=BackupTransfer.class.getDeclaredField("done");done.setAccessible(true);
        while(!done.getBoolean(transfer)&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5);}assertEquals(0,deliveries[0]);transfer.attach(t->deliveries[0]++);assertEquals(1,deliveries[0]);transfer.attach(t->deliveries[0]++);assertEquals(1,deliveries[0]);
    }
    @Test @Config(sdk=26) public void restorePreviewWaitsForDeviceUnlock()throws Exception{
        JSONObject empty=snapshot();addExpense();context.getSharedPreferences("security",0).edit().putBoolean("entryLock",true).commit();Shadows.shadowOf(context.getSystemService(KeyguardManager.class)).setIsDeviceSecure(true);
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=c.get();selectRestore(a,empty);assertNotNull(a.findViewById(android.R.id.content).findViewWithTag("app-locked"));assertEquals(1,snapshot().getJSONArray("transactions").length());
            a.onActivityResult(AppLock.CREDENTIAL_REQUEST,Activity.RESULT_OK,null);Shadows.shadowOf(Looper.getMainLooper()).idle();AlertDialog preview=ShadowAlertDialog.getLatestAlertDialog();assertTrue(preview.isShowing());assertTrue((preview.getWindow().getAttributes().flags&android.view.WindowManager.LayoutParams.FLAG_SECURE)!=0);preview.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();
        }finally{context.getSharedPreferences("security",0).edit().clear().commit();}
    }
    @Test public void malformedAndOversizedFilesAreRejected()throws Exception{
        try{BackupFiles.description(new JSONObject("{\"format\":\"other\"}"));fail();}catch(Exception expected){}
        try{BackupFiles.read(new ByteArrayInputStream("not json".getBytes(StandardCharsets.UTF_8)));fail();}catch(Exception expected){}
        InputStream huge=new InputStream(){int remaining=BackupFiles.MAX_BYTES+1;public int read(){return remaining-->0?' ': -1;}public int read(byte[] b,int off,int len){if(remaining<=0)return -1;int n=Math.min(remaining,len);remaining-=n;java.util.Arrays.fill(b,off,off+n,(byte)' ');return n;}};
        try{BackupFiles.read(huge);fail();}catch(IOException expected){}
        assertTrue(BackupFiles.isDrive(drive));assertFalse(BackupFiles.isDrive(Uri.parse("content://other/document/1")));
    }
}
