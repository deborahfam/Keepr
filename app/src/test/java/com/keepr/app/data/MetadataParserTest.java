package com.keepr.app.data;

import org.junit.Test;
import static org.junit.Assert.*;

public class MetadataParserTest {
    @Test public void collectsDistinctPhotosAndRejectsNonWebImageUrls() {
        PreviewMetadata preview = new MetadataParser().parse("<meta property='og:image' content='/one.jpg'><meta property='og:image' content='/two.jpg'><meta property='og:image' content='/one.jpg'><meta property='og:image' content='file:///private'>", "https://example.com/post");
        assertEquals(java.util.List.of("https://example.com/one.jpg","https://example.com/two.jpg"),preview.imageUrls());
    }
    @Test public void readsStructuredFullCaptionAndImageGallery() {
        PreviewMetadata preview = new MetadataParser().parse("<meta property='og:description' content='short'><script type='application/ld+json'>{\"@type\":\"SocialMediaPosting\",\"headline\":\"A post\",\"articleBody\":\"The complete caption in structured data\",\"image\":[\"/a.jpg\",{\"contentUrl\":\"/b.jpg\"}],\"offers\":{\"price\":125,\"priceCurrency\":\"USD\"}}</script>", "https://example.com/post");
        assertEquals("The complete caption in structured data",preview.description());
        assertEquals(2,preview.imageUrls().size()); assertEquals("125",preview.price());
        assertEquals("https://example.com/a.jpg",preview.imageUrl());
    }
    @Test public void keepsEveryPhotoBeyondEightAcrossMetadataAndStructuredData() {
        StringBuilder html=new StringBuilder();
        for(int i=0;i<15;i++) html.append("<meta property='og:image' content='/photo-").append(i).append(".jpg'>");
        org.json.JSONArray photos=new org.json.JSONArray(); for(int i=15;i<40;i++) photos.put("/photo-"+i+".jpg");
        html.append("<script type='application/ld+json'>{\"@type\":\"SocialMediaPosting\",\"image\":").append(photos).append("}</script>");
        PreviewMetadata preview=new MetadataParser().parse(html.toString(),"https://example.com/post");
        assertEquals(40,preview.imageUrls().size()); assertEquals("https://example.com/photo-39.jpg",preview.imageUrls().get(39));
    }
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
