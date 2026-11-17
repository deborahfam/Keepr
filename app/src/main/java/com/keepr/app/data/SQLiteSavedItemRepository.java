package com.keepr.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.keepr.app.domain.SavedItem;
import com.keepr.app.domain.CachedMedia;
import com.keepr.app.domain.ContactDetails;
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
        backfillListingFacts(db); backfillContacts(db); backfillLocations(db);
        return query(db);
    }
    private List<SavedItem> query(SQLiteDatabase db) { return query(db,null); }
    private List<SavedItem> query(SQLiteDatabase db,String itemId) {
        return queryRows(db,"id NOT IN (SELECT saved_item_id FROM Trash)"+(itemId==null?"":" AND id=?"),itemId==null?null:new String[]{itemId},"created_at DESC,id","");
    }
    private List<SavedItem> queryRows(SQLiteDatabase db,String where,String[] args,String order,String limit) {
        List<SavedItem> items=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT id,url,title,description,site_name,image_url,price,currency,contact,notes,(SELECT name FROM Category WHERE id=SavedItem.category_id),status,preview_state,preview_error,created_at,updated_at,shared_text,expected_photos,(SELECT COUNT(*) FROM MediaDownload WHERE saved_item_id=SavedItem.id AND state='PENDING'),(SELECT COUNT(*) FROM MediaDownload WHERE saved_item_id=SavedItem.id AND state='FAILED'),location,product_type,price_period,metadata_source,contact_name,contact_phone,contact_email,contact_whatsapp,contact_source FROM SavedItem WHERE "+where+" ORDER BY "+order+limit,args)) {
            while (c.moveToNext()) items.add(new SavedItem(c.getString(0), c.getString(1), c.getString(2), c.getString(3),
                    c.getString(4), c.getString(5), c.getString(6), c.getString(7), c.getString(8), c.getString(9),
                    c.getString(10), SavedItem.Status.valueOf(c.getString(11)), SavedItem.PreviewState.valueOf(c.getString(12)),
                    c.getString(13), c.getLong(14), c.getLong(15), c.getString(16), tags(db, c.getString(0)), media(db, c.getString(0)),c.getInt(17),c.getInt(18),c.getInt(19),c.getString(20),c.getString(21),c.getString(22),c.getString(23),new ContactDetails(c.getString(24),c.getString(25),c.getString(26),c.getString(27),c.getString(28))));
        }
        return Collections.unmodifiableList(items);
    }
    public void initialize() throws JSONException { SQLiteDatabase db=helper.getWritableDatabase(); migrateLegacy(db); backfillListingFacts(db); backfillContacts(db); backfillLocations(db); }
    public record Page(List<SavedItem> items,int total) {}
    public Page page(FeedQuery filter,int offset,int size) {
        if(offset<0 || size<1 || size>100) throw new IllegalArgumentException("Invalid page bounds");
        var sql=filter.sql(); SQLiteDatabase db=helper.getReadableDatabase(); db.beginTransaction();
        try {
            int total;
            try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM SavedItem WHERE "+sql.where(),sql.args())) { c.moveToFirst(); total=c.getInt(0); }
            List<SavedItem> items=queryRows(db,sql.where(),sql.args(),sql.order()," LIMIT "+size+" OFFSET "+offset);
            db.setTransactionSuccessful(); return new Page(items,total);
        } finally { db.endTransaction(); }
    }
    public List<String> matchingIds(FeedQuery filter) {
        var sql=filter.sql(); List<String> ids=new ArrayList<>();
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT id FROM SavedItem WHERE "+sql.where()+" ORDER BY "+sql.order(),sql.args())) { while(c.moveToNext()) ids.add(c.getString(0)); } return ids;
    }
    public List<String> pendingItemIds() {
        List<String> ids=new ArrayList<>();
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT id FROM SavedItem WHERE url!='' AND (preview_state='PENDING' OR capture_version<4) AND id NOT IN (SELECT saved_item_id FROM Trash)",null)) { while(c.moveToNext()) ids.add(c.getString(0)); } return ids;
    }
    public List<String> filterOptions(boolean tags) {
        List<String> values=new ArrayList<>();
        String sql=tags?"SELECT DISTINCT t.name FROM Tag t JOIN SavedItemTag it ON it.tag_id=t.id WHERE it.saved_item_id NOT IN (SELECT saved_item_id FROM Trash) ORDER BY t.name COLLATE NOCASE":"SELECT DISTINCT "+FeedQuery.SOURCE+" AS source FROM SavedItem WHERE id NOT IN (SELECT saved_item_id FROM Trash) ORDER BY source COLLATE NOCASE";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,null)) { while(c.moveToNext()) values.add(c.getString(0)); } return values;
    }
    private Runnable changeObserver;
    public void observeChanges(Runnable observer) { changeObserver=observer; }
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
                try (Cursor existing = db.rawQuery("SELECT id FROM SavedItem WHERE id=? AND id NOT IN (SELECT saved_item_id FROM Trash)", new String[]{item.id()})) {
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
    private void backfillListingFacts(SQLiteDatabase db) {
        db.beginTransaction();
        try(Cursor cursor=db.rawQuery("SELECT id,title,description,shared_text,price FROM SavedItem WHERE metadata_source='' AND metadata_edited=0",null)) {
            while(cursor.moveToNext()) {
                ListingFacts facts=ListingFacts.extract(cursor.getString(1),cursor.getString(2),cursor.getString(3));
                ContentValues values=new ContentValues();
                if(cursor.getString(4).isEmpty()) { values.put("price",facts.price()); values.put("currency",facts.currency()); values.put("price_period",facts.period()); }
                values.put("location",facts.location()); values.put("product_type",facts.productType()); values.put("metadata_source","Suggested from saved text; review these details.");
                db.update("SavedItem",values,"id=? AND metadata_edited=0",new String[]{cursor.getString(0)});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private void backfillLocations(SQLiteDatabase db) {
        db.beginTransaction();
        try(Cursor cursor=db.rawQuery("SELECT id,title,description,shared_text,location FROM SavedItem WHERE location_version=0 AND metadata_edited=0",null)) {
            while(cursor.moveToNext()) {
                ContentValues values=new ContentValues(); values.put("location_version",1);
                if(cursor.getString(4).isEmpty()) {
                    String location=ListingFacts.extract(cursor.getString(1),cursor.getString(2),cursor.getString(3)).location();
                    if(!location.isEmpty()) values.put("location",location);
                }
                db.update("SavedItem",values,"id=? AND metadata_edited=0",new String[]{cursor.getString(0)});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private static ContentValues contactValues(ContactDetails contacts,boolean onlyPresent) {
        ContentValues values=new ContentValues();
        if(!onlyPresent || !contacts.name().isEmpty()) values.put("contact_name",contacts.name());
        if(!onlyPresent || !contacts.phone().isEmpty()) values.put("contact_phone",contacts.phone());
        if(!onlyPresent || !contacts.email().isEmpty()) values.put("contact_email",contacts.email());
        if(!onlyPresent || !contacts.whatsapp().isEmpty()) values.put("contact_whatsapp",contacts.whatsapp());
        values.put("contact_source",contacts.source()); return values;
    }
    private void backfillContacts(SQLiteDatabase db) {
        db.beginTransaction();
        try(Cursor cursor=db.rawQuery("SELECT id,title,description,shared_text,contact FROM SavedItem WHERE contact_source='' AND contact_edited=0",null)) {
            while(cursor.moveToNext()) {
                ContactDetails contacts=ContactParser.saved(cursor.getString(1),cursor.getString(2),cursor.getString(3),cursor.getString(4));
                db.update("SavedItem",contactValues(contacts,false),"id=? AND contact_edited=0",new String[]{cursor.getString(0)});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public void editContacts(String id,ContactDetails contacts) {
        ContactDetails valid=ContactParser.validated(contacts.name(),contacts.phone(),contacts.email(),contacts.whatsapp());
        ContentValues values=contactValues(valid,false); values.put("contact_edited",1); values.put("updated_at",System.currentTimeMillis());
        // Clearing a channel explicitly must not resurrect the old untyped contact on capture.
        values.put("contact","");
        if(helper.getWritableDatabase().update("SavedItem",values,"id=?",new String[]{id})!=1) throw new IllegalStateException("This post no longer exists.");
        emit();
    }
    private void insert(SQLiteDatabase db, SavedItem item) {
        String folderId=item.categoryId()==null?null:ensureFolder(db,item.categoryId());
        ContentValues v = new ContentValues();
        v.putAll(contactValues(ContactParser.prefer(item.contacts(),ContactParser.saved(item.title(),item.description(),item.sharedText(),item.contact())),false));
        v.put("id", item.id()); v.put("url", item.url()); v.put("title", PublicationText.clean(item.title()));
        v.put("description", PublicationText.clean(item.description())); v.put("site_name", item.siteName()); v.put("image_url", item.imageUrl());
        ListingFacts facts=ListingFacts.extract(item.title(),item.description(),item.sharedText());
        v.put("price",item.price().isEmpty()?facts.price():item.price()); v.put("currency",item.price().isEmpty()?facts.currency():item.currency()); v.put("contact",item.contact());
        v.put("location_version",1);
        v.put("location",item.location().isEmpty()?facts.location():item.location());
        v.put("product_type",item.productType().isEmpty()?facts.productType():item.productType());
        v.put("price_period",item.pricePeriod().isEmpty() && item.price().isEmpty()?facts.period():item.pricePeriod());
        v.put("metadata_source",item.metadataSource().isEmpty()?"Suggested from saved text; review these details.":item.metadataSource());
        v.put("shared_text", PublicationText.clean(item.sharedText())); v.put("category_id", folderId); v.put("notes", item.notes()); v.put("status", item.status().name());
        v.put("preview_state", item.previewState().name()); v.put("preview_error", item.previewError());
        v.put("created_at", item.createdAt()); v.put("updated_at", item.updatedAt());
        db.insertOrThrow("SavedItem", null, v);
    }
    public void setStatus(String id, SavedItem.Status status) {
        java.util.Objects.requireNonNull(status);
        ContentValues values=new ContentValues(); values.put("status",status.name()); values.put("updated_at",System.currentTimeMillis());
        if(helper.getWritableDatabase().update("SavedItem",values,"id=?",new String[]{id})!=1) throw new IllegalStateException("This post no longer exists.");
        emit();
    }
    /** Compare normalized full URLs; retain queries and fragments to avoid conflating distinct posts. */
    public SavedItem findDuplicate(String url) {
        if(url==null || url.isEmpty()) return null;
        String normalized=UrlExtractor.normalize(url);
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT id,url FROM SavedItem WHERE id NOT IN (SELECT saved_item_id FROM Trash) ORDER BY created_at DESC,id",null)) {
            while(c.moveToNext()) {
                try { if(normalized.equals(UrlExtractor.normalize(c.getString(1)))) return find(c.getString(0)); }
                catch(IllegalArgumentException ignored) { /* Legacy text-only or invalid source. */ }
            }
        }
        return null;
    }
    SQLiteDatabase backupDatabase() { return helper.getWritableDatabase(); }
    /** Import independent copies atomically; never overwrite an existing publication. */
    public int importShared(PostShare.Preview preview,boolean keepExisting) throws Exception {
        SQLiteDatabase db=helper.getWritableDatabase(); List<CachedMedia> installed=new ArrayList<>(); int added=0; boolean committed=false;
        db.beginTransaction();
        try {
            for(PostShare.Post post:preview.posts()) {
                if(keepExisting && !post.url().isEmpty() && findDuplicate(post.url())!=null) continue;
                String id=java.util.UUID.randomUUID().toString(); long now=System.currentTimeMillis(); List<CachedMedia> media=new ArrayList<>();
                for(PostShare.Photo photo:post.photos()) {
                    try(java.io.InputStream input=new java.io.FileInputStream(new java.io.File(preview.directory(),photo.entry()))) {
                        CachedMedia saved=new MediaCache(context).store(id,"keepr-share:"+java.util.UUID.randomUUID(),MediaCache.readBounded(input,MediaCache.MAX_BYTES));
                        installed.add(saved); media.add(saved);
                    }
                }
                SavedItem item=new SavedItem(id,post.url(),post.title(),post.description(),"","",post.price(),post.currency(),"",post.notes(),"Other",
                    SavedItem.Status.ACTIVE,SavedItem.PreviewState.READY,"",now,now,"",List.of(),media,media.size(),0,0,post.location(),post.productType(),post.period(),"Shared copy · review these details",post.contacts());
                insert(db,item);
                // Keep exactly the reviewed fields, including intentionally absent contact information.
                ContentValues values=contactValues(post.contacts(),false); values.put("contact_source","Shared copy · review before contacting"); values.put("contact_edited",1);
                values.put("price",post.price()); values.put("currency",post.currency()); values.put("price_period",post.period()); values.put("location",post.location()); values.put("product_type",post.productType());
                values.put("metadata_edited",1); values.put("title_edited",1); values.put("capture_version",4); values.put("expected_photos",media.size());
                db.update("SavedItem",values,"id=?",new String[]{id});
                for(CachedMedia photo:media) insertMedia(db,photo);
                added++;
            }
            db.setTransactionSuccessful(); committed=true;
        } finally {
            try { db.endTransaction(); } catch(RuntimeException error) { committed=false; throw error; }
            finally { if(!committed) for(CachedMedia photo:installed) new java.io.File(context.getFilesDir(),photo.relativePath()).delete(); }
        }
        emit(); return added;
    }
    public void edit(String id, String title, String notes, String folder, String tagText) { edit(id,title,notes,folder,tagText,null); }
    public void edit(String id, String title, String notes, String folder, String tagText, ListingFacts facts) {
        if(facts!=null) {
            if(!facts.price().isEmpty() && !facts.price().matches("[0-9]{1,12}(?:\\.[0-9]{1,2})?")) throw new IllegalArgumentException("Use a price such as 1250.00, without currency symbols.");
            if(facts.currency().length()>20 || facts.location().length()>500 || facts.productType().length()>100 || facts.period().length()>30) throw new IllegalArgumentException("Listing detail is too long.");
        }
        title=title.trim(); folder=folder.trim();
        if(title.isEmpty() || title.length()>1000) throw new IllegalArgumentException("Use a title from 1 to 1,000 characters.");
        if(notes.length()>65536) throw new IllegalArgumentException("Notes must be under 65,536 characters.");
        if(folder.isEmpty() || folder.length()>60 || folder.equalsIgnoreCase("All")) throw new IllegalArgumentException("Use a folder name from 1 to 60 characters, other than All.");
        List<String> normalized=com.keepr.app.domain.TagNames.parse(tagText);
        SQLiteDatabase db=helper.getWritableDatabase(); db.beginTransaction();
        try {
            String folderId=ensureFolder(db,folder);
            ContentValues v=new ContentValues(); v.put("title",title); v.put("title_edited",1); v.put("notes",notes); v.put("category_id",folderId); v.put("updated_at",System.currentTimeMillis());
            if(facts!=null) {
                v.put("price",facts.price()); v.put("currency",facts.currency()); v.put("price_period",facts.period());
                v.put("location",facts.location()); v.put("product_type",facts.productType()); v.put("metadata_edited",1); v.put("metadata_source","Reviewed and edited by you");
            }
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
    private String ensureFolder(SQLiteDatabase db,String name) {
        name=validFolder(name);
        try(Cursor c=db.rawQuery("SELECT id FROM Category WHERE name=? COLLATE NOCASE",new String[]{name})) { if(c.moveToFirst()) return c.getString(0); }
        String id=java.util.UUID.randomUUID().toString(); ContentValues values=new ContentValues(); values.put("id",id); values.put("name",name); values.put("created_at",System.currentTimeMillis());
        db.insertOrThrow("Category",null,values); return id;
    }
    private String validFolder(String name) {
        name=name.trim();
        if(name.isEmpty() || name.length()>60 || name.equalsIgnoreCase("All")) throw new IllegalArgumentException("Use a folder name from 1 to 60 characters, other than All.");
        return name;
    }
    public enum BulkAction { MOVE, ADD_TAGS, ARCHIVE, DELETE }
    public int bulk(java.util.Collection<String> ids,BulkAction action,String value) {
        java.util.Set<String> unique=new java.util.LinkedHashSet<>(ids);
        if(unique.isEmpty()) throw new IllegalArgumentException("Select at least one post.");
        List<String> addedTags=action==BulkAction.ADD_TAGS?com.keepr.app.domain.TagNames.parse(value):Collections.emptyList();
        if(action==BulkAction.ADD_TAGS && addedTags.isEmpty()) throw new IllegalArgumentException("Enter at least one tag.");
        List<CachedMedia> removed=new ArrayList<>(); SQLiteDatabase db=helper.getWritableDatabase(); db.beginTransaction();
        try {
            String folder=action==BulkAction.MOVE?ensureFolder(db,value):null;
            for(String id:unique) {
                try(Cursor c=db.rawQuery("SELECT 1 FROM SavedItem WHERE id=?",new String[]{id})) { if(!c.moveToFirst()) throw new IllegalStateException("A selected post no longer exists. Refresh and select again."); }
                if(action==BulkAction.DELETE) { removed.addAll(media(db,id)); db.delete("SavedItem","id=?",new String[]{id}); continue; }
                ContentValues values=new ContentValues(); values.put("updated_at",System.currentTimeMillis());
                if(action==BulkAction.MOVE) values.put("category_id",folder);
                if(action==BulkAction.ARCHIVE) values.put("status",SavedItem.Status.ARCHIVED.name());
                if(action==BulkAction.ADD_TAGS) {
                    List<String> combined=new ArrayList<>(tags(db,id)); combined.addAll(addedTags);
                    com.keepr.app.domain.TagNames.parse(String.join(",",combined)); // Validate the union before committing any item.
                    for(String tagName:addedTags) {
                        ContentValues tag=new ContentValues(); tag.put("id",java.util.UUID.randomUUID().toString()); tag.put("name",tagName);
                        db.insertWithOnConflict("Tag",null,tag,SQLiteDatabase.CONFLICT_IGNORE);
                        db.execSQL("INSERT OR IGNORE INTO SavedItemTag SELECT ?,id FROM Tag WHERE name=? COLLATE NOCASE",new Object[]{id,tagName});
                    }
                }
                db.update("SavedItem",values,"id=?",new String[]{id});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        for(CachedMedia file:removed) new java.io.File(context.getFilesDir(),file.relativePath()).delete();
        emit(); return unique.size();
    }
    public void organizeFolder(String source,String destination,boolean merge) {
        destination=validFolder(destination); SQLiteDatabase db=helper.getWritableDatabase(); db.beginTransaction();
        try {
            String sourceId;
            try(Cursor c=db.rawQuery("SELECT id FROM Category WHERE name=? COLLATE NOCASE",new String[]{source})) { if(!c.moveToFirst()) throw new IllegalStateException("Folder no longer exists."); sourceId=c.getString(0); }
            String targetId=null;
            try(Cursor c=db.rawQuery("SELECT id FROM Category WHERE name=? COLLATE NOCASE",new String[]{destination})) { if(c.moveToFirst()) targetId=c.getString(0); }
            if(merge) {
                if(targetId==null || targetId.equals(sourceId)) throw new IllegalArgumentException("Choose a different existing destination folder.");
                db.execSQL("UPDATE SavedItem SET category_id=?,updated_at=? WHERE category_id=?",new Object[]{targetId,System.currentTimeMillis(),sourceId});
                db.delete("Category","id=?",new String[]{sourceId});
            } else {
                if(targetId!=null && !targetId.equals(sourceId)) throw new IllegalArgumentException("That folder already exists. Use Merge folders instead.");
                ContentValues values=new ContentValues(); values.put("name",destination); db.update("Category",values,"id=?",new String[]{sourceId});
                db.execSQL("UPDATE SavedItem SET updated_at=? WHERE category_id=?",new Object[]{System.currentTimeMillis(),sourceId});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        emit();
    }
    public record TrashedPost(String id,String title,long expiresAt) {}
    public List<TrashedPost> trashContents() {
        List<TrashedPost> result=new ArrayList<>();
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT s.id,s.title,t.expires_at FROM SavedItem s JOIN Trash t ON t.saved_item_id=s.id ORDER BY t.deleted_at DESC",null)) { while(c.moveToNext()) result.add(new TrashedPost(c.getString(0),c.getString(1),c.getLong(2))); }
        return result;
    }
    public int retentionDays() { return context.getSharedPreferences("keepr",Context.MODE_PRIVATE).getInt("trash_days",30); }
    public void setRetentionDays(int days) { if(days!=7 && days!=30 && days!=90) throw new IllegalArgumentException("Choose 7, 30, or 90 days."); context.getSharedPreferences("keepr",Context.MODE_PRIVATE).edit().putInt("trash_days",days).apply(); }
    public void trash(java.util.Collection<String> ids) {
        SQLiteDatabase db=helper.getWritableDatabase(); long now=System.currentTimeMillis(); db.beginTransaction();
        try { for(String id:new java.util.HashSet<>(ids)) {
            ContentValues values=new ContentValues(); values.put("saved_item_id",id); values.put("deleted_at",now); values.put("expires_at",now+java.util.concurrent.TimeUnit.DAYS.toMillis(retentionDays()));
            db.insertWithOnConflict("Trash",null,values,SQLiteDatabase.CONFLICT_IGNORE);
        } db.setTransactionSuccessful(); } finally { db.endTransaction(); }
        emit();
    }
    public void restoreTrash(String id) { helper.getWritableDatabase().delete("Trash","saved_item_id=?",new String[]{id}); emit(); }
    public void purgeExpired() {
        SQLiteDatabase db=helper.getWritableDatabase(); List<CachedMedia> files=new ArrayList<>(); db.beginTransaction();
        try {
            List<String> expired=new ArrayList<>();
            try(Cursor c=db.rawQuery("SELECT saved_item_id FROM Trash WHERE expires_at<=?",new String[]{Long.toString(System.currentTimeMillis())})) { while(c.moveToNext()) expired.add(c.getString(0)); }
            for(String id:expired) { files.addAll(media(db,id)); db.delete("SavedItem","id=?",new String[]{id}); }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        for(CachedMedia file:files) new java.io.File(context.getFilesDir(),file.relativePath()).delete();
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
            try (Cursor c = db.rawQuery("SELECT id FROM SavedItem WHERE id=? AND id NOT IN (SELECT saved_item_id FROM Trash)", new String[]{media.savedItemId()})) {
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
        if (!preview.description().isEmpty() && (item.description().isEmpty() || item.description().equals(item.sharedText()))) v.put("description", PublicationText.clean(preview.description()));
        v.put("expected_photos",preview.expectedPhotoCount()); v.put("image_url",preview.imageUrl()); v.put("site_name",preview.siteName());
        ContentValues facts=new ContentValues();
        if(!preview.price().isEmpty()) { facts.put("price",preview.price()); facts.put("currency",preview.currency()); facts.put("price_period",preview.pricePeriod()); }
        if(!preview.location().isEmpty()) facts.put("location",preview.location());
        if(!preview.productType().isEmpty()) facts.put("product_type",preview.productType());
        if(facts.size()>0) { facts.put("metadata_source",preview.metadataSource()); helper.getWritableDatabase().update("SavedItem",facts,"id=? AND metadata_edited=0",new String[]{id}); }
        ContactDetails contacts=ContactParser.prefer(preview.contacts(),ContactParser.saved(preview.title(),preview.description(),"",preview.contact()));
        if(!contacts.isEmpty()) {
            ContentValues contactUpdate=contactValues(contacts,true);
            helper.getWritableDatabase().update("SavedItem",contactUpdate,"id=? AND contact_edited=0",new String[]{id});
        }
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
            try(Cursor c=db.rawQuery("SELECT id FROM SavedItem WHERE id=? AND id NOT IN (SELECT saved_item_id FROM Trash)",new String[]{id})) { if(!c.moveToFirst()) return; }
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
        if(changeObserver!=null) changeObserver.run();
        if(observers.isEmpty()) return;
        List<SavedItem> snapshot = query(helper.getReadableDatabase());
        for (Consumer<List<SavedItem>> observer : observers) observer.accept(snapshot);
    }
    @Override public void close() { changeObserver=null; observers.clear(); helper.close(); }
}
