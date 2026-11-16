package com.keepr.app.data;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AppUpdatesTest {
    private String release(String version,String url) throws Exception {
        return new JSONObject().put("tag_name","v"+version).put("draft",false).put("prerelease",false)
                .put("assets",new JSONArray().put(new JSONObject().put("name","Keepr-"+version+".apk").put("state","uploaded")
                        .put("size",3200000).put("browser_download_url",url))).toString();
    }
    @Test public void comparesVersionsNumericallyWithoutOfferingDowngrades() {
        assertTrue(AppUpdates.compareVersions("v0.10.0","0.9.9")>0);
        assertEquals(0,AppUpdates.compareVersions("v0.6.0","0.6.0"));
        assertTrue(AppUpdates.compareVersions("0.5.0","0.6.0")<0);
        assertTrue(AppUpdates.compareVersions("1.0.0","0.99.99")>0);
    }
    @Test public void refusesAmbiguousOrPreviewVersionStrings() {
        for(String version:new String[]{"0.6.0-beta","01.2.3","../0.6.0","2147483648.0.0","0.6"}) {
            try { AppUpdates.compareVersions(version,"0.5.0"); fail(version); } catch(IllegalArgumentException expected) { }
        }
    }
    @Test public void readsOnlyOfficialMatchingApkAndBuildsReleaseLink() throws Exception {
        String url=AppUpdates.RELEASES+"/download/v0.6.0/Keepr-0.6.0.apk";
        var result=AppUpdates.parse(release("0.6.0",url));
        assertEquals(url,result.downloadUrl()); assertEquals("0.6.0",result.version());
        assertEquals(AppUpdates.RELEASES+"/tag/v0.6.0",result.pageUrl()); assertEquals(3200000,result.bytes());
    }
    @Test public void untrustedOrMissingAssetDoesNotProduceDownloadButton() throws Exception {
        for(String url:new String[]{"https://example.com/Keepr-0.6.0.apk","http://github.com/deborahfam/Keepr/releases/download/v0.6.0/Keepr-0.6.0.apk",AppUpdates.RELEASES+"/download/v0.5.0/Keepr-0.5.0.apk"})
            assertEquals("",AppUpdates.parse(release("0.6.0",url)).downloadUrl());
        JSONObject json=new JSONObject(release("0.6.0","")); json.put("assets",new JSONArray());
        assertEquals("",AppUpdates.parse(json.toString()).downloadUrl());
    }
    @Test public void rejectsDraftPreviewAndMalformedResponses() throws Exception {
        for(String key:new String[]{"draft","prerelease"}) {
            JSONObject json=new JSONObject(release("0.6.0","")); json.put(key,true);
            try { AppUpdates.parse(json.toString()); fail(); } catch(AppUpdates.CheckFailure expected) { }
        }
        try { AppUpdates.parse("not JSON"); fail(); } catch(AppUpdates.CheckFailure expected) { }
    }
    @Test public void checksPublicEndpointWithoutCredentials() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setBody(release("0.6.0",AppUpdates.RELEASES+"/download/v0.6.0/Keepr-0.6.0.apk")));
            AppUpdates service=new AppUpdates(new OkHttpClient(),server.url("/repos/deborahfam/Keepr/releases/latest"));
            assertEquals("0.6.0",service.latest().version()); RecordedRequest request=server.takeRequest();
            assertEquals("/repos/deborahfam/Keepr/releases/latest",request.getPath());
            assertNull(request.getHeader("Authorization")); assertEquals("no-cache",request.getHeader("Cache-Control"));
        }
    }
    @Test public void explainsUnavailableReleasesAndRateLimits() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            AppUpdates service=new AppUpdates(new OkHttpClient(),server.url("/latest"));
            for(int code:new int[]{404,403,429,503}) {
                server.enqueue(new MockResponse().setResponseCode(code));
                try { service.latest(); fail(); }
                catch(AppUpdates.CheckFailure error) { assertTrue(error.getMessage().contains(code==404?"No published":code==503?"could not":"limiting")); }
            }
        }
    }
    @Test public void boundsChunkedResponseSize() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setChunkedBody("x".repeat(1024*1024+1),4096));
            try { new AppUpdates(new OkHttpClient(),server.url("/latest")).latest(); fail(); }
            catch(AppUpdates.CheckFailure error) { assertTrue(error.getMessage().contains("could not be read")); }
        }
    }
}
