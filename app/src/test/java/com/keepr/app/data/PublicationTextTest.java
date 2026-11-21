package com.keepr.app.data;
import org.junit.Test;
import static org.junit.Assert.*;
public class PublicationTextTest {
    @Test public void removesSpanishAndUnicodeHashtags() { assertEquals("Casa en La Víbora",PublicationText.clean("Casa en La Víbora #casa #LaHabana #inmuebles_2026")); }
    @Test public void preservesLinksAndProgrammingNames() { assertEquals("C# book https://example.com/page#photos",PublicationText.clean("C# book https://example.com/page#photos #sale")); }
    @Test public void keepsParagraphsAndUsefulContent() { assertEquals("Casa\nContacto: Ana\n\nUSD 150",PublicationText.clean("Casa #venta\nContacto: Ana\n\n#habana\n\nUSD 150")); }
    @Test public void pageImportsAreCleaned() { var m=new MetadataParser().parse("<meta property='og:title' content='Casa #venta'><meta property='og:description' content='En La Víbora #Habana'>","https://example.com/post#main"); assertEquals("Casa",m.title()); assertEquals("En La Víbora",m.description()); assertEquals("https://example.com/post#main",m.resolvedUrl()); }
    @Test public void shorterPreviewCannotReplaceCompleteCaption() {
        String caption="Full publication\n"+"Detail 😀 ".repeat(10000)+"END";
        assertEquals(caption,PublicationText.complete(caption,"Short preview"));
        assertEquals(caption,PublicationText.complete("Short preview",caption));
    }
    @Test public void metadataPreservesCaptionBeyondOldLimit() {
        String caption="Description ".repeat(7000)+"FINAL PARAGRAPH";
        var metadata=new MetadataParser().parse("<meta property='og:description' content='"+caption+"'>","https://example.com/post");
        assertEquals(caption,metadata.description());
    }
}
