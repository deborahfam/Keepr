package com.keepr.app.data;

import java.io.*;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

/** Download the official APK into private storage and verify its published checksum. */
public final class UpdateDownload {
    public interface Progress { void update(long read,long total); }
    public static File fetch(AppUpdates.Release release,File directory,Progress progress) throws Exception {
        String expected=AppUpdates.RELEASES+"/download/v"+release.version()+"/Keepr-"+release.version()+".apk";
        if(!expected.equals(release.downloadUrl()) || release.bytes()<=0 || release.bytes()>256L*1024*1024) throw new IOException("Invalid release asset");
        return fetchVerified(release,directory,progress,new OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).callTimeout(10,TimeUnit.MINUTES).followSslRedirects(false).build(),expected);
    }
    static File fetchVerified(AppUpdates.Release release,File directory,Progress progress,OkHttpClient client,String expected) throws Exception {
        if(!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create update directory");
        File part=new File(directory,"update.part"),apk=new File(directory,"update.apk");
        try {
            String checksum;
            try(Response response=client.newCall(new Request.Builder().url(expected+".sha256").build()).execute()) {
                if(!response.isSuccessful() || response.body()==null || response.body().source().request(4097)) throw new IOException("Checksum unavailable");
                checksum=response.body().string().trim().split("\\s+")[0];
                if(!checksum.matches("[a-fA-F0-9]{64}")) throw new IOException("Invalid checksum");
            }
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(Response response=client.newCall(new Request.Builder().url(expected).build()).execute()) {
                if(!response.isSuccessful() || response.body()==null) throw new IOException("Download unavailable");
                try(InputStream input=response.body().byteStream(); OutputStream output=new FileOutputStream(part)) {
                    byte[] buffer=new byte[32768]; int count; long total=0;
                    while((count=input.read(buffer))!=-1) { total+=count; if(total>release.bytes()) throw new IOException("Unexpected download size"); output.write(buffer,0,count); digest.update(buffer,0,count); progress.update(total,release.bytes()); }
                    if(total!=release.bytes()) throw new IOException("Incomplete download");
                }
            }
            StringBuilder actual=new StringBuilder(); for(byte value:digest.digest()) actual.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
            if(!checksum.equalsIgnoreCase(actual.toString())) throw new IOException("Checksum mismatch");
            if(apk.exists() && !apk.delete()) throw new IOException("Cannot replace old update");
            if(!part.renameTo(apk)) throw new IOException("Cannot finish update");
            return apk;
        } finally { part.delete(); }
    }
}
