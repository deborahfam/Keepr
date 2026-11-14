package com.keepr.app.data;

import okhttp3.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Blocking service: call on a worker. Each redirect is validated; no cookies or credentials. */
public final class MetadataService {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private final OkHttpClient client;
    public MetadataService() {
        this(new OkHttpClient.Builder().dns(host -> {
            java.util.List<InetAddress> addresses = Dns.SYSTEM.lookup(host);
            for (InetAddress address : addresses) {
                byte[] bytes = address.getAddress();
                boolean reservedV4 = bytes.length == 4 && ((bytes[0] & 255) == 0 || (bytes[0] & 255) >= 224
                        || ((bytes[0] & 255) == 100 && (bytes[1] & 255) >= 64 && (bytes[1] & 255) <= 127));
                boolean localV6 = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress() || reservedV4 || localV6)
                    throw new UnknownHostException("Preview requests require a public address");
            }
            return addresses;
        }).build());
    }
    // Package-private seam for deterministic HTTP tests; production always uses public-only DNS.
    MetadataService(OkHttpClient client) {
        this.client = client.newBuilder().proxy(java.net.Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false)
                .connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS).retryOnConnectionFailure(false).build();
    }
    public byte[] fetchImage(String input) throws IOException {
        String url;
        try { url = UrlExtractor.normalize(input); } catch (IllegalArgumentException e) { throw new IOException("Invalid image URL", e); }
        Set<String> visited = new HashSet<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        for (int hop = 0; hop <= 5; hop++) {
            if (!visited.add(url)) throw new IOException("Image redirect loop");
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new IOException("Image request timed out");
            Call call = client.newCall(new Request.Builder().url(url).header("User-Agent", "Keepr/0.1 (link preview)")
                    .header("Accept", "image/*").build());
            call.timeout().timeout(Math.min(remaining, TimeUnit.SECONDS.toNanos(15)), TimeUnit.NANOSECONDS);
            try (Response response = call.execute()) {
                if (Arrays.asList(301,302,303,307,308).contains(response.code())) {
                    String location = response.header("Location");
                    HttpUrl next = location == null ? null : response.request().url().resolve(location);
                    if (next == null) throw new IOException("Invalid image redirect");
                    String destination;
                    try { destination = UrlExtractor.normalize(next.toString()); }
                    catch (IllegalArgumentException e) { throw new IOException("Unsafe image redirect",e); }
                    if (url.startsWith("https:") && destination.startsWith("http:")) throw new IOException("Insecure image redirect");
                    url = destination; continue;
                }
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) throw new IOException("Photo unavailable (HTTP " + response.code() + ")");
                if (body.contentType() == null || !"image".equals(body.contentType().type())) throw new IOException("Not an image response");
                if (body.contentLength() > MediaCache.MAX_BYTES) throw new IOException("Photo exceeds 8 MiB");
                return MediaCache.readBounded(body.byteStream(),MediaCache.MAX_BYTES);
            }
        }
        throw new IOException("Too many image redirects");
    }
    public PreviewMetadata fetch(String input) throws IOException {
        String url;
        try { url = UrlExtractor.normalize(input); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid preview URL", e); }
        Set<String> visited = new HashSet<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        for (int hop = 0; hop <= 5; hop++) {
            if (!visited.add(url)) throw new IOException("Redirect loop");
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new IOException("Preview request timed out");
            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "Keepr/0.1 (link preview)")
                    .header("Accept", "text/html, application/xhtml+xml")
                    .header("Accept-Language", "en").build();
            Call call = client.newCall(request);
            call.timeout().timeout(Math.min(remaining, TimeUnit.SECONDS.toNanos(15)), TimeUnit.NANOSECONDS);
            try (Response response = call.execute()) {
                if (Arrays.asList(301, 302, 303, 307, 308).contains(response.code())) {
                    String location = response.header("Location");
                    HttpUrl next = location == null ? null : response.request().url().resolve(location);
                    if (next == null) throw new IOException("Redirect missing a valid destination");
                    try {
                        String destination = UrlExtractor.normalize(next.toString());
                        if (url.startsWith("https:") && destination.startsWith("http:")) throw new IOException("Insecure redirect refused");
                        url = destination;
                    } catch (IllegalArgumentException e) { throw new IOException("Unsafe redirect", e); }
                    continue;
                }
                if (!response.isSuccessful()) throw new IOException("Preview unavailable (HTTP " + response.code() + ")");
                ResponseBody body = response.body();
                if (body == null) throw new IOException("Empty preview response");
                MediaType type = body.contentType();
                if (type == null || !(type.type().equals("text") && type.subtype().equals("html")
                        || type.toString().toLowerCase(java.util.Locale.ROOT).startsWith("application/xhtml+xml")))
                    throw new IOException("The shared link is not an HTML page");
                if (body.contentLength() > MAX_BYTES) throw new IOException("Preview page exceeds 2 MiB");
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                java.io.InputStream stream = body.byteStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = stream.read(chunk, 0, Math.min(chunk.length, MAX_BYTES + 1 - buffer.size()))) > 0) {
                    buffer.write(chunk, 0, read);
                    if (buffer.size() > MAX_BYTES) throw new IOException("Preview page exceeds 2 MiB");
                }
                byte[] bytes = buffer.toByteArray();
                if (bytes.length > MAX_BYTES) throw new IOException("Preview page exceeds 2 MiB");
                Charset charset = type.charset();
                org.jsoup.nodes.Document document = org.jsoup.Jsoup.parse(new java.io.ByteArrayInputStream(bytes), charset == null ? null : charset.name(), url);
                PreviewMetadata preview=new MetadataParser().parse(document,url);
                String host=java.net.URI.create(url).getHost().toLowerCase(java.util.Locale.ROOT);
                String title=preview.title().trim().toLowerCase(java.util.Locale.ROOT);
                boolean social=host.equals("facebook.com") || host.endsWith(".facebook.com") || host.equals("instagram.com") || host.endsWith(".instagram.com");
                String path=java.net.URI.create(url).getPath();
                if (social && (path.contains("/login") || path.contains("/accounts/login") || path.contains("/checkpoint")
                        || title.equals("facebook") || title.equals("instagram") || title.contains("log into facebook") || title.contains("login • instagram")))
                    throw new IOException("The source returned a login page instead of a public post");
                return preview;
            } catch (IllegalArgumentException e) { throw new IOException("Invalid preview response", e); }
        }
        throw new IOException("Too many redirects");
    }
}
