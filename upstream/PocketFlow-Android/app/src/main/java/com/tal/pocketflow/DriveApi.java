package com.tal.pocketflow;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Drive v3 REST, restricted to PocketFlow's private appDataFolder. */
final class DriveApi {
    interface Transport { byte[] call(String method,String url,String contentType,byte[] body)throws Exception; }
    static final class ApiFailure extends IOException {final int status;ApiFailure(int status){super("Drive HTTP "+status);this.status=status;}}
    static final class Remote {final String id,name,date;final long size;Remote(JSONObject file)throws Exception{id=file.getString("id");name=file.getString("name");date=file.optString("createdTime","");size=file.optLong("size",0);}}
    final Transport transport;
    DriveApi(String token){this(new HttpTransport(token));}
    DriveApi(Transport transport){this.transport=transport;}
    static String encode(String value){try{return URLEncoder.encode(value,"UTF-8");}catch(UnsupportedEncodingException impossible){throw new IllegalStateException(impossible);}}
    JSONObject json(String method,String url,String type,byte[] body)throws Exception{return new JSONObject(new String(transport.call(method,url,type,body),StandardCharsets.UTF_8));}
    JSONObject user()throws Exception{return json("GET","https://www.googleapis.com/drive/v3/about?fields=user(emailAddress,permissionId)",null,null).getJSONObject("user");}
    List<Remote> list()throws Exception{
        String q="trashed = false and appProperties has { key='app' and value='PocketFlow' }";
        JSONObject data=json("GET","https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&pageSize=50&orderBy=createdTime%20desc&fields=files(id,name,createdTime,size)&q="+encode(q),null,null);
        JSONArray files=data.getJSONArray("files");List<Remote> result=new ArrayList<>();for(int i=0;i<files.length();i++)result.add(new Remote(files.getJSONObject(i)));return result;
    }
    Remote upload(JSONObject backup)throws Exception{
        BackupFiles.description(backup);byte[] payload=backup.toString().getBytes(StandardCharsets.UTF_8);if(payload.length>BackupFiles.MAX_BYTES)throw new IOException("Backup too large");
        String name=BackupFiles.filename(),boundary="PocketFlow"+UUID.randomUUID().toString().replace("-","");
        JSONObject meta=new JSONObject().put("name",name).put("mimeType","application/json").put("parents",new JSONArray().put("appDataFolder")).put("appProperties",new JSONObject().put("app","PocketFlow"));
        ByteArrayOutputStream body=new ByteArrayOutputStream();body.write(("--"+boundary+"\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"+meta+"\r\n--"+boundary+"\r\nContent-Type: application/json\r\n\r\n").getBytes(StandardCharsets.UTF_8));body.write(payload);body.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        JSONObject response=json("POST","https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,createdTime,size,md5Checksum","multipart/related; boundary="+boundary,body.toByteArray());
        StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("MD5").digest(payload))hash.append(String.format(Locale.US,"%02x",b&255));
        if(!hash.toString().equalsIgnoreCase(response.getString("md5Checksum"))||response.getLong("size")!=payload.length)throw new IOException("Upload integrity check failed");
        Remote remote=new Remote(response);if(remote.id.isEmpty())throw new IOException("Missing file ID");return remote;
    }
    JSONObject download(String id)throws Exception{
        if(id==null||id.isEmpty())throw new IOException("Missing backup ID");
        byte[] bytes=transport.call("GET","https://www.googleapis.com/drive/v3/files/"+encode(id)+"?alt=media",null,null);
        JSONObject root=BackupFiles.read(new ByteArrayInputStream(bytes));BackupFiles.description(root);return root;
    }
    void cancel(){if(transport instanceof HttpTransport)((HttpTransport)transport).cancel();}
    static final class HttpTransport implements Transport {
        final String token;volatile HttpURLConnection current;volatile boolean canceled;
        HttpTransport(String token){if(token==null||token.isEmpty())throw new IllegalArgumentException("Missing access token");this.token=token;}
        void cancel(){canceled=true;HttpURLConnection connection=current;if(connection!=null)connection.disconnect();}
        public byte[] call(String method,String url,String contentType,byte[] body)throws Exception{
            if(canceled)throw new InterruptedIOException();HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();current=connection;
            try{
                connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(20000);connection.setReadTimeout(45000);connection.setRequestMethod(method);connection.setRequestProperty("Authorization","Bearer "+token);connection.setRequestProperty("Accept","application/json");
                if(body!=null){connection.setDoOutput(true);connection.setRequestProperty("Content-Type",contentType);connection.setFixedLengthStreamingMode(body.length);try(OutputStream out=connection.getOutputStream()){out.write(body);}}
                int status=connection.getResponseCode();if(status<200||status>=300)throw new ApiFailure(status);
                try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[8192];int n,total=0;while((n=in.read(buffer))!=-1){if(canceled)throw new InterruptedIOException();total+=n;if(total>BackupFiles.MAX_BYTES)throw new IOException("Response too large");out.write(buffer,0,n);}return out.toByteArray();
                }
            }finally{connection.disconnect();current=null;}
        }
    }
}
