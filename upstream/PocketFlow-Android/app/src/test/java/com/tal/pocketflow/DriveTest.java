package com.tal.pocketflow;

import android.app.job.*;
import android.content.*;
import android.os.Looper;
import android.widget.*;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class DriveTest {
    Context context;DriveState state;
    @Before public void before(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("pocketflow.db");for(String key:new String[]{"prefs","security","google-drive","backup-history"})context.getSharedPreferences(key,0).edit().clear().commit();state=new DriveState(context);state.scheduler().cancelAll();}
    @After public void after(){state.scheduler().cancelAll();state.prefs.edit().clear().commit();}
    JSONObject backup()throws Exception{try(Store db=new Store(context)){return db.backup();}}
    static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    static String checksum(byte[] bytes)throws Exception{StringBuilder result=new StringBuilder();for(byte b:MessageDigest.getInstance("MD5").digest(bytes))result.append(String.format(Locale.US,"%02x",b&255));return result.toString();}
    static byte[] uploadedPayload(byte[] body){String text=new String(body,StandardCharsets.UTF_8),prefix="Content-Type: application/json\r\n\r\n";int start=text.indexOf(prefix)+prefix.length();return bytes(text.substring(start,text.indexOf("\r\n--",start)));}
    static byte[] accepted(byte[] body,boolean corrupt)throws Exception{byte[] payload=uploadedPayload(body);return bytes(new JSONObject().put("id","file-123").put("name","backup.json").put("createdTime","2026-10-30T14:00:00Z").put("size",payload.length).put("md5Checksum",corrupt?"bad":checksum(payload)).toString());}
    DriveTask run(String action,String id,DriveApi.Transport transport,boolean background)throws Exception{
        DriveTask task=new DriveTask(context,action,id,state.generation(),background,t->new DriveApi(transport));final boolean[] done={false};task.attach(result->done[0]=true);task.start("ephemeral-token");long end=System.nanoTime()+5_000_000_000L;
        while(!done[0]&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5);}Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue("Cloud task timed out",done[0]);return task;
    }
    static byte[] user(){return bytes("{\"user\":{\"emailAddress\":\"test@example.com\",\"permissionId\":\"account-1\"}}");}
    @Test public void authorizationIsPinnedToAccountAndRequestsOnlyAppData(){AuthorizationRequest request=DriveTask.request("test@example.com");assertEquals("test@example.com",request.getAccount().name);assertEquals(1,request.getRequestedScopes().size());assertEquals(DriveState.SCOPE,request.getRequestedScopes().get(0).getScopeUri());}
    @Test public void jobsRequireConnectionAndNetworkAndRespectDailyWeeklyOff(){
        state.schedule();assertNull(state.scheduler().getPendingJob(DriveState.JOB_ID));state.connect("test@example.com","account-1");JobInfo daily=state.scheduler().getPendingJob(DriveState.JOB_ID);assertNotNull(daily);assertEquals(86400000L,daily.getIntervalMillis());assertTrue(daily.isPersisted());assertEquals(JobInfo.NETWORK_TYPE_ANY,daily.getNetworkType());
        state.automatic(true,7);assertEquals(7*86400000L,state.scheduler().getPendingJob(DriveState.JOB_ID).getIntervalMillis());state.automatic(false,7);assertNull(state.scheduler().getPendingJob(DriveState.JOB_ID));state.automatic(true,1);long generation=state.generation();state.disconnect();assertFalse(state.connected());assertTrue(state.generation()>generation);assertNull(state.scheduler().getPendingJob(DriveState.JOB_ID));
    }
    @Test public void authorizationFailureStopsScheduledWorkButPreservesBackupsHistory(){state.connect("test@example.com","account-1");state.success("backup.json");state.failure(true);state.schedule();assertNull(state.scheduler().getPendingJob(DriveState.JOB_ID));assertTrue(state.prefs.getBoolean("needsAuth",false));assertTrue(state.prefs.getLong("lastSuccess",0)>0);}
    @Test public void listIsScopedToPrivateAppDataAndFiftyLatestFiles()throws Exception{
        DriveApi api=new DriveApi((method,url,type,body)->{assertEquals("GET",method);assertTrue(url.contains("spaces=appDataFolder"));assertTrue(url.contains("pageSize=50"));assertTrue(url.contains("orderBy=createdTime"));assertTrue(url.contains("appProperties"));return bytes("{\"files\":[{\"id\":\"a\",\"name\":\"old.json\",\"size\":123}]} ");});List<DriveApi.Remote> files=api.list();assertEquals(1,files.size());assertEquals("a",files.get(0).id);
    }
    @Test public void multipartUploadIncludesFullDataPrivateParentAndIntegrityCheck()throws Exception{
        JSONObject backup=backup();DriveApi api=new DriveApi((method,url,type,body)->{assertEquals("POST",method);assertTrue(url.contains("uploadType=multipart"));assertTrue(type.startsWith("multipart/related; boundary="));String multipart=new String(body,StandardCharsets.UTF_8);assertTrue(multipart.contains("\"parents\":[\"appDataFolder\"]"));assertTrue(multipart.contains("\"app\":\"PocketFlow\""));JSONObject payload=new JSONObject(new String(uploadedPayload(body),StandardCharsets.UTF_8));assertEquals(backup.getJSONArray("accounts").toString(),payload.getJSONArray("accounts").toString());return accepted(body,false);});assertEquals("file-123",api.upload(backup).id);
        try{new DriveApi((m,u,t,b)->accepted(b,true)).upload(backup);fail("Accepted corrupt upload");}catch(IOException expected){}
    }
    @Test public void downloadValidatesPocketFlowFileWithoutChangingDatabase()throws Exception{
        JSONObject root=backup();DriveApi api=new DriveApi((method,url,type,body)->{assertTrue(url.contains("file-123?alt=media"));return bytes(root.toString());});assertEquals("PocketFlow",api.download("file-123").getString("format"));
        try{new DriveApi((m,u,t,b)->bytes("{\"format\":\"foreign\"}")).download("wrong");fail();}catch(Exception expected){}assertEquals(2,backup().getJSONArray("accounts").length());
    }
    @Test public void connectingUploadsFirstBackupAndRecordsOnlyServerConfirmedSuccess()throws Exception{
        final int[] uploads={0};DriveTask task=run(DriveTask.CONNECT,"",(method,url,type,body)->{if(url.contains("/about?"))return user();uploads[0]++;return accepted(body,false);},false);
        assertNull(task.error);assertEquals(1,uploads[0]);assertEquals("test@example.com",state.email());assertTrue(state.automatic());assertTrue(state.prefs.getLong("lastSuccess",0)>0);assertNotNull(state.scheduler().getPendingJob(DriveState.JOB_ID));assertFalse(state.prefs.getAll().containsKey("accessToken"));
    }
    @Test public void failedUploadDoesNotOverwritePreviousSuccessOrFinancialData()throws Exception{
        state.connect("test@example.com","account-1");state.success("previous.json");long last=state.prefs.getLong("lastSuccess",0);String data=backup().getJSONArray("accounts").toString();
        DriveTask task=run(DriveTask.UPLOAD,"",(method,url,type,body)->{if(url.contains("/about?"))return user();throw new IOException("Network unavailable");},true);
        assertNotNull(task.error);assertEquals(last,state.prefs.getLong("lastSuccess",0));assertEquals("previous.json",state.prefs.getString("lastFile",""));assertEquals(data,backup().getJSONArray("accounts").toString());assertFalse(state.prefs.getString("error","").isEmpty());
    }
    @Test public void wrongAccountCannotUploadOrRestore()throws Exception{
        state.connect("test@example.com","account-1");DriveTask task=run(DriveTask.UPLOAD,"",(m,u,t,b)->bytes("{\"user\":{\"emailAddress\":\"other@example.com\",\"permissionId\":\"account-2\"}}"),false);assertNotNull(task.error);assertEquals(0,state.prefs.getLong("lastSuccess",0));assertEquals("test@example.com",state.email());
    }
    @Test public void cloudDownloadStagesPreviewWithoutReplacingData()throws Exception{
        state.connect("test@example.com","account-1");JSONObject root=backup();DriveTask task=run(DriveTask.DOWNLOAD,"file-123",(m,u,t,b)->u.contains("/about?")?user():bytes(root.toString()),false);assertNull(task.error);assertTrue(new File(context.getCacheDir(),"restore-draft.json").isFile());assertEquals(root.getJSONArray("accounts").toString(),backup().getJSONArray("accounts").toString());
    }
    @Test public void cloudConnectionAndScheduleAreNotExportedOrReplacedOnRestore()throws Exception{
        state.connect("test@example.com","account-1");state.automatic(true,7);JSONObject root=backup();assertFalse(root.toString().contains("test@example.com"));assertFalse(root.has("google-drive"));try(Store db=new Store(context)){db.restore(root);}assertEquals("test@example.com",state.email());assertEquals(7,state.days());
    }
    @Test public void connectedBackupScreenShowsCloudControlsAndPreservesManualExport(){
        state.connect("test@example.com","account-1");
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();StoreTest.navigate(a,6);assertEquals("test@example.com",((TextView)a.getWindow().getDecorView().findViewWithTag("google-account")).getText());assertNotNull(a.getWindow().getDecorView().findViewWithTag("google-backup"));assertNotNull(a.getWindow().getDecorView().findViewWithTag("drive-backup"));((CheckBox)a.getWindow().getDecorView().findViewWithTag("google-auto")).setChecked(false);assertNull(state.scheduler().getPendingJob(DriveState.JOB_ID));
        }
    }
}
