package com.tal.pocketflow;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.CancellationSignal;

/** Device-local entry protection. Authentication results and credentials are never backed up. */
final class AppLock {
    static final int CREDENTIAL_REQUEST=701;
    private final Activity activity;
    private CancellationSignal cancellation;
    private Runnable success,cancel;
    private boolean credentialPending;
    private long generation;

    AppLock(Activity activity){this.activity=activity;}
    boolean enabled(){return activity.getSharedPreferences("security",Context.MODE_PRIVATE).getBoolean("entryLock",false);}
    void enabled(boolean enabled){activity.getSharedPreferences("security",Context.MODE_PRIVATE).edit().putBoolean("entryLock",enabled).commit();}
    boolean secure(){KeyguardManager manager=activity.getSystemService(KeyguardManager.class);return manager!=null&&manager.isDeviceSecure();}
    boolean busy(){return success!=null;}
    @SuppressWarnings("deprecation")
    void authenticate(Runnable onSuccess,Runnable onCancel){
        if(busy())return;
        success=onSuccess;cancel=onCancel;final long token=++generation;
        if(!secure()){complete(false);return;}
        try{
            if(Build.VERSION.SDK_INT>=28){
                if(Build.VERSION.SDK_INT==28){FingerprintManager fingerprint=activity.getSystemService(FingerprintManager.class);if(fingerprint==null||!fingerprint.isHardwareDetected()||!fingerprint.hasEnrolledFingerprints()){credential();return;}}
                BiometricPrompt.Builder builder=new BiometricPrompt.Builder(activity).setTitle("פתיחת PocketFlow").setSubtitle("אימות באמצעות טביעת אצבע או נעילת המכשיר");
                if(Build.VERSION.SDK_INT>=30)builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG|BiometricManager.Authenticators.DEVICE_CREDENTIAL);
                else if(Build.VERSION.SDK_INT>=29)builder.setDeviceCredentialAllowed(true);
                else builder.setNegativeButton("קוד נעילת המכשיר",activity.getMainExecutor(),(dialog,which)->{if(token==generation&&busy())credential();});
                cancellation=new CancellationSignal();
                builder.build().authenticate(cancellation,activity.getMainExecutor(),new BiometricPrompt.AuthenticationCallback(){
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){if(token==generation&&busy())complete(true);}
                    @Override public void onAuthenticationError(int error,CharSequence message){
                        if(token!=generation||!busy()||credentialPending)return;
                        if(error==BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS||error==BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT||error==BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE||error==BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT||error==BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT)credential();
                        else complete(false);
                    }
                });
            }else credential();
        }catch(RuntimeException error){credential();}
    }
    @SuppressWarnings("deprecation")
    private void credential(){
        if(credentialPending||!busy())return;
        try{
            KeyguardManager manager=activity.getSystemService(KeyguardManager.class);
            Intent intent=manager==null?null:manager.createConfirmDeviceCredentialIntent("PocketFlow","יש לאמת את נעילת המכשיר כדי להמשיך");
            if(intent==null){complete(false);return;}
            credentialPending=true;activity.startActivityForResult(intent,CREDENTIAL_REQUEST);
        }catch(RuntimeException error){complete(false);}
    }
    boolean result(int request,int result){
        if(request!=CREDENTIAL_REQUEST)return false;
        if(credentialPending&&busy())complete(result==Activity.RESULT_OK);
        return true;
    }
    private void complete(boolean passed){
        Runnable action=passed?success:cancel;success=null;cancel=null;cancellation=null;credentialPending=false;generation++;
        if(action!=null)action.run();
    }
    void background(){
        if(credentialPending)return; // Android's credential screen temporarily stops the app.
        CancellationSignal signal=cancellation;generation++;success=null;cancel=null;cancellation=null;
        if(signal!=null)signal.cancel();
    }
}
