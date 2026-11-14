package com.keepr.app.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public final class TagNames {
    private TagNames() {}
    public static List<String> parse(String input) {
        LinkedHashMap<String, String> tags = new LinkedHashMap<>();
        if (input == null) return new ArrayList<>();
        for (String part : input.split(",")) {
            String tag = part.trim().replaceFirst("^#+", "").trim();
            if (tag.isEmpty()) continue;
            if (tag.length() > 40) throw new IllegalArgumentException("Keep each tag under 40 characters.");
            tags.putIfAbsent(tag.toLowerCase(Locale.ROOT), tag);
            if (tags.size() > 20) throw new IllegalArgumentException("Use up to 20 tags per post.");
        }
        return new ArrayList<>(tags.values());
    }
}
