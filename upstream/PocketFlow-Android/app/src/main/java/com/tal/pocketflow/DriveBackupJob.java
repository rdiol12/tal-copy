package com.tal.pocketflow;

import android.app.job.*;

/** Native Android scheduler: approximate daily/weekly execution, network required, survives reboot. */
public final class DriveBackupJob extends JobService {
    private DriveTask task;
    @Override public boolean onStartJob(JobParameters params){
        DriveState state=new DriveState(this);if(!state.connected()||!state.automatic()||state.prefs.getBoolean("needsAuth",false))return false;
        task=new DriveTask(this,DriveTask.UPLOAD,"",state.generation(),true);DriveTask active=task;
        active.attach(result->{if(task!=active)return;task=null;jobFinished(params,result.error!=null&&!result.authRequired);});active.start(null);return true;
    }
    @Override public boolean onStopJob(JobParameters params){if(task!=null){task.cancel();task.detach();task=null;}return true;}
    @Override public void onDestroy(){if(task!=null){task.cancel();task.detach();task=null;}super.onDestroy();}
}
