package com.keepr.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.keepr.app.domain.SavedItem;
import com.keepr.app.domain.CachedMedia;
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
        this(context,"keepr.db");
    }
    public SQLiteSavedItemRepository(Context context, String databaseName) {
        this.context = context.getApplicationContext(); helper = new VaultDatabase(context,databaseName);
    }
    @Override public List<SavedItem> load() throws JSONException {
        SQLiteDatabase db = helper.getWritableDatabase();
        migrateLegacy(db);
        return query(db);
    }
    private List<SavedItem> query(SQLiteDatabase db) { return query(db,null); }
    private List<SavedItem> query(SQLiteDatabase db,String itemId) {
        List<SavedItem> items = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT id,url,title,description,site_name,image_url,price,currency,contact,notes,category_id,status,preview_state,preview_error,created_at,updated_at,shared_text FROM SavedItem"+(itemId==null ? "" : " WHERE id=?")+" ORDER BY created_at DESC,id", itemId==null ? null : new String[]{itemId})) {
            while (c.moveToNext()) items.add(new SavedItem(c.getString(0), c.getString(1), c.getString(2), c.getString(3),
                    c.getString(4), c.getString(5), c.getString(6), c.getString(7), c.getString(8), c.getString(9),
                    c.getString(10), SavedItem.Status.valueOf(c.getString(11)), SavedItem.PreviewState.valueOf(c.getString(12)),
                    c.getString(13), c.getLong(14), c.getLong(15), c.getString(16), tags(db, c.getString(0)), media(db, c.getString(0))));
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
                        SavedItem.Status.ACTIVE, SavedItem.PreviewState.PENDING, "", now, now, "", java.util.Collections.emptyList(), java.util.Collections.emptyList());
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
        try {
            insert(db, item);
            for (String name : item.tags()) {
                ContentValues tag = new ContentValues(); tag.put("id", java.util.UUID.randomUUID().toString()); tag.put("name", name);
                db.insertWithOnConflict("Tag", null, tag, SQLiteDatabase.CONFLICT_IGNORE);
                db.execSQL("INSERT INTO SavedItemTag(saved_item_id,tag_id) SELECT ?,id FROM Tag WHERE name=? COLLATE NOCASE", new Object[]{item.id(), name});
            }
            for (CachedMedia media : item.media()) insertMedia(db, media);
            db.setTransactionSuccessful();
        }
        finally { db.endTransaction(); }
        emit();
    }
    private void insert(SQLiteDatabase db, SavedItem item) {
        String folderId = item.categoryId();
        if (folderId != null) {
            try (Cursor existing = db.rawQuery("SELECT id FROM Category WHERE name=? COLLATE NOCASE", new String[]{folderId})) {
                if (existing.moveToFirst()) folderId = existing.getString(0);
                else {
                    ContentValues category = new ContentValues(); category.put("id", folderId);
                    category.put("name", folderId); category.put("created_at", item.createdAt());
                    db.insertOrThrow("Category", null, category);
                }
            }
        }
        ContentValues v = new ContentValues();
        v.put("id", item.id()); v.put("url", item.url()); v.put("title", item.title());
        v.put("description", item.description()); v.put("site_name", item.siteName()); v.put("image_url", item.imageUrl());
        v.put("price", item.price()); v.put("currency", item.currency()); v.put("contact", item.contact());
        v.put("shared_text", item.sharedText()); v.put("category_id", folderId); v.put("notes", item.notes()); v.put("status", item.status().name());
        v.put("preview_state", item.previewState().name()); v.put("preview_error", item.previewError());
        v.put("created_at", item.createdAt()); v.put("updated_at", item.updatedAt());
        db.insertOrThrow("SavedItem", null, v);
    }
    SQLiteDatabase backupDatabase() { return helper.getWritableDatabase(); }
    public void edit(String id, String title, String notes, String folder, String tagText) {
        title=title.trim(); folder=folder.trim();
        if(title.isEmpty() || title.length()>1000) throw new IllegalArgumentException("Use a title from 1 to 1,000 characters.");
        if(notes.length()>65536) throw new IllegalArgumentException("Notes must be under 65,536 characters.");
        if(folder.isEmpty() || folder.length()>60 || folder.equalsIgnoreCase("All")) throw new IllegalArgumentException("Use a folder name from 1 to 60 characters, other than All.");
        List<String> normalized=com.keepr.app.domain.TagNames.parse(tagText);
        SQLiteDatabase db=helper.getWritableDatabase(); db.beginTransaction();
        try {
            String folderId=folder;
            try(Cursor c=db.rawQuery("SELECT id FROM Category WHERE name=? COLLATE NOCASE",new String[]{folder})) {
                if(c.moveToFirst()) folderId=c.getString(0);
                else { ContentValues v=new ContentValues(); v.put("id",folder); v.put("name",folder); v.put("created_at",System.currentTimeMillis()); db.insertOrThrow("Category",null,v); }
            }
            ContentValues v=new ContentValues(); v.put("title",title); v.put("title_edited",1); v.put("notes",notes); v.put("category_id",folderId); v.put("updated_at",System.currentTimeMillis());
            if(db.update("SavedItem",v,"id=?",new String[]{id})!=1) throw new IllegalStateException("This post no longer exists.");
            db.delete("SavedItemTag","saved_item_id=?",new String[]{id});
            for(String name:normalized) {
                ContentValues tag=new ContentValues(); tag.put("id",java.util.UUID.randomUUID().toString()); tag.put("name",name);
                db.insertWithOnConflict("Tag",null,tag,SQLiteDatabase.CONFLICT_IGNORE);
                db.execSQL("INSERT INTO SavedItemTag SELECT ?,id FROM Tag WHERE name=? COLLATE NOCASE",new Object[]{id,name});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        emit();
    }
    @Override public void delete(String id) {
        SQLiteDatabase db=helper.getWritableDatabase(); List<CachedMedia> files;
        db.beginTransaction();
        try { files=media(db,id); db.delete("SavedItem", "id=?", new String[]{id}); db.setTransactionSuccessful(); }
        finally { db.endTransaction(); }
        for (CachedMedia file : files) new java.io.File(context.getFilesDir(), file.relativePath()).delete();
        emit();
    }
    @Override public List<String> folders() {
        List<String> folders = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().rawQuery("SELECT name FROM Category ORDER BY name COLLATE NOCASE", null)) {
            while (c.moveToNext()) folders.add(c.getString(0));
        }
        return folders;
    }
    private List<String> tags(SQLiteDatabase db, String id) {
        List<String> tags = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT t.name FROM Tag t JOIN SavedItemTag it ON it.tag_id=t.id WHERE it.saved_item_id=? ORDER BY t.name", new String[]{id})) {
            while (c.moveToNext()) tags.add(c.getString(0));
        }
        return Collections.unmodifiableList(tags);
    }
    private List<CachedMedia> media(SQLiteDatabase db, String id) {
        List<CachedMedia> media = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT m.id,m.saved_item_id,m.source_url,m.relative_path,m.mime_type,m.byte_count,m.sha256,m.cached_at FROM CachedMedia m LEFT JOIN MediaDownload d ON d.saved_item_id=m.saved_item_id AND d.source_url=m.source_url WHERE m.saved_item_id=? ORDER BY CASE WHEN d.position IS NULL THEN 0 ELSE 1 END,d.position,m.cached_at,m.id", new String[]{id})) {
            while (c.moveToNext()) media.add(new CachedMedia(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getLong(5),c.getString(6),c.getLong(7)));
        }
        return Collections.unmodifiableList(media);
    }
    private void insertMedia(SQLiteDatabase db, CachedMedia media) {
        ContentValues v = new ContentValues(); v.put("id",media.id()); v.put("saved_item_id",media.savedItemId());
        v.put("source_url",media.sourceUrl()); v.put("relative_path",media.relativePath()); v.put("mime_type",media.mimeType());
        v.put("byte_count",media.byteCount()); v.put("sha256",media.sha256()); v.put("cached_at",media.cachedAt());
        db.insertOrThrow("CachedMedia",null,v);
    }
    public SavedItem find(String id) {
        for (SavedItem item : query(helper.getReadableDatabase(),id)) return item;
        return null;
    }
    public void attach(CachedMedia media) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            try (Cursor c = db.rawQuery("SELECT id FROM SavedItem WHERE id=?", new String[]{media.savedItemId()})) {
                if (!c.moveToFirst()) { new java.io.File(context.getFilesDir(),media.relativePath()).delete(); return; }
            }
            insertMedia(db, media);
            db.execSQL("UPDATE MediaDownload SET state='DOWNLOADED',error='' WHERE saved_item_id=? AND source_url=?",new Object[]{media.savedItemId(),media.sourceUrl()});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public void applyPreview(String id, PreviewMetadata preview) {
        SavedItem item = find(id); if (item == null) return;
        ContentValues v = new ContentValues();
        String host = android.net.Uri.parse(item.url()).getHost();
        if ((item.title().equals(host) || item.title().equals("Shared post")) && !preview.title().isEmpty()) {
            ContentValues title=new ContentValues(); title.put("title",preview.title());
            helper.getWritableDatabase().update("SavedItem",title,"id=? AND title_edited=0 AND title=?",new String[]{id,item.title()});
        }
        if (!preview.description().isEmpty() && (item.description().isEmpty() || item.description().equals(item.sharedText()))) v.put("description", preview.description());
        v.put("expected_photos",preview.expectedPhotoCount()); v.put("image_url",preview.imageUrl()); v.put("site_name",preview.siteName());
        v.put("price",preview.price()); v.put("currency",preview.currency()); v.put("contact",preview.contact());
        v.put("updated_at",System.currentTimeMillis());
        helper.getWritableDatabase().update("SavedItem",v,"id=?",new String[]{id});
    }
    public void previewState(String id, SavedItem.PreviewState state, String message) {
        ContentValues v = new ContentValues(); v.put("preview_state",state.name()); v.put("preview_error",message);
        v.put("updated_at",System.currentTimeMillis());
        helper.getWritableDatabase().update("SavedItem",v,"id=?",new String[]{id});
    }
    public record PhotoProgress(int saved,int total,int pending,int failed,int unexposed) {}
    public PhotoProgress photoProgress(String id) {
        SQLiteDatabase db=helper.getReadableDatabase(); int saved=0,pending=0,failed=0;
        try (Cursor c=db.rawQuery("SELECT COUNT(*) FROM CachedMedia WHERE saved_item_id=?",new String[]{id})) { if(c.moveToFirst()) saved=c.getInt(0); }
        try (Cursor c=db.rawQuery("SELECT state,COUNT(*) FROM MediaDownload WHERE saved_item_id=? AND state!='DOWNLOADED' GROUP BY state",new String[]{id})) {
            while(c.moveToNext()) if(c.getString(0).equals("FAILED")) failed=c.getInt(1); else pending=c.getInt(1);
        }
        int expected=0;
        try(Cursor c=db.rawQuery("SELECT expected_photos FROM SavedItem WHERE id=?",new String[]{id})) { if(c.moveToFirst()) expected=c.getInt(0); }
        int total=Math.max(expected,saved+pending+failed);
        return new PhotoProgress(saved,total,pending,failed,total-saved-pending-failed);
    }
    public boolean hasPhotoPlan(String id) {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT 1 FROM MediaDownload WHERE saved_item_id=? LIMIT 1",new String[]{id})) { return c.moveToFirst(); }
    }
    public void planPhotos(String id,List<String> urls) {
        SQLiteDatabase db=helper.getWritableDatabase(); db.beginTransaction();
        try {
            try(Cursor c=db.rawQuery("SELECT id FROM SavedItem WHERE id=?",new String[]{id})) { if(!c.moveToFirst()) return; }
            List<CachedMedia> cached=media(db,id);
            int position=0;
            for(String candidate:urls) {
                String url=candidate;
                for(CachedMedia old:cached) if(sameFacebookImage(candidate,old.sourceUrl())) { url=old.sourceUrl(); break; }
                db.execSQL("INSERT OR IGNORE INTO MediaDownload(saved_item_id,source_url,position,state) VALUES(?,?,?,CASE WHEN EXISTS(SELECT 1 FROM CachedMedia WHERE saved_item_id=? AND source_url=?) THEN 'DOWNLOADED' ELSE 'PENDING' END)",new Object[]{id,url,position++,id,url});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private boolean sameFacebookImage(String first,String second) {
        try {
            java.net.URI a=java.net.URI.create(first),b=java.net.URI.create(second);
            if(a.getHost()==null || b.getHost()==null || !a.getHost().endsWith(".fbcdn.net") || !b.getHost().endsWith(".fbcdn.net")) return false;
            String ap=a.getPath(),bp=b.getPath();
            return ap.substring(ap.lastIndexOf('/')+1).equals(bp.substring(bp.lastIndexOf('/')+1));
        } catch(IllegalArgumentException error) { return false; }
    }
    public boolean needsPhotoDiscovery(String id) {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT capture_version FROM SavedItem WHERE id=?",new String[]{id})) { return c.moveToFirst() && c.getInt(0)<4; }
    }
    public void finishPhotoDiscovery(String id) { helper.getWritableDatabase().execSQL("UPDATE SavedItem SET capture_version=4 WHERE id=?",new Object[]{id}); }
    public List<String> pendingPhotos(String id) {
        List<String> urls=new ArrayList<>();
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT source_url FROM MediaDownload WHERE saved_item_id=? AND state='PENDING' ORDER BY position LIMIT 12",new String[]{id})) {
            while(c.moveToNext()) urls.add(c.getString(0));
        }
        return urls;
    }
    public void failPhoto(String id,String url) {
        helper.getWritableDatabase().execSQL("UPDATE MediaDownload SET attempts=attempts+1,state=CASE WHEN attempts+1>=3 THEN 'FAILED' ELSE 'PENDING' END,error='Photo could not be downloaded' WHERE saved_item_id=? AND source_url=?",new Object[]{id,url});
    }
    public void retryPhotos(String id) {
        helper.getWritableDatabase().execSQL("UPDATE MediaDownload SET state='PENDING',attempts=0,error='' WHERE saved_item_id=? AND state='FAILED'",new Object[]{id});
        helper.getWritableDatabase().execSQL("UPDATE SavedItem SET capture_version=0 WHERE id=?",new Object[]{id});
        previewState(id,SavedItem.PreviewState.PENDING,"");
    }
    @Override public void observe(Consumer<List<SavedItem>> observer) { observers.add(observer); }
    private void emit() {
        List<SavedItem> snapshot = query(helper.getReadableDatabase());
        for (Consumer<List<SavedItem>> observer : observers) observer.accept(snapshot);
    }
    @Override public void close() { observers.clear(); helper.close(); }
}
