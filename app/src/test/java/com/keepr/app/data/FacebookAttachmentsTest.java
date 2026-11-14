package com.keepr.app.data;

import org.junit.Test;
import org.junit.Assume;
import static org.junit.Assert.*;
import java.nio.file.*;

public class FacebookAttachmentsTest {
    @Test public void extractsOnlyTheTargetStoryAndKeepsTheReportedAlbumCount() {
        String html="<meta property='og:url' content='https://www.facebook.com/groups/1/posts/100/'><meta property='og:image' content='https://cdn.example/preview.jpg'>"
                +"<script type='application/json'>{\"data\":[{\"__typename\":\"Story\",\"post_id\":\"100\",\"attachments\":[{\"styles\":{\"attachment\":{\"all_subattachments\":{\"count\":36,\"nodes\":["
                +"{\"media\":{\"__typename\":\"Photo\",\"id\":\"a\",\"image\":{\"uri\":\"https://cdn.example/small.jpg\"},\"viewer_image\":{\"uri\":\"https://cdn.example/full.jpg\"}}},"
                +"{\"media\":{\"__typename\":\"Photo\",\"id\":\"b\",\"image\":{\"uri\":\"https://cdn.example/two.jpg\"}}}]}}}}]},"
                +"{\"__typename\":\"Story\",\"post_id\":\"999\",\"attachments\":[{\"media\":{\"__typename\":\"Photo\",\"id\":\"other\",\"image\":{\"uri\":\"https://cdn.example/unrelated.jpg\"}}}]}]}</script>";
        PreviewMetadata preview=new MetadataParser().parse(html,"https://www.facebook.com/share/p/example/");
        assertEquals(36,preview.expectedPhotoCount());
        assertEquals(java.util.List.of("https://cdn.example/full.jpg","https://cdn.example/two.jpg"),preview.imageUrls());
    }
    @Test public void validatesTheCapturedPublicFacebookResponse() throws Exception {
        String fixture=System.getenv("KEEPR_PUBLIC_POST_FIXTURE"); Assume.assumeNotNull(fixture);
        PreviewMetadata preview=new MetadataParser().parse(new String(Files.readAllBytes(Paths.get(fixture)),java.nio.charset.StandardCharsets.UTF_8),"https://www.facebook.com/share/p/18Z2fbD1bG/");
        assertEquals(36,preview.expectedPhotoCount()); assertEquals(5,preview.imageUrls().size());
    }
}
