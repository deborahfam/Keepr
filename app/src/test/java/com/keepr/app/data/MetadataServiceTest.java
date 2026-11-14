package com.keepr.app.data;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class MetadataServiceTest {
    @Test public void followsRelativeRedirectAndParses() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", "/page"));
            server.enqueue(new MockResponse().addHeader("Content-Type", "text/html; charset=utf-8").setBody("<title>Deal</title>"));
            PreviewMetadata m = new MetadataService(new OkHttpClient()).fetch(server.url("/start").toString());
            assertEquals("Deal", m.title()); assertEquals(server.url("/page").toString(), m.resolvedUrl());
            assertEquals("Keepr/0.1 (link preview)", server.takeRequest().getHeader("User-Agent"));
        }
    }
    @Test public void rejectsLoopsHttpErrorsAndNonHtml() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            MetadataService service = new MetadataService(new OkHttpClient());
            server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", "/loop"));
            expectFailure(service, server.url("/loop").toString(), "loop");
            server.enqueue(new MockResponse().setResponseCode(403));
            expectFailure(service, server.url("/forbidden").toString(), "403");
            server.enqueue(new MockResponse().addHeader("Content-Type", "image/png").setBody("not HTML"));
            expectFailure(service, server.url("/image").toString(), "HTML");
        }
    }
    @Test public void capsBodySizeAndRejectsLocalNetworkInProduction() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().addHeader("Content-Type", "text/html").setBody("x".repeat(2 * 1024 * 1024 + 1)));
            expectFailure(new MetadataService(new OkHttpClient()), server.url("/").toString(), "2 MiB");
            expectFailure(new MetadataService(), server.url("/").toString(), "public address");
        }
    }
    @Test public void rejectsCredentialRedirectAndStopsAtRedirectLimit() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            MetadataService service = new MetadataService(new OkHttpClient());
            server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", "http://user:password@example.com/"));
            expectFailure(service, server.url("/credentials").toString(), "Unsafe redirect");
            for (int i = 0; i < 6; i++) server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", "/hop" + i));
            expectFailure(service, server.url("/start").toString(), "Too many redirects");
        }
    }
    @Test public void honorsHtmlCharsetWhenHttpCharsetIsAbsent() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            byte[] html = "<meta charset='ISO-8859-1'><title>Café</title>".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            server.enqueue(new MockResponse().addHeader("Content-Type", "text/html").setBody(new okio.Buffer().write(html)));
            assertEquals("Café", new MetadataService(new OkHttpClient()).fetch(server.url("/").toString()).title());
        }
    }
    @Test public void downloadsOnlyBoundedImagesAndChecksRedirects() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            MetadataService service=new MetadataService(new OkHttpClient());
            server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location","/photo"));
            server.enqueue(new MockResponse().addHeader("Content-Type","image/jpeg").setBody("image bytes"));
            assertArrayEquals("image bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8),service.fetchImage(server.url("/start").toString()));
            server.enqueue(new MockResponse().addHeader("Content-Type","text/html").setBody("login page"));
            try { service.fetchImage(server.url("/html").toString()); fail(); } catch (IOException expected) { assertTrue(expected.getMessage().contains("Not an image")); }
            server.enqueue(new MockResponse().addHeader("Content-Type","image/jpeg").setBody("x".repeat(MediaCache.MAX_BYTES+1)));
            try { service.fetchImage(server.url("/large").toString()); fail(); } catch (IOException expected) { assertTrue(expected.getMessage().contains("8 MiB")); }
            server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location","http://user:password@example.com/image"));
            try { service.fetchImage(server.url("/unsafe").toString()); fail(); } catch (IOException expected) { assertTrue(expected.getMessage().contains("Unsafe")); }
        }
    }
    private void expectFailure(MetadataService service, String url, String message) throws Exception {
        try { service.fetch(url); fail("Expected failure"); }
        catch (IOException expected) { assertTrue(expected.toString(), expected.getMessage().contains(message)); }
    }
}
