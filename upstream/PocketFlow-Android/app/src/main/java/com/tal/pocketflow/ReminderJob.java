package com.tal.pocketflow;
import android.app.*;
import android.app.job.*;
import android.content.*;
import android.os.Build;
import java.time.*;
import java.util.*;
/** Optional device-local daily reminders. Delivery timing is managed by Android. */
public class ReminderJob extends JobService {
 static void schedule(Context c){if(!c.getSharedPreferences("reminder-local",0).getBoolean("enabled",false))return;JobScheduler scheduler=c.getSystemService(JobScheduler.class);scheduler.schedule(new JobInfo.Builder(7002,new ComponentName(c,ReminderJob.class)).setPeriodic(24*60*60*1000L).setPersisted(true).build());}
 @Override public boolean onStartJob(JobParameters p){new Thread(()->{try{if(!getSharedPreferences("reminder-local",0).getBoolean("enabled",false))return;String day=LocalDate.now().toString();if(day.equals(getSharedPreferences("reminder-local",0).getString("sent","")))return;if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;try(Store s=new Store(this)){List<String> alerts=new Analytics(s,this).alerts(LocalDate.now());if(alerts.isEmpty())return;NotificationManager manager=getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel("planning","תזכורות ותקציבים",NotificationManager.IMPORTANCE_DEFAULT));Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,7002,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);manager.notify(7002,new Notification.Builder(this,"planning").setSmallIcon(R.drawable.nav_clock).setContentTitle("PocketFlow · "+alerts.size()+" פריטים לתשומת לבך").setContentText("פתח את האפליקציה לצפייה בתזכורות ובתקציבים").setContentIntent(pi).setAutoCancel(true).build());getSharedPreferences("reminder-local",0).edit().putString("sent",day).apply();}}catch(Exception ignored){}finally{jobFinished(p,false);}},"PocketFlow-reminders").start();return true;}
 @Override public boolean onStopJob(JobParameters p){return false;}
}
