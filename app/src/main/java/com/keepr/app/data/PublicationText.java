package com.keepr.app.data;

import java.util.regex.*;

/** Removes social hashtags without altering URL fragments, C#, or manually assigned tags. */
public final class PublicationText {
    private static final Pattern URL=Pattern.compile("(?i)https?://[^\\s<>]+");
    private static final Pattern HASHTAG=Pattern.compile("(?<![\\p{L}\\p{N}_])#[\\p{L}\\p{N}_]+");
    private PublicationText() {}
    public static String clean(String value) {
        StringBuilder out=new StringBuilder(); Matcher urls=URL.matcher(value); int end=0;
        while(urls.find()) { out.append(strip(value.substring(end,urls.start()))); out.append(urls.group()); end=urls.end(); }
        out.append(strip(value.substring(end)));
        return out.toString().replaceAll("[ \\t]+"," ").replaceAll("(?m)^ +| +$","").replaceAll("\\n{3,}","\n\n").trim();
    }
    private static String strip(String text) { return HASHTAG.matcher(text).replaceAll(""); }
}
