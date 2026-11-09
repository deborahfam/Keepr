package com.keepr.app.data;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class UrlExtractorTest {
    @Test public void extractsDistinctUrlsAndRemovesProsePunctuation() {
        assertEquals(List.of("https://example.com/item?id=42&ref=share", "https://example.org/wiki/A_(B)"),
                UrlExtractor.extract("Great deal! (https://EXAMPLE.com/item?id=42&amp;ref=share).\nhttps://example.org/wiki/A_(B) https://EXAMPLE.com/item?id=42&ref=share"));
    }
    @Test public void rejectsCredentialsAndInvalidSchemes() {
        assertTrue(UrlExtractor.extract("https://user:secret@example.com ftp://example.com https://").isEmpty());
        for (String url : List.of("file:///etc/passwd", "javascript:alert(1)", "https://example.com:70000", "https://example.com:0")) {
            try { UrlExtractor.normalize(url); fail(url); } catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void preservesSignedQueryAndHandlesNull() {
        assertTrue(UrlExtractor.extract(null).isEmpty());
        assertEquals("https://example.com/?token=a%2Fb%3D#section", UrlExtractor.normalize("HTTPS://EXAMPLE.COM?token=a%2Fb%3D#section"));
    }
}
