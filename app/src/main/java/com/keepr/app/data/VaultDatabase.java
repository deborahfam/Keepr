package com.keepr.app.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Versioned relational schema. Category and tag membership are separate concerns. */
public final class VaultDatabase extends SQLiteOpenHelper {
    public VaultDatabase(Context context) { this(context,"keepr.db"); }
    public VaultDatabase(Context context, String databaseName) { super(context.getApplicationContext(), databaseName, null, 7); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE Category (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL UNIQUE COLLATE NOCASE, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE SavedItem (id TEXT PRIMARY KEY NOT NULL, url TEXT NOT NULL, title TEXT NOT NULL DEFAULT '', title_edited INTEGER NOT NULL DEFAULT 0, shared_text TEXT NOT NULL DEFAULT '', expected_photos INTEGER NOT NULL DEFAULT 0, capture_version INTEGER NOT NULL DEFAULT 0, description TEXT NOT NULL DEFAULT '', site_name TEXT NOT NULL DEFAULT '', image_url TEXT NOT NULL DEFAULT '', price TEXT NOT NULL DEFAULT '', location TEXT NOT NULL DEFAULT '', product_type TEXT NOT NULL DEFAULT '', price_period TEXT NOT NULL DEFAULT '', metadata_source TEXT NOT NULL DEFAULT '', metadata_edited INTEGER NOT NULL DEFAULT 0, currency TEXT NOT NULL DEFAULT '', contact TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', category_id TEXT REFERENCES Category(id) ON DELETE SET NULL, status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','REACHED_OUT','PURCHASED','ARCHIVED')), preview_state TEXT NOT NULL DEFAULT 'PENDING' CHECK(preview_state IN ('PENDING','READY','FAILED')), preview_error TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX saved_item_feed ON SavedItem(status, category_id, created_at DESC)");
        db.execSQL("CREATE INDEX saved_item_url ON SavedItem(url)");
        db.execSQL("CREATE TABLE CachedMedia (id TEXT PRIMARY KEY NOT NULL, saved_item_id TEXT NOT NULL REFERENCES SavedItem(id) ON DELETE CASCADE, source_url TEXT NOT NULL, relative_path TEXT NOT NULL UNIQUE, mime_type TEXT NOT NULL, byte_count INTEGER NOT NULL CHECK(byte_count >= 0), sha256 TEXT NOT NULL, cached_at INTEGER NOT NULL, UNIQUE(saved_item_id, source_url))");
        db.execSQL("CREATE TABLE Tag (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL UNIQUE COLLATE NOCASE)");
        db.execSQL("CREATE TABLE SavedItemTag (saved_item_id TEXT NOT NULL REFERENCES SavedItem(id) ON DELETE CASCADE, tag_id TEXT NOT NULL REFERENCES Tag(id) ON DELETE CASCADE, PRIMARY KEY(saved_item_id, tag_id))");
        db.execSQL("CREATE INDEX item_tag_tag ON SavedItemTag(tag_id)");
        createPhotoQueue(db);
        createTrash(db);
        String[] defaults = {"Real Estate", "Electronics", "Vehicles", "Jobs", "Marketplace", "Rentals", "Social", "Other"};
        for (String name : defaults) db.execSQL("INSERT INTO Category VALUES (?, ?, ?)", new Object[]{name, name, System.currentTimeMillis()});
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 6) createTrash(db);
        if (oldVersion < 2) db.execSQL("ALTER TABLE SavedItem ADD COLUMN shared_text TEXT NOT NULL DEFAULT ''");
        if (oldVersion < 3) createPhotoQueue(db);
        if (oldVersion < 5) db.execSQL("ALTER TABLE SavedItem ADD COLUMN title_edited INTEGER NOT NULL DEFAULT 0");
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE SavedItem ADD COLUMN expected_photos INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE SavedItem ADD COLUMN capture_version INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE SavedItem ADD COLUMN location TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE SavedItem ADD COLUMN product_type TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE SavedItem ADD COLUMN price_period TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE SavedItem ADD COLUMN metadata_source TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE SavedItem ADD COLUMN metadata_edited INTEGER NOT NULL DEFAULT 0");
        }

    }
    private void createTrash(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE Trash (saved_item_id TEXT PRIMARY KEY NOT NULL REFERENCES SavedItem(id) ON DELETE CASCADE, deleted_at INTEGER NOT NULL, expires_at INTEGER NOT NULL)");
    }
    private void createPhotoQueue(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE MediaDownload (saved_item_id TEXT NOT NULL REFERENCES SavedItem(id) ON DELETE CASCADE, source_url TEXT NOT NULL, position INTEGER NOT NULL, state TEXT NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','DOWNLOADED','FAILED')), attempts INTEGER NOT NULL DEFAULT 0, error TEXT NOT NULL DEFAULT '', PRIMARY KEY(saved_item_id,source_url))");
        db.execSQL("CREATE INDEX media_download_pending ON MediaDownload(saved_item_id,state,position)");
    }
}
