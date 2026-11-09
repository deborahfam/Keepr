package com.keepr.app.data;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keeps query strings (including signed URLs) intact; never guesses a scheme. */
public final class UrlExtractor {
    private static final int MAX_PAYLOAD = 65_536;
    private static final Pattern URL = Pattern.compile("https?://[^\\s\\p{Cc}<>\\\"“”]+", Pattern.CASE_INSENSITIVE);
    private UrlExtractor() {}
    public static List<String> extract(String text) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        if (text == null) return new ArrayList<>();
        Matcher matcher = URL.matcher(text.substring(0, Math.min(text.length(), MAX_PAYLOAD)));
        while (matcher.find() && urls.size() < 20) {
            String value = matcher.group().replace("&amp;", "&");
            while (!value.isEmpty()) {
                char last = value.charAt(value.length() - 1);
                if (".,;:!?'}’".indexOf(last) >= 0 || (last == ')' && unbalanced(value, '(', ')'))
                        || (last == ']' && unbalanced(value, '[', ']'))) value = value.substring(0, value.length() - 1);
                else break;
            }
            try { urls.add(normalize(value)); } catch (IllegalArgumentException ignored) { /* Skip malformed candidates. */ }
        }
        return new ArrayList<>(urls);
    }
    private static boolean unbalanced(String text, char open, char close) {
        return text.chars().filter(c -> c == close).count() > text.chars().filter(c -> c == open).count();
    }
    public static String normalize(String value) {
        if (value == null || value.length() > 8192) throw new IllegalArgumentException("Invalid web URL");
        URI uri;
        try { uri = URI.create(value.trim()); } catch (RuntimeException e) { throw new IllegalArgumentException("Invalid web URL", e); }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getPort() > 65535
                || uri.getPort() == 0) throw new IllegalArgumentException("Enter a valid public http:// or https:// link without credentials.");
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return scheme.toLowerCase(Locale.ROOT) + "://" + host + (uri.getPort() == -1 ? "" : ":" + uri.getPort())
                + (uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())
                + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment());
    }
}
