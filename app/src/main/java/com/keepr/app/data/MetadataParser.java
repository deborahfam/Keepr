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
        String image = first(meta, "og:image:secure_url", "og:image", "twitter:image", "image");
        try { image = UrlExtractor.normalize(java.net.URI.create(url).resolve(image).toString()); }
        catch (IllegalArgumentException e) { image = ""; }
        if (first(meta, "og:image:secure_url", "og:image", "twitter:image", "image").isEmpty()) image = "";
        String title = first(meta, "og:title", "twitter:title", "title");
        if (title.isEmpty()) title = doc.title();
        return new PreviewMetadata(url, limit(title, 1000), limit(first(meta, "og:description", "description", "twitter:description"), 10000),
                limit(first(meta, "og:site_name", "application-name"), 500), image,
                limit(first(meta, "product:price:amount", "og:price:amount", "price"), 100),
                limit(first(meta, "product:price:currency", "og:price:currency", "pricecurrency"), 20),
                limit(first(meta, "contact", "contact:email", "og:email", "email", "telephone"), 1000));
    }
    private static String first(Map<String, String> values, String... keys) {
        for (String key : keys) if (values.containsKey(key)) return values.get(key);
        return "";
    }
    private static String limit(String value, int length) { return value.substring(0, Math.min(length, value.length())); }
}
