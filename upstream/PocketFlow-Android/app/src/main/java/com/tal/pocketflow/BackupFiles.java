package com.tal.pocketflow;

import android.net.Uri;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;

/** Documents UI gives access only to the file selected by the user, including Drive files. */
final class BackupFiles {
    static final int MAX_BYTES=20_000_000;
    static String filename(){return "PocketFlow-backup-"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss-SSS"))+".json";}
    static boolean isDrive(Uri uri){return "com.google.android.apps.docs.storage".equals(uri.getAuthority())||"com.google.android.apps.docs.storage.legacy".equals(uri.getAuthority());}
    static void copy(InputStream in,OutputStream out)throws IOException{
        if(in==null||out==null)throw new IOException("File unavailable");
        byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);out.flush();
    }
    static JSONObject read(InputStream in)throws Exception{
        if(in==null)throw new IOException("File unavailable");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count,total=0;
        while((count=in.read(buffer))!=-1){total+=count;if(total>MAX_BYTES)throw new IOException("Backup too large");bytes.write(buffer,0,count);}
        String json=bytes.toString(StandardCharsets.UTF_8.name());if(json.startsWith("\uFEFF"))json=json.substring(1);return new JSONObject(json);
    }
    static String description(JSONObject root)throws Exception{
        if(!"PocketFlow".equals(root.getString("format"))||(root.getInt("version")<1||root.getInt("version")>9))throw new IOException("Unsupported backup");
        int accounts=root.getJSONArray("accounts").length(),rules=root.getJSONArray("rules").length(),transactions=root.getJSONArray("transactions").length();
        if(accounts==0||accounts>100000||rules>100000||transactions>100000)throw new IOException("Invalid backup size");
        String created="לא צוין";try{created=Instant.parse(root.getString("createdAt")).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));}catch(Exception ignored){}
        return "נוצר בתאריך: "+created+"\nתנועות: "+transactions+"\nהוראות קבע: "+rules+"\nחשבונות וכרטיסים: "+accounts;
    }
}
