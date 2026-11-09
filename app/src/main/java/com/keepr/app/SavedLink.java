package com.keepr.app;

import org.json.JSONException;
import org.json.JSONObject;
import java.net.URI;
import java.util.UUID;

/** A locally saved listing. Only explicit web links are accepted. */
final class SavedLink {
    final String id, title, url, category, notes;

    SavedLink(String id, String title, String url, String category, String notes) {
        this.id = id; this.title = title; this.url = url;
        this.category = category; this.notes = notes;
    }

    static SavedLink create(String title, String url, String category, String notes) {
        String cleaned = url.trim();
        if (!isWebUrl(cleaned)) throw new IllegalArgumentException("Enter a valid http:// or https:// link.");
        return new SavedLink(UUID.randomUUID().toString(), title.trim().isEmpty() ? URI.create(cleaned).getHost() : title.trim(), cleaned, category, notes.trim());
    }

    static boolean isWebUrl(String value) {
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && !uri.getHost().isEmpty();
        } catch (IllegalArgumentException e) { return false; }
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("id", id).put("title", title).put("url", url).put("category", category).put("notes", notes);
    }

    static SavedLink fromJson(JSONObject json) throws JSONException {
        return new SavedLink(json.getString("id"), json.getString("title"), json.getString("url"), json.getString("category"), json.optString("notes"));
    }
}
