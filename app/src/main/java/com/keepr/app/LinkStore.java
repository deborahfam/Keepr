package com.keepr.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;

/** Small offline repository. Replace with a database when collections grow. */
final class LinkStore {
    private final SharedPreferences preferences;
    LinkStore(Context context) { preferences = context.getSharedPreferences("keepr", Context.MODE_PRIVATE); }
    List<SavedLink> load() throws JSONException {
        JSONArray array = new JSONArray(preferences.getString("links", "[]"));
        List<SavedLink> links = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) links.add(SavedLink.fromJson(array.getJSONObject(i)));
        return links;
    }
    void save(List<SavedLink> links) {
        JSONArray array = new JSONArray();
        try { for (SavedLink link : links) array.put(link.toJson()); }
        catch (JSONException e) { throw new IllegalStateException("Could not encode saved links", e); }
        preferences.edit().putString("links", array.toString()).apply();
    }
}
