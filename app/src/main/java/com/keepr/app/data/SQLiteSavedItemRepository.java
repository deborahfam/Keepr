package com.keepr.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.keepr.app.domain.SavedItem;
import com.keepr.app.domain.SavedItemRepository;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** One serialized worker owns this repository. Each successful write emits a fresh DB snapshot. */
public final class SQLiteSavedItemRepository implements SavedItemRepository {
    private final VaultDatabase helper;
    private final Context context;
    private final List<Consumer<List<SavedItem>>> observers = new ArrayList<>();
    public SQLiteSavedItemRepository(Context context) {
        this.context = context.getApplicationContext(); helper = new VaultDatabase(context);
    }
    @Override public List<SavedItem> load() throws JSONException {
        SQLiteDatabase db = helper.getWritableDatabase();
        migrateLegacy(db);
        return query(db);
    }
    private List<SavedItem> query(SQLiteDatabase db) {
        List<SavedItem> items = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT id,url,title,description,site_name,image_url,price,currency,contact,notes,category_id,status,preview_state,preview_error,created_at,updated_at FROM SavedItem ORDER BY created_at DESC,id", null)) {
            while (c.moveToNext()) items.add(new SavedItem(c.getString(0), c.getString(1), c.getString(2), c.getString(3),
                    c.getString(4), c.getString(5), c.getString(6), c.getString(7), c.getString(8), c.getString(9),
                    c.getString(10), SavedItem.Status.valueOf(c.getString(11)), SavedItem.PreviewState.valueOf(c.getString(12)),
                    c.getString(13), c.getLong(14), c.getLong(15)));
        }
        return Collections.unmodifiableList(items);
    }
    private void migrateLegacy(SQLiteDatabase db) throws JSONException {
        android.content.SharedPreferences prefs = context.getSharedPreferences("keepr", Context.MODE_PRIVATE);
        if (prefs.getBoolean("sqlite_migrated", false)) return;
        JSONArray array = new JSONArray(prefs.getString("links", "[]"));
        db.beginTransaction();
        try {
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.getJSONObject(i);
                long now = System.currentTimeMillis() - i;
                SavedItem item = new SavedItem(json.getString("id"), json.getString("url"), json.getString("title"),
                        "", "", "", "", "", "", json.optString("notes"), json.getString("category"),
                        SavedItem.Status.ACTIVE, SavedItem.PreviewState.PENDING, "", now, now);
                try (Cursor existing = db.rawQuery("SELECT id FROM SavedItem WHERE id=?", new String[]{item.id()})) {
                    if (!existing.moveToFirst()) insert(db, item);
                }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        // Retain original JSON as a recovery copy; inserts are idempotent after process death.
        if (!prefs.edit().putBoolean("sqlite_migrated", true).commit()) throw new IllegalStateException("Could not record legacy migration");
    }
    @Override public void add(SavedItem item) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try { insert(db, item); db.setTransactionSuccessful(); }
        finally { db.endTransaction(); }
        emit();
    }
    private void insert(SQLiteDatabase db, SavedItem item) {
        if (item.categoryId() != null) {
            ContentValues category = new ContentValues(); category.put("id", item.categoryId());
            category.put("name", item.categoryId()); category.put("created_at", item.createdAt());
            db.insertWithOnConflict("Category", null, category, SQLiteDatabase.CONFLICT_IGNORE);
        }
        ContentValues v = new ContentValues();
        v.put("id", item.id()); v.put("url", item.url()); v.put("title", item.title());
        v.put("description", item.description()); v.put("site_name", item.siteName()); v.put("image_url", item.imageUrl());
        v.put("price", item.price()); v.put("currency", item.currency()); v.put("contact", item.contact());
        v.put("category_id", item.categoryId()); v.put("notes", item.notes()); v.put("status", item.status().name());
        v.put("preview_state", item.previewState().name()); v.put("preview_error", item.previewError());
        v.put("created_at", item.createdAt()); v.put("updated_at", item.updatedAt());
        db.insertOrThrow("SavedItem", null, v);
    }
    @Override public void delete(String id) {
        helper.getWritableDatabase().delete("SavedItem", "id=?", new String[]{id}); emit();
    }
    @Override public void observe(Consumer<List<SavedItem>> observer) { observers.add(observer); }
    private void emit() {
        List<SavedItem> snapshot = query(helper.getReadableDatabase());
        for (Consumer<List<SavedItem>> observer : observers) observer.accept(snapshot);
    }
    @Override public void close() { observers.clear(); helper.close(); }
}
