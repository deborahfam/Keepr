package com.keepr.app.data;

import org.json.*;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.net.URI;
import java.util.LinkedHashSet;

/** Only structured publication fields are read; unrelated page images are not scraped. */
final class StructuredPost {
    String title="",text="",price="",currency="";
    final LinkedHashSet<String> images=new LinkedHashSet<>();
    static StructuredPost parse(Document doc,String url) {
        StructuredPost post=new StructuredPost();
        for (Element script:doc.select("script[type=application/ld+json]")) {
            try { post.visit(new JSONTokener(script.data()).nextValue(),url,0); }
            catch (JSONException | IllegalArgumentException ignored) { }
        }
        return post;
    }
    private void visit(Object value,String url,int depth) throws JSONException {
        if (depth>10) return;
        if (value instanceof JSONArray) {
            JSONArray array=(JSONArray)value;
            for (int i=0;i<array.length();i++) visit(array.opt(i),url,depth+1);
        } else if (value instanceof JSONObject) {
            JSONObject node=(JSONObject)value;
            String type=node.optString("@type","");
            boolean post=type.matches("(?i).*(Article|Posting|Product|Listing|WebPage).*");
            if (post) {
                if (title.isEmpty()) title=node.optString("headline",node.optString("name",""));
                String body=node.optString("articleBody",node.optString("text",node.optString("description","")));
                if (body.length()>text.length()) text=body;
                image(node.opt("image"),url,0);
                image(node.opt("thumbnailUrl"),url,0);
                Object offers=node.opt("offers");
                JSONObject offer=offers instanceof JSONObject ? (JSONObject)offers : offers instanceof JSONArray ? ((JSONArray)offers).optJSONObject(0) : null;
                if (offer!=null && price.isEmpty()) { price=offer.optString("price",""); currency=offer.optString("priceCurrency",""); }
            }
            for (String key:new String[]{"@graph","mainEntity","itemListElement","item"}) visit(node.opt(key),url,depth+1);
        }
    }
    private void image(Object value,String url,int depth) {
        if (depth>5) return;
        if (value instanceof JSONArray) {
            JSONArray array=(JSONArray)value; for (int i=0;i<array.length();i++) image(array.opt(i),url,depth+1);
        } else if (value instanceof JSONObject) {
            JSONObject object=(JSONObject)value; image(object.optString("contentUrl",object.optString("url","")),url,depth+1);
        } else if (value instanceof String && !((String)value).trim().isEmpty()) {
            try { images.add(UrlExtractor.normalize(URI.create(url).resolve(((String)value).trim()).toString())); }
            catch (IllegalArgumentException ignored) { }
        }
    }
}
