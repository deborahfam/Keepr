package com.keepr.app.data;

import org.json.*;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.net.URI;
import java.util.LinkedHashSet;

/** Only structured publication fields are read; unrelated page images are not scraped. */
final class StructuredPost {
    String title="",text="",price="",currency="",location="",productType="",period="";
    com.keepr.app.domain.ContactDetails contacts=com.keepr.app.domain.ContactDetails.EMPTY;
    final LinkedHashSet<String> images=new LinkedHashSet<>();
    static StructuredPost parse(Document doc,String url) {
        StructuredPost post=new StructuredPost();
        java.util.List<JSONObject> candidates=new java.util.ArrayList<>();
        for (Element script:doc.select("script[type=application/ld+json]")) {
            try { collect(new JSONTokener(script.data()).nextValue(),candidates,0); }
            catch (JSONException | IllegalArgumentException ignored) { }
        }
        JSONObject selected=null; int score=-1; boolean tied=false;
        for(JSONObject node:candidates) {
            String type=node.optString("@type","");
            int next=type.matches("(?i).*(Product|Listing).*")?30:type.matches("(?i).*(Article|Posting).*")?20:10;
            String target=node.optString("url",node.optString("@id",""));
            try { if(!target.isEmpty() && URI.create(url).resolve(target).toString().split("#",2)[0].equals(url.split("#",2)[0])) next+=100; } catch(IllegalArgumentException ignored) { }
            if(next>score) { selected=node; score=next; tied=false; } else if(next==score) tied=true;
        }
        if(selected!=null && !tied) try { post.visit(selected,url,0); } catch(JSONException ignored) { }
        return post;
    }
    private static void collect(Object value,java.util.List<JSONObject> result,int depth) {
        if(depth>10 || result.size()>100) return;
        if(value instanceof JSONArray array) { for(int i=0;i<array.length();i++) collect(array.opt(i),result,depth+1); }
        else if(value instanceof JSONObject node) {
            if(node.optString("@type","").matches("(?i).*(Article|Posting|Product|Listing|WebPage).*") && !(node.optString("@type","").contains("WebPage") && node.opt("mainEntity") instanceof JSONObject)) result.add(node);
            collect(node.opt("@graph"),result,depth+1); collect(node.opt("mainEntity"),result,depth+1);
        }
    }
    private static String place(Object value) {
        if(value instanceof String text) return text.trim();
        if(value instanceof JSONObject node) {
            String address=place(node.opt("address")); if(!address.isEmpty()) return address;
            java.util.List<String> parts=new java.util.ArrayList<>();
            for(String key:new String[]{"streetAddress","addressLocality","addressRegion","addressCountry"}) {
                Object part=node.opt(key); String text=part instanceof JSONObject obj?obj.optString("name",""):part instanceof String?(String)part:"";
                if(!text.isEmpty()) parts.add(text);
            }
            return parts.isEmpty()?node.optString("name",""):String.join(", ",parts);
        }
        return "";
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
                // Multiple offers can be variants, deposits or ranges; do not pick one silently.
                if(offers instanceof JSONArray array && array.length()!=1) offer=null;
                if (offer!=null && price.isEmpty()) {
                    price=offer.optString("price",""); currency=offer.optString("priceCurrency","");
                    JSONObject spec=offer.optJSONObject("priceSpecification");
                    if(price.isEmpty() && spec!=null) { price=spec.optString("price",""); currency=spec.optString("priceCurrency",""); }
                    if(spec!=null) period=switch(spec.optString("unitCode","").toUpperCase(java.util.Locale.ROOT)) { case "MON" -> "month"; case "DAY" -> "day"; case "WEE" -> "week"; case "ANN" -> "year"; case "HUR" -> "hour"; default -> ""; };
                    location=place(offer.opt("availableAtOrFrom"));
                }
                JSONObject person=node.optJSONObject("seller");
                if(person==null && offer!=null) person=offer.optJSONObject("seller");
                if(person==null) person=node.optJSONObject("author");
                if(person!=null) {
                    JSONObject point=person.optJSONObject("contactPoint");
                    String phone=ContactParser.phone(person.optString("telephone",point==null?"":point.optString("telephone","")));
                    String email=ContactParser.email(person.optString("email",point==null?"":point.optString("email","")));
                    String wa=ContactParser.whatsappNumber(person.optString("url",""));
                    if(wa.isEmpty()) wa=ContactParser.whatsappNumber(person.optString("sameAs",""));
                    String name=person.optString("name",""); if(name.length()>100) name=name.substring(0,100);
                    contacts=new com.keepr.app.domain.ContactDetails(name,phone,email,wa,"Published contact data · review before contacting");
                }
                if(location.isEmpty()) location=place(node.opt("contentLocation"));
                if(location.isEmpty()) location=place(node.opt("location"));
                if(location.isEmpty()) location=place(node.opt("address"));
                Object category=node.opt("category");
                productType=category instanceof String?(String)category:category instanceof JSONObject?((JSONObject)category).optString("name",""):"";
            }

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
