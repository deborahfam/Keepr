package com.keepr.app.data;

import org.junit.Test;
import static org.junit.Assert.*;

public class MetadataParserTest {
    private final MetadataParser parser = new MetadataParser();
    @Test public void prefersOgAndResolvesImagesWithEntities() {
        PreviewMetadata m = parser.parse("<title>Fallback</title><meta name='description' content='fallback'><meta property='og:title' content='Car &amp; truck'><meta property='og:description' content='OG description'><meta property='og:image' content='/photo.jpg'><meta property='product:price:amount' content='12500.00'><meta property='product:price:currency' content='USD'>", "https://example.com/listing");
        assertEquals("Car & truck", m.title()); assertEquals("OG description", m.description());
        assertEquals("https://example.com/photo.jpg", m.imageUrl()); assertEquals("12500.00", m.price()); assertEquals("USD", m.currency());
    }
    @Test public void fallsBackAndDoesNotInventContactOrPrice() {
        PreviewMetadata m = parser.parse("<title>Apartment</title><meta NAME='DESCRIPTION' content='Two bedrooms'><meta property='og:image' content='javascript:alert(1)'>", "https://example.com/");
        assertEquals("Apartment", m.title()); assertEquals("Two bedrooms", m.description());
        assertEquals("", m.imageUrl()); assertEquals("", m.price()); assertEquals("", m.contact());
    }
    @Test public void acceptsMixedAttributeOrderAndSkipsEmptyOg() {
        assertEquals("Fallback", parser.parse("<meta content='' property='og:title'><meta content='Fallback' name='twitter:title'>", "https://example.com/").title());
    }
}
