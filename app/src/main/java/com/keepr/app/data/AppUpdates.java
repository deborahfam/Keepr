package com.keepr.app.data;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import okhttp3.*;
import org.json.*;

/** Reads public release metadata for the in-app updater. */
public final class AppUpdates {
    public static final String RELEASES = "https://github.com/deborahfam/Keepr/releases";
    private static final String API = "https://api.github.com/repos/deborahfam/Keepr/releases/latest";
    private static final Pattern VERSION = Pattern.compile("v?(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})");
    private static final int MAX_RESPONSE = 1024 * 1024;
    private final OkHttpClient client;
    private final HttpUrl endpoint;

    public AppUpdates() {
        this(new OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS)
                .callTimeout(20,TimeUnit.SECONDS).followRedirects(false).build(),HttpUrl.get(API));
    }
    AppUpdates(OkHttpClient client,HttpUrl endpoint) { this.client=client; this.endpoint=endpoint; }

    public record Release(String version,String pageUrl,String downloadUrl,long bytes) { }
    public static final class CheckFailure extends IOException {
        CheckFailure(String message) { super(message); }
    }

    public Release latest() throws IOException {
        Request request=new Request.Builder().url(endpoint).header("Accept","application/vnd.github+json")
                .header("User-Agent","Keepr-Android-Updater").header("X-GitHub-Api-Version","2022-11-28")
                .header("Cache-Control","no-cache").build();
        try(Response response=client.newCall(request).execute()) {
            if(response.code()==404) throw new CheckFailure("No published release is available yet. Try again later.");
            if(response.code()==403 || response.code()==429) throw new CheckFailure("GitHub is limiting update checks. Try again later or open GitHub releases.");
            if(!response.isSuccessful()) throw new CheckFailure("GitHub could not check for updates right now. Please try again later.");
            ResponseBody body=response.body();
            if(body==null || body.contentLength()>MAX_RESPONSE) throw new CheckFailure("The release information could not be read. Open GitHub releases instead.");
            // Bound chunked responses too, rather than trusting Content-Length.
            if(body.source().request(MAX_RESPONSE+1L)) throw new CheckFailure("The release information could not be read. Open GitHub releases instead.");
            return parse(body.string());
        } catch(CheckFailure failure) { throw failure; }
        catch(IOException error) { throw new CheckFailure("Could not reach GitHub. Check your internet connection and try again."); }
    }

    static Release parse(String json) throws CheckFailure {
        try {
            JSONObject release=new JSONObject(json);
            if(release.getBoolean("draft") || release.getBoolean("prerelease")) throw new JSONException("Not a stable release");
            String tag=release.getString("tag_name");
            String version=normalize(tag);
            String expectedName="Keepr-"+version+".apk";
            String expectedUrl=RELEASES+"/download/"+tag+"/"+expectedName;
            String download=""; long size=0;
            JSONArray assets=release.getJSONArray("assets");
            for(int index=0;index<assets.length();index++) {
                JSONObject asset=assets.getJSONObject(index);
                if(expectedName.equals(asset.optString("name")) && "uploaded".equals(asset.optString("state"))
                        && expectedUrl.equals(asset.optString("browser_download_url")) && asset.optLong("size",0)>0) {
                    download=expectedUrl; size=asset.getLong("size"); break;
                }
            }
            return new Release(version,RELEASES+"/tag/"+tag,download,size);
        } catch(JSONException | IllegalArgumentException error) {
            throw new CheckFailure("The release information could not be read. Open GitHub releases instead.");
        }
    }
    public static int compareVersions(String candidate,String installed) {
        String[] left=normalize(candidate).split("\\."),right=normalize(installed).split("\\.");
        for(int index=0;index<3;index++) {
            int result=Integer.compare(Integer.parseInt(left[index]),Integer.parseInt(right[index]));
            if(result!=0) return result;
        }
        return 0;
    }
    private static String normalize(String value) {
        if(value==null || !VERSION.matcher(value).matches()) throw new IllegalArgumentException("Unsupported version");
        return value.startsWith("v")?value.substring(1):value;
    }
}
