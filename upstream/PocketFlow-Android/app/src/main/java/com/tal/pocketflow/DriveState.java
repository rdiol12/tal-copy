package com.tal.pocketflow;

import android.app.job.*;
import android.content.*;

/** Local connection metadata only. Tokens and this state are excluded from exported backups. */
final class DriveState {
    static final int JOB_ID=7001;
    static final String SCOPE="https://www.googleapis.com/auth/drive.appdata";
    final Context context;final SharedPreferences prefs;
    DriveState(Context context){this.context=context.getApplicationContext();prefs=this.context.getSharedPreferences("google-drive",Context.MODE_PRIVATE);}
    String email(){return prefs.getString("email","");}
    boolean connected(){return !email().isEmpty();}
    long generation(){return prefs.getLong("generation",0);}
    boolean automatic(){return prefs.getBoolean("automatic",true);}
    int days(){return prefs.getInt("days",1)==7?7:1;}
    void connect(String email,String id){boolean first=!connected();if(email.isEmpty()||id.isEmpty())throw new IllegalArgumentException("Missing account identity");prefs.edit().putBoolean("automatic",first||automatic()).putString("email",email).putString("userId",id).putBoolean("needsAuth",false).putString("error","").apply();schedule();}
    void success(String name){prefs.edit().putLong("lastSuccess",System.currentTimeMillis()).putString("lastFile",name).putBoolean("needsAuth",false).putString("error","").apply();}
    void failure(boolean auth){prefs.edit().putBoolean("needsAuth",auth).putString("error",auth?"נדרש חיבור מחדש לחשבון Google":"הגיבוי האחרון נכשל. ננסה שוב כשיתאפשר חיבור.").apply();}
    void automatic(boolean on,int days){prefs.edit().putBoolean("automatic",on).putInt("days",days==7?7:1).apply();schedule();}
    void disconnect(){long next=generation()+1;prefs.edit().clear().putLong("generation",next).putBoolean("automatic",false).apply();scheduler().cancel(JOB_ID);}
    JobScheduler scheduler(){return (JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);}
    JobInfo specification(){return new JobInfo.Builder(JOB_ID,new ComponentName(context,DriveBackupJob.class))
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
        .setPeriodic(days()*24L*60*60*1000).setBackoffCriteria(30*60*1000L,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build();}
    boolean schedule(){if(!connected()||!automatic()||prefs.getBoolean("needsAuth",false)){scheduler().cancel(JOB_ID);return true;}JobInfo job=scheduler().getPendingJob(JOB_ID);long interval=days()*24L*60*60*1000;
        if(job!=null&&job.getIntervalMillis()==interval)return true;
        boolean ok=scheduler().schedule(specification())==JobScheduler.RESULT_SUCCESS;if(!ok)prefs.edit().putString("error","Android לא אפשר לתזמן גיבוי. נסה להפעיל שוב את הגיבוי האוטומטי.").apply();return ok;
    }
}
