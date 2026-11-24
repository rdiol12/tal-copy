package com.tal.pocketflow;

import android.accounts.Account;
import android.app.PendingIntent;
import android.content.Context;
import android.os.*;
import com.google.android.gms.auth.api.identity.*;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Tasks;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** One retained foreground task, or one scheduled background task. No token is persisted. */
final class DriveTask {
    interface Completion {void complete(DriveTask task);}
    interface ApiFactory {DriveApi create(String token);}
    final ApiFactory factory;
    static final String CONNECT="connect",UPLOAD="upload",LIST="list",DOWNLOAD="download",REVOKE="revoke";
    static final class NeedsAuth extends IOException {NeedsAuth(){super("Google authorization required");}}
    static final Object CLOUD_LOCK=new Object();
    final Context context;final String action,fileId;final long generation;final boolean background;
    PendingIntent resolution;Exception error;boolean authRequired;List<DriveApi.Remote> files;String savedName="";
    private Completion completion;private boolean done,delivered;private volatile boolean canceled;private volatile DriveApi api;
    DriveTask(Context context,String action,String fileId,long generation,boolean background){this(context,action,fileId,generation,background,DriveApi::new);}
    DriveTask(Context context,String action,String fileId,long generation,boolean background,ApiFactory factory){this.factory=factory;this.context=context.getApplicationContext();this.action=action;this.fileId=fileId;this.generation=generation;this.background=background;}
    static List<Scope> scopes(){return Collections.singletonList(new Scope(DriveState.SCOPE));}
    static AuthorizationRequest request(String email){AuthorizationRequest.Builder builder=AuthorizationRequest.builder().setRequestedScopes(scopes()).setOptOutIncludingGrantedScopes(true);if(!email.isEmpty())builder.setAccount(new Account(email,"com.google"));return builder.build();}
    void attach(Completion callback){completion=callback;deliver();}
    void detach(){completion=null;}
    private void deliver(){if(done&&!delivered&&completion!=null){delivered=true;completion.complete(this);completion=null;}}
    void cancel(){canceled=true;DriveApi current=api;if(current!=null)current.cancel();}
    void check()throws IOException{if(canceled||new DriveState(context).generation()!=generation)throw new InterruptedIOException("Connection changed");}
    void start(String authorizedToken){
        Handler main=new Handler(Looper.getMainLooper());new Thread(()->{
            DriveState state=new DriveState(context);
            try{check();if(REVOKE.equals(action)){revoke(state);return;}synchronized(CLOUD_LOCK){check();
                if(!CONNECT.equals(action)&&!state.connected())throw new IOException("Not connected");
                String token=authorizedToken;
                for(int attempt=0;attempt<2;attempt++){
                    if(token==null){AuthorizationResult auth=Tasks.await(Identity.getAuthorizationClient(context).authorize(request(state.email())),45,TimeUnit.SECONDS);
                        if(auth.hasResolution()){resolution=auth.getPendingIntent();authRequired=true;return;}
                        token=token(auth);
                    }
                    check();api=factory.create(token);
                    try{execute(state);break;}catch(DriveApi.ApiFailure failure){
                        if(failure.status==401&&attempt==0){Tasks.await(Identity.getAuthorizationClient(context).clearToken(ClearTokenRequest.builder().setToken(token).build()),15,TimeUnit.SECONDS);token=null;continue;}
                        authRequired=failure.status==401;throw failure;
                    }
                }
            }}catch(Exception failure){error=failure;Throwable cause=failure;while(cause.getCause()!=null)cause=cause.getCause();
                if(cause instanceof com.google.android.gms.common.api.ApiException){int code=((com.google.android.gms.common.api.ApiException)cause).getStatusCode();authRequired=code==4||code==5||code==10;}
                if(cause instanceof NeedsAuth)authRequired=true;
            }
            finally{
                if(!canceled&&state.generation()==generation&&((error!=null&&(UPLOAD.equals(action)||CONNECT.equals(action)||authRequired))||(background&&authRequired))){state.failure(authRequired);if(authRequired)state.schedule();}
                main.post(()->{done=true;deliver();});
            }
        },"PocketFlow-Drive").start();
    }
    static String token(AuthorizationResult result)throws IOException{
        if(!result.getGrantedScopes().contains(DriveState.SCOPE)||result.getAccessToken()==null||result.getAccessToken().isEmpty())throw new NeedsAuth();return result.getAccessToken();
    }
    private void execute(DriveState state)throws Exception{
        check();JSONObject user=api.user();String email=user.getString("emailAddress"),id=user.getString("permissionId");
        if(CONNECT.equals(action)){check();state.connect(email,id);}
        else if(!id.equals(state.prefs.getString("userId","")))throw new NeedsAuth();
        check();
        if(CONNECT.equals(action)||UPLOAD.equals(action)){
            if(background&&!state.automatic())throw new InterruptedIOException();
            JSONObject backup;try(Store db=new Store(context)){backup=db.backup();}
            check();DriveApi.Remote remote=api.upload(backup);check();savedName=remote.name;state.success(remote.name);
        }else if(LIST.equals(action)){files=api.list();check();}
        else if(DOWNLOAD.equals(action)){
            JSONObject backup=api.download(fileId);check();
            try(OutputStream out=new FileOutputStream(new File(context.getCacheDir(),"restore-draft.json"))){out.write(backup.toString().getBytes(StandardCharsets.UTF_8));}
        }
    }
    private void revoke(DriveState state)throws Exception{
        String email=state.email();if(email.isEmpty())return;
        // Always stop local access, even if Google cannot be reached to revoke the grant.
        state.disconnect();
        Tasks.await(Identity.getAuthorizationClient(context).revokeAccess(RevokeAccessRequest.builder().setAccount(new Account(email,"com.google")).setScopes(scopes()).build()),45,TimeUnit.SECONDS);
    }
}
