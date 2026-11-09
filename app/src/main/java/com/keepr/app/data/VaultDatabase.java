package com.keepr.app.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Versioned relational schema. Category and tag membership are separate concerns. */
public final class VaultDatabase extends SQLiteOpenHelper {
    public VaultDatabase(Context context) { super(context.getApplicationContext(), "keepr.db", null, 1); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE Category (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL UNIQUE COLLATE NOCASE, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE SavedItem (id TEXT PRIMARY KEY NOT NULL, url TEXT NOT NULL, title TEXT NOT NULL DEFAULT '', description TEXT NOT NULL DEFAULT '', site_name TEXT NOT NULL DEFAULT '', image_url TEXT NOT NULL DEFAULT '', price TEXT NOT NULL DEFAULT '', currency TEXT NOT NULL DEFAULT '', contact TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', category_id TEXT REFERENCES Category(id) ON DELETE SET NULL, status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','REACHED_OUT','PURCHASED','ARCHIVED')), preview_state TEXT NOT NULL DEFAULT 'PENDING' CHECK(preview_state IN ('PENDING','READY','FAILED')), preview_error TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX saved_item_feed ON SavedItem(status, category_id, created_at DESC)");
        db.execSQL("CREATE INDEX saved_item_url ON SavedItem(url)");
        db.execSQL("CREATE TABLE CachedMedia (id TEXT PRIMARY KEY NOT NULL, saved_item_id TEXT NOT NULL REFERENCES SavedItem(id) ON DELETE CASCADE, source_url TEXT NOT NULL, relative_path TEXT NOT NULL UNIQUE, mime_type TEXT NOT NULL, byte_count INTEGER NOT NULL CHECK(byte_count >= 0), sha256 TEXT NOT NULL, cached_at INTEGER NOT NULL, UNIQUE(saved_item_id, source_url))");
        db.execSQL("CREATE TABLE Tag (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL UNIQUE COLLATE NOCASE)");
        db.execSQL("CREATE TABLE SavedItemTag (saved_item_id TEXT NOT NULL REFERENCES SavedItem(id) ON DELETE CASCADE, tag_id TEXT NOT NULL REFERENCES Tag(id) ON DELETE CASCADE, PRIMARY KEY(saved_item_id, tag_id))");
        db.execSQL("CREATE INDEX item_tag_tag ON SavedItemTag(tag_id)");
        String[] defaults = {"Real Estate", "Electronics", "Vehicles", "Jobs", "Marketplace", "Rentals", "Social", "Other"};
        for (String name : defaults) db.execSQL("INSERT INTO Category VALUES (?, ?, ?)", new Object[]{name, name, System.currentTimeMillis()});
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("A non-destructive migration is required for version " + newVersion);
    }
}
