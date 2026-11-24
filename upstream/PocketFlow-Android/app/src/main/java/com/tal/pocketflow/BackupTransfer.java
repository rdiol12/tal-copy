package com.tal.pocketflow;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Cloud providers may block while downloading: never perform their I/O on the UI thread. */
final class BackupTransfer {
    interface Completion { void complete(BackupTransfer transfer); }
    final int request;final Uri uri;
    private Completion completion;private boolean done,delivered;
    Exception error;
    BackupTransfer(int request,Uri uri){this.request=request;this.uri=uri;}
    void attach(Completion value){completion=value;deliver();}
    void detach(){completion=null;}
    private void deliver(){if(done&&!delivered&&completion!=null){delivered=true;completion.complete(this);completion=null;}}
    void start(Context context){
        Context app=context.getApplicationContext();Handler main=new Handler(Looper.getMainLooper());
        new Thread(()->{
            try{
                if(request==101||request==103||request==105){
                    try(InputStream in=new FileInputStream(new File(app.getCacheDir(),"export-draft"));OutputStream out=app.getContentResolver().openOutputStream(uri,"wt")){BackupFiles.copy(in,out);}
                }else{
                    JSONObject backup;try(InputStream in=app.getContentResolver().openInputStream(uri)){backup=BackupFiles.read(in);}
                    BackupFiles.description(backup);
                    try(OutputStream out=new FileOutputStream(new File(app.getCacheDir(),"restore-draft.json"))){out.write(backup.toString().getBytes(StandardCharsets.UTF_8));}
                }
            }catch(Exception failure){error=failure;}
            main.post(()->{done=true;deliver();});
        },"PocketFlow-backup").start();
    }
}
