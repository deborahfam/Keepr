package com.keepr.app.data;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class MetadataParser {
    public PreviewMetadata parse(String html, String url) {
        return parse(Jsoup.parse(html, url), url);
    }
    PreviewMetadata parse(Document doc, String url) {
        Map<String, String> meta = new HashMap<>();
        for (Element element : doc.select("meta")) {
            String key = element.hasAttr("property") ? element.attr("property") : element.attr("name");
            if (key.isEmpty()) key = element.attr("itemprop");
            String content = element.attr("content").trim();
            if (!content.isEmpty()) meta.putIfAbsent(key.toLowerCase(Locale.ROOT), content);
        }
        java.util.LinkedHashSet<String> images = new java.util.LinkedHashSet<>();
        for (Element element : doc.select("meta")) {
            String key = (element.hasAttr("property") ? element.attr("property") : element.attr("name")).toLowerCase(Locale.ROOT);
            if (key.isEmpty()) key=element.attr("itemprop").toLowerCase(Locale.ROOT);
            if (java.util.Arrays.asList("og:image:secure_url", "og:image", "og:image:url", "twitter:image", "image").contains(key)) {
                try {
                    if (!element.attr("content").trim().isEmpty())
                        images.add(UrlExtractor.normalize(java.net.URI.create(url).resolve(element.attr("content").trim()).toString()));
                } catch (IllegalArgumentException ignored) { }
            }
        }
        StructuredPost structured=StructuredPost.parse(doc,url);
        images.addAll(structured.images);
        FacebookAttachments facebook=FacebookAttachments.parse(doc,url);
        if(!facebook.photos.isEmpty()) { images.clear(); images.addAll(facebook.photos.values()); }
        String image = first(meta, "og:image:secure_url", "og:image", "twitter:image", "image");
        try { if (!image.isEmpty()) image = UrlExtractor.normalize(java.net.URI.create(url).resolve(image).toString()); }
        catch (IllegalArgumentException e) { image = ""; }
        if (image.isEmpty() && !images.isEmpty()) image=images.iterator().next();
        String title = first(meta, "og:title", "twitter:title", "title");
        if (title.isEmpty()) title=structured.title;
        if (title.isEmpty()) title = doc.title();
        String description=first(meta,"og:description","description","twitter:description");
        if (structured.text.length()>description.length()) description=structured.text;
        String price=first(meta,"product:price:amount","og:price:amount","price");
        String currency=first(meta,"product:price:currency","og:price:currency","pricecurrency");
        return new PreviewMetadata(url, limit(title, 1000), limit(description,65536),
                limit(first(meta, "og:site_name", "application-name"), 500), image,
                limit(price.isEmpty()?structured.price:price,100),
                limit(currency.isEmpty()?structured.currency:currency,20),
                limit(first(meta, "contact", "contact:email", "og:email", "email", "telephone"), 1000), java.util.Collections.unmodifiableList(new java.util.ArrayList<>(images)),Math.max(facebook.expected,images.size()));
    }
    private static String first(Map<String, String> values, String... keys) {
        for (String key : keys) if (values.containsKey(key)) return values.get(key);
        return "";
    }
    private static String limit(String value, int length) { return value.substring(0, Math.min(length, value.length())); }
}
