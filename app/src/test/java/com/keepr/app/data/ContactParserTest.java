package com.keepr.app.data;
import org.junit.Test;
import static org.junit.Assert.*;
public class ContactParserTest {
    @Test public void extractsExplicitSpanishContactChannels() {
        var c=ContactParser.extract("Contacto: Ana Pérez\nTeléfono: +53 5555-1234\nCorreo: ana@example.com\nWhatsApp: +53 5555-5678");
        assertEquals("Ana Pérez",c.name()); assertEquals("+5355551234",c.phone()); assertEquals("ana@example.com",c.email()); assertEquals("+5355555678",c.whatsapp());
    }
    @Test public void phoneDoesNotImplyWhatsApp() { var c=ContactParser.extract("Call (787) 555-1234"); assertEquals("7875551234",c.phone()); assertEquals("",c.whatsapp()); }
    @Test public void leavesLocalWhatsAppWithoutInventingCountry() { var c=ContactParser.extract("WhatsApp: 55551234"); assertEquals("55551234",c.whatsapp()); assertEquals("",ContactParser.whatsappUrl(c.whatsapp())); }
    @Test public void acceptsOfficialLinksOnlyAndDropsMessages() {
        assertEquals("https://wa.me/5355551234",ContactParser.whatsappUrl("https://wa.me/5355551234?text=Hello"));
        assertEquals("+5355551234",ContactParser.whatsappNumber("https://api.whatsapp.com/send?phone=5355551234&text=Hello"));
        for(String url:new String[]{"https://wa.me.evil.example/5355551234","https://evil.example/wa.me/5355551234","https://someone@wa.me/5355551234","javascript:wa.me/5355551234","https://wa.me/05355551234"}) assertEquals(url,"",ContactParser.whatsappNumber(url));
    }
    @Test public void ambiguousContactsAreNotPickedArbitrarily() { var c=ContactParser.extract("Phone: +5355551234; Phone: +5355555678\na@example.com b@example.com"); assertEquals("",c.phone()); assertEquals("",c.email()); }
    @Test public void arbitraryNumbersAndDatesAreNotContacts() { for(String text:new String[]{"1234567890","Price $1850; 403000 views","10 de octubre de 2026","ID 987654321"}) assertTrue(text,ContactParser.extract(text).isEmpty()); }
    @Test public void legacyBarePhoneCanStillBeRead() { assertEquals("+5355551234",ContactParser.saved("","","","+53 5555 1234").phone()); }
    @Test public void validatesEditableFieldsAndRejectsDialCodes() {
        assertEquals("+5355551234",ContactParser.validated("Ana","+53 5555 1234","ana@example.com","https://wa.me/5355551234").whatsapp());
        assertEquals("",ContactParser.phone("*21*123456789#")); assertEquals("",ContactParser.email("ana@example.com?subject=x"));
    }
    @Test public void parsesOnlySellerOfSelectedProduct() {
        var m=new MetadataParser().parse("<script type='application/ld+json'>{\"@type\":\"Product\",\"seller\":{\"name\":\"Ana\",\"telephone\":\"+5355551234\",\"email\":\"ana@example.com\",\"url\":\"https://wa.me/5355555678\"}}</script><footer>Other contact: footer@example.com</footer>","https://example.com/post");
        assertEquals("Ana",m.contacts().name()); assertEquals("+5355551234",m.contacts().phone()); assertEquals("ana@example.com",m.contacts().email()); assertEquals("+5355555678",m.contacts().whatsapp());
    }
    @Test public void contactLabelDoesNotChangeProductType() { assertEquals("House",ListingFacts.extract("Casa en venta\nTeléfono: +53 5555 1234").productType()); }
}
