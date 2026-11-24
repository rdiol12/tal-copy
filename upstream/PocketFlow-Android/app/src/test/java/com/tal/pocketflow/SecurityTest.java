package com.tal.pocketflow;

import android.app.*;
import android.content.*;
import android.os.Looper;
import android.view.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=26)
public class SecurityTest {
    Context context;
    @Before public void before(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("pocketflow.db");context.getSharedPreferences("prefs",0).edit().clear().commit();context.getSharedPreferences("security",0).edit().clear().commit();Shadows.shadowOf(context.getSystemService(KeyguardManager.class)).setIsDeviceSecure(true);}
    @After public void after(){context.getSharedPreferences("security",0).edit().clear().commit();}
    void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
    boolean locked(MainActivity a){return a.getWindow().getDecorView().findViewWithTag("app-locked")!=null;}
    @Test public void coldStartCancellationAndBackgroundNeverExposeData(){
        context.getSharedPreferences("security",0).edit().putBoolean("entryLock",true).commit();
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();idle();assertTrue(locked(a));assertNull(a.getWindow().getDecorView().findViewWithTag("menu-toggle"));assertTrue((a.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0);
            assertEquals(AppLock.CREDENTIAL_REQUEST,Shadows.shadowOf(a).getNextStartedActivityForResult().requestCode);
            a.onActivityResult(AppLock.CREDENTIAL_REQUEST,Activity.RESULT_CANCELED,null);assertTrue(locked(a));
            a.getWindow().getDecorView().findViewWithTag("unlock-app").performClick();a.onActivityResult(AppLock.CREDENTIAL_REQUEST,Activity.RESULT_OK,null);assertFalse(locked(a));
            a.getWindow().getDecorView().findViewWithTag("add-movement").performClick();idle();AlertDialog draft=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(draft.isShowing());
            controller.pause().stop();assertTrue(locked(a));assertFalse(draft.isShowing());controller.start().resume();idle();assertTrue(locked(a));
        }
    }
    @Test public void authenticationStartsOnResumeWithoutIntermediateButtonAndDoesNotLoopOnCancel(){
        context.getSharedPreferences("security",0).edit().putBoolean("entryLock",true).commit();
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).create().start()){
            MainActivity a=controller.get();assertTrue(locked(a));
            assertNull(a.getWindow().getDecorView().findViewWithTag("unlock-app"));
            assertNull(Shadows.shadowOf(a).getNextStartedActivityForResult());
            controller.resume();
            assertEquals(AppLock.CREDENTIAL_REQUEST,Shadows.shadowOf(a).getNextStartedActivityForResult().requestCode);
            // A credential activity stops the app; its return must not launch a second prompt.
            controller.pause().stop().start();
            a.onActivityResult(AppLock.CREDENTIAL_REQUEST,Activity.RESULT_CANCELED,null);controller.resume();idle();
            assertTrue(locked(a));assertNotNull(a.getWindow().getDecorView().findViewWithTag("unlock-app"));
            assertNull(Shadows.shadowOf(a).getNextStartedActivityForResult());
            a.getWindow().getDecorView().findViewWithTag("unlock-app").performClick();
            assertEquals(AppLock.CREDENTIAL_REQUEST,Shadows.shadowOf(a).getNextStartedActivityForResult().requestCode);
            a.onActivityResult(AppLock.CREDENTIAL_REQUEST,Activity.RESULT_OK,null);assertFalse(locked(a));
            controller.pause().stop().start().resume();
            assertNull(a.getWindow().getDecorView().findViewWithTag("unlock-app"));
            assertEquals(AppLock.CREDENTIAL_REQUEST,Shadows.shadowOf(a).getNextStartedActivityForResult().requestCode);
        }
    }
    @Test public void activationAndDeactivationRequireSuccessfulDeviceAuthentication() throws Exception {
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();StoreTest.navigate(a,5);a.getWindow().getDecorView().findViewWithTag("entry-lock-setting").performClick();idle();assertFalse(new AppLock(a).enabled());
            a.onActivityResult(AppLock.CREDENTIAL_REQUEST,Activity.RESULT_OK,null);assertTrue(new AppLock(a).enabled());
            a.getWindow().getDecorView().findViewWithTag("entry-lock-setting").performClick();a.onActivityResult(AppLock.CREDENTIAL_REQUEST,Activity.RESULT_CANCELED,null);assertTrue(new AppLock(a).enabled());
            try(Store db=new Store(context)){org.json.JSONObject backup=db.backup();assertFalse(backup.getJSONObject("settings").has("entryLock"));db.restore(backup);assertTrue(new AppLock(a).enabled());}
            a.getWindow().getDecorView().findViewWithTag("entry-lock-setting").performClick();a.onActivityResult(AppLock.CREDENTIAL_REQUEST,Activity.RESULT_OK,null);assertFalse(new AppLock(a).enabled());
        }
    }
    @Test public void unsecuredDeviceCannotEnableProtection(){
        Shadows.shadowOf(context.getSystemService(KeyguardManager.class)).setIsDeviceSecure(false);
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();StoreTest.navigate(a,5);a.getWindow().getDecorView().findViewWithTag("entry-lock-setting").performClick();assertFalse(new AppLock(a).enabled());
        }
    }
}
