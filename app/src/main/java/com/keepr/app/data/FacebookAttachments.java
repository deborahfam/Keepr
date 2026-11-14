package com.keepr.app.data;

import org.json.*;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.net.URI;
import java.util.*;

/** Reads only post attachments from JSON delivered in the public HTML, never profile/feed images. */
final class FacebookAttachments {
    final LinkedHashMap<String,String> photos=new LinkedHashMap<>();
    int expected;
    static FacebookAttachments parse(Document document,String pageUrl) {
        FacebookAttachments result=new FacebookAttachments();
        String host=URI.create(pageUrl).getHost();
        if(host==null || !(host.equalsIgnoreCase("facebook.com") || host.toLowerCase(Locale.ROOT).endsWith(".facebook.com"))) return result;
        String canonical=document.select("meta[property=og:url]").attr("content");
        java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("/posts/([0-9]+)").matcher(canonical.isEmpty()?pageUrl:canonical);
        String postId=matcher.find()?matcher.group(1):"";
        for(Element script:document.select("script[type=application/json]")) {
            String json=script.data().trim();
            if(!json.contains("all_subattachments") && !json.contains("attachments")) continue;
            try { result.scan(new JSONTokener(json).nextValue(),postId,0); } catch(JSONException ignored) { }
        }
        return result;
    }
    private void scan(Object value,String postId,int depth) throws JSONException {
        if(depth>60) return;
        if(value instanceof JSONArray) {
            JSONArray array=(JSONArray)value; for(int i=0;i<array.length();i++) scan(array.opt(i),postId,depth+1);
        } else if(value instanceof JSONObject) {
            JSONObject node=(JSONObject)value;
            if((!postId.isEmpty() && postId.equals(node.optString("post_id")))
                    || (postId.isEmpty() && "Story".equals(node.optString("__typename")))) {
                attachment(node.opt("attachments"),0);
                attachment(node.opt("comet_sections"),0);
            }
            Iterator<String> keys=node.keys(); while(keys.hasNext()) scan(node.opt(keys.next()),postId,depth+1);
        }
    }
    private void attachment(Object value,int depth) throws JSONException {
        if(depth>20) return;
        if(value instanceof JSONArray) {
            JSONArray array=(JSONArray)value; for(int i=0;i<array.length();i++) attachment(array.opt(i),depth+1);
        } else if(value instanceof JSONObject) {
            JSONObject node=(JSONObject)value;
            if(node.has("count") && node.opt("nodes") instanceof JSONArray) expected=Math.max(expected,node.optInt("count",0));
            JSONObject media=node.optJSONObject("media");
            if(media!=null && "Photo".equals(media.optString("__typename"))) {
                String url=image(media.optJSONObject("viewer_image"));
                if(url.isEmpty()) url=image(media.optJSONObject("image"));
                if(!url.isEmpty()) photos.putIfAbsent(media.optString("id",url),url);
            }
            for(String key:new String[]{"styles","attachment","all_subattachments","subattachments","nodes","edges","node","content","story"}) attachment(node.opt(key),depth+1);
        }
    }
    private String image(JSONObject image) {
        if(image==null) return "";
        String uri=image.optString("uri",image.optString("url",""));
        try { return UrlExtractor.normalize(uri); } catch(IllegalArgumentException e) { return ""; }
    }
}
