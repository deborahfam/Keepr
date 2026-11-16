package com.keepr.app.data;

import org.junit.Test;
import static org.junit.Assert.*;

public class ListingFactsTest {
    @Test public void extractsSpanishMonthlyRentAndExplicitLocation() {
        ListingFacts f=ListingFacts.extract("Apartamento con terraza", "Precio: $1,850/mes\nUbicación: Miramar, San Juan");
        assertEquals("1850",f.price()); assertEquals("$",f.currency()); assertEquals("month",f.period());
        assertEquals("Miramar, San Juan",f.location()); assertEquals("Apartment",f.productType());
    }
    @Test public void supportsEuropeanPricesAndEnglishPickup() {
        ListingFacts f=ListingFacts.extract("Bicycle 1.250,50 EUR\nPickup in: London");
        assertEquals("1250.5",f.price()); assertEquals("EUR",f.currency()); assertEquals("London",f.location()); assertEquals("Bicycle",f.productType());
    }
    @Test public void currencyIsNotInferredFromLocationOrDollarSign() {
        assertEquals("$",ListingFacts.extract("$25\nUbicación: San Juan").currency());
        assertEquals("USD",ListingFacts.extract("US$25").currency());
        assertEquals("MXN",ListingFacts.extract("250 MXN").currency());
    }
    @Test public void ignoresPhoneNumbersViewsDatesAndModelNumbers() {
        ListingFacts f=ListingFacts.extract("403K views · 2.9K reactions. iPhone 15. Call 787-555-1234, 2026.");
        assertEquals("",f.price()); assertEquals("",f.location());
    }
    @Test public void ambiguousAmountsAndRangesAreNotAdvertisedAsOnePrice() {
        for(String text:new String[]{"$100 or $200", "$100-200", "desde $25", "From $100", "Deposit $500", "$20.99.99", "$40k"}) assertEquals(text,"",ListingFacts.extract(text).price());
    }
    @Test public void acceptsSentencePunctuation() { assertEquals("25",ListingFacts.extract("Price $25.").price()); }
    @Test public void repeatedPriceIsNotAConflict() { assertEquals("25",ListingFacts.extract("$25","$25").price()); }
    @Test public void conflictingTypesOrLocationsStayEmpty() {
        ListingFacts f=ListingFacts.extract("Sofa and laptop\nLocation: Madrid\nLocation: London");
        assertEquals("",f.productType()); assertEquals("",f.location());
    }
    @Test public void plainNumbersWithoutCurrencyStayEmpty() { assertEquals("",ListingFacts.extract("Precio 500, 3 habitaciones").price()); }
    @Test public void zeroIsValidAndInstallmentsAreNotCombined() {
        assertEquals("0",ListingFacts.extract("USD 0").price());
        assertEquals("",ListingFacts.extract("Total $500, payments $25/month").price());
    }
    @Test public void structuredListingUsesAddressAndCategory() {
        PreviewMetadata m=new MetadataParser().parse("<script type='application/ld+json'>{\"@type\":\"Product\",\"name\":\"Desk\",\"category\":\"Furniture\",\"offers\":{\"price\":\"125.00\",\"priceCurrency\":\"USD\",\"availableAtOrFrom\":{\"@type\":\"Place\",\"address\":{\"addressLocality\":\"San Juan\",\"addressRegion\":\"PR\"}}}}</script>","https://example.com/p/1");
        assertEquals("125.00",m.price()); assertEquals("San Juan, PR",m.location()); assertEquals("Furniture",m.productType());
    }
    @Test public void doesNotUseSellerAddressOrRecommendedProducts() {
        PreviewMetadata m=new MetadataParser().parse("<script type='application/ld+json'>{\"@type\":\"WebPage\",\"itemListElement\":[{\"@type\":\"Product\",\"offers\":{\"price\":900}}],\"seller\":{\"address\":\"Madrid\"}}</script>","https://example.com/p/1");
        assertEquals("",m.price()); assertEquals("",m.location());
    }
    @Test public void doesNotMergeTwoProductsInGraph() {
        PreviewMetadata m=new MetadataParser().parse("<script type='application/ld+json'>{\"@graph\":[{\"@type\":\"Product\",\"offers\":{\"price\":10}},{\"@type\":\"Product\",\"offers\":{\"price\":20}}]}</script>","https://example.com/p/1");
        assertEquals("",m.price());
    }
    @Test public void selectsProductMatchingPageUrl() {
        PreviewMetadata m=new MetadataParser().parse("<script type='application/ld+json'>{\"@graph\":[{\"@type\":\"Product\",\"url\":\"/other\",\"offers\":{\"price\":10}},{\"@type\":\"Product\",\"url\":\"/p/1\",\"offers\":{\"price\":20}}]}</script>","https://example.com/p/1");
        assertEquals("20",m.price());
    }
    @Test public void mainEntityWinsOverPageContainer() {
        PreviewMetadata m=new MetadataParser().parse("<script type='application/ld+json'>{\"@type\":\"WebPage\",\"url\":\"https://example.com/p/1\",\"mainEntity\":{\"@type\":\"Product\",\"offers\":{\"price\":20}}}</script>","https://example.com/p/1");
        assertEquals("20",m.price());
    }
    @Test public void extractsCaptionWhenStructuredPriceIsMissing() {
        PreviewMetadata m=new MetadataParser().parse("<meta property='og:title' content='Laptop USD 450'><meta property='og:description' content='Location: Austin'>","https://example.com/p/1");
        assertEquals("450",m.price()); assertEquals("Computer",m.productType()); assertEquals("Austin",m.location());
    }
}
