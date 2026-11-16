package com.keepr.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Versioned portable archive. Restore validates everything before merging missing items. */
public final class VaultBackup {
    private static final String[] TABLES={"Category","SavedItem","Tag","SavedItemTag","CachedMedia","MediaDownload","Trash"};
    private static final int MANIFEST_LIMIT=32*1024*1024;
    private static final long ARCHIVE_LIMIT=4L*1024*1024*1024;
    private final Context context;
    public VaultBackup(Context context) { this.context=context.getApplicationContext(); }

    public void exportTo(SQLiteSavedItemRepository repository,OutputStream output) throws Exception {
        SQLiteDatabase db=repository.backupDatabase();
        db.beginTransaction(); // Keep rows and referenced files stable against concurrent capture/deletion.
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(output))) {
            JSONObject manifest=new JSONObject(); manifest.put("format","keepr"); manifest.put("version",3);
            for(String table:TABLES) {
                JSONArray rows=new JSONArray();
                try(Cursor c=db.query(table,null,null,null,null,null,null)) {
                    while(c.moveToNext()) {
                        JSONObject row=new JSONObject();
                        for(int i=0;i<c.getColumnCount();i++) row.put(c.getColumnName(i),c.isNull(i)?JSONObject.NULL:c.getType(i)==Cursor.FIELD_TYPE_INTEGER?c.getLong(i):c.getString(i));
                        rows.put(row);
                    }
                }
                manifest.put(table,rows);
            }
            byte[] bytes=manifest.toString().getBytes(StandardCharsets.UTF_8);
            if(bytes.length>MANIFEST_LIMIT) throw new IOException("Collection metadata exceeds the backup limit.");
            zip.putNextEntry(new ZipEntry("manifest.json")); zip.write(bytes); zip.closeEntry();
            JSONArray media=manifest.getJSONArray("CachedMedia"); long total=bytes.length;
            for(int i=0;i<media.length();i++) {
                JSONObject row=media.getJSONObject(i); String path=row.getString("relative_path"); checkPath(path);
                File file=new File(context.getFilesDir(),path);
                if(file.length()!=row.getLong("byte_count") || !hash(file).equals(row.getString("sha256"))) throw new IOException("A saved photo is missing or damaged. Backup was not completed.");
                total+=file.length(); if(total>ARCHIVE_LIMIT) throw new IOException("Backup exceeds 4 GiB.");
                zip.putNextEntry(new ZipEntry(path));
                try(InputStream input=new FileInputStream(file)) { copy(input,zip,MediaCache.MAX_BYTES); }
                zip.closeEntry();
            }
            zip.finish();
        } finally { db.endTransaction(); }
    }

    public record RestoreResult(int added,int skipped) {}
    public RestoreResult restoreFrom(SQLiteSavedItemRepository repository,InputStream input) throws Exception {
        File staging=new File(context.getFilesDir(),"restore-"+UUID.randomUUID());
        if(!staging.mkdirs()) throw new IOException("Could not prepare restore.");
        List<File> installed=new ArrayList<>(); boolean committed=false;
        try(SQLiteDatabase validated=SQLiteDatabase.create(null); ZipInputStream zip=new ZipInputStream(new BufferedInputStream(input))) {
            validated.setForeignKeyConstraintsEnabled(true);
            try(VaultDatabase schema=new VaultDatabase(context)) { schema.onCreate(validated); }
            validated.delete("Category",null,null);
            ZipEntry entry=zip.getNextEntry();
            if(entry==null || !entry.getName().equals("manifest.json")) throw new IOException("This is not a Keepr backup.");
            ByteArrayOutputStream manifestBytes=new ByteArrayOutputStream(); copy(zip,manifestBytes,MANIFEST_LIMIT);
            JSONObject manifest=new JSONObject(manifestBytes.toString(StandardCharsets.UTF_8.name()));
            if(!manifest.getString("format").equals("keepr") || (manifest.getInt("version")<1 || manifest.getInt("version")>3)) throw new IOException("Unsupported backup version.");
            if(manifest.getInt("version")==1) manifest.put("Trash",new JSONArray());
            // SQLite validates columns, required values, uniqueness, enums and foreign keys in isolation.
            for(String table:TABLES) {
                Set<String> columns=new HashSet<>();
                try(Cursor c=validated.query(table,null,null,null,null,null,null)) { columns.addAll(Arrays.asList(c.getColumnNames())); }
                JSONArray rows=manifest.getJSONArray(table);
                for(int i=0;i<rows.length();i++) {
                    JSONObject row=rows.getJSONObject(i);
                    if(table.equals("SavedItem") && manifest.getInt("version")<3) {
                        for(String key:new String[]{"location","product_type","price_period","metadata_source"}) row.put(key,"");
                        row.put("metadata_edited",0);
                    }
                    if(row.length()!=columns.size()) throw new IOException("Incomplete backup metadata.");
                    ContentValues values=new ContentValues();
                    for(String key:columns) {
                        Object value=row.get(key);
                        if(value==JSONObject.NULL) values.putNull(key);
                        else if(value instanceof Number) values.put(key,((Number)value).longValue());
                        else if(value instanceof String) values.put(key,(String)value);
                        else throw new IOException("Invalid backup value.");
                    }
                    validated.insertOrThrow(table,null,values);
                }
            }
            Map<String,JSONObject> expected=new HashMap<>();
            JSONArray media=manifest.getJSONArray("CachedMedia");
            for(int i=0;i<media.length();i++) {
                JSONObject row=media.getJSONObject(i); String path=row.getString("relative_path"); checkPath(path); expected.put(path,row);
            }
            Set<String> seen=new HashSet<>(); long total=manifestBytes.size();
            while((entry=zip.getNextEntry())!=null) {
                String path=entry.getName(); checkPath(path);
                if(!expected.containsKey(path) || !seen.add(path) || entry.isDirectory()) throw new IOException("Unexpected or duplicate backup file.");
                File file=new File(staging,new File(path).getName());
                try(OutputStream out=new FileOutputStream(file)) { total+=copy(zip,out,MediaCache.MAX_BYTES); }
                if(total>ARCHIVE_LIMIT) throw new IOException("Backup exceeds 4 GiB.");
                JSONObject row=expected.get(path);
                if(file.length()!=row.getLong("byte_count") || !hash(file).equals(row.getString("sha256"))) throw new IOException("Backup photo is damaged.");
            }
            if(seen.size()!=expected.size()) throw new IOException("Backup is missing photos.");
            SQLiteDatabase target=repository.backupDatabase(); int added=0,skipped=0;
            target.beginTransaction();
            try {
                Map<String,String> folders=mergeNames(target,manifest.getJSONArray("Category"),"Category");
                Map<String,String> tags=mergeNames(target,manifest.getJSONArray("Tag"),"Tag");
                Set<String> accepted=new HashSet<>();
                JSONArray items=manifest.getJSONArray("SavedItem");
                for(int i=0;i<items.length();i++) {
                    JSONObject row=items.getJSONObject(i); String id=row.getString("id");
                    try(Cursor c=target.rawQuery("SELECT 1 FROM SavedItem WHERE id=?",new String[]{id})) { if(c.moveToFirst()) { skipped++; continue; } }
                    ContentValues values=values(row);
                    if(!row.isNull("category_id")) values.put("category_id",folders.get(row.getString("category_id")));
                    target.insertOrThrow("SavedItem",null,values); accepted.add(id); added++;
                }
                for(String table:new String[]{"SavedItemTag","CachedMedia","MediaDownload","Trash"}) {
                    JSONArray rows=manifest.getJSONArray(table);
                    for(int i=0;i<rows.length();i++) {
                        JSONObject row=rows.getJSONObject(i); if(!accepted.contains(row.getString("saved_item_id"))) continue;
                        ContentValues values=values(row);
                        if(table.equals("SavedItemTag")) values.put("tag_id",tags.get(row.getString("tag_id")));
                        if(table.equals("CachedMedia")) {
                            String newId=UUID.randomUUID().toString(),path="media/"+newId+".jpg";
                            File destination=new File(context.getFilesDir(),path);
                            File parent=destination.getParentFile(); if(!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not create photo storage.");
                            File source=new File(staging,new File(row.getString("relative_path")).getName());
                            if(!source.renameTo(destination)) throw new IOException("Could not restore photo.");
                            installed.add(destination); values.put("id",newId); values.put("relative_path",path);
                        }
                        target.insertOrThrow(table,null,values);
                    }
                }
                target.setTransactionSuccessful();
            } finally { target.endTransaction(); }
            committed=true; return new RestoreResult(added,skipped);
        } finally {
            if(!committed) for(File file:installed) file.delete();
            File[] files=staging.listFiles(); if(files!=null) for(File file:files) file.delete(); staging.delete();
        }
    }
    private Map<String,String> mergeNames(SQLiteDatabase db,JSONArray rows,String table) throws Exception {
        Map<String,String> mapping=new HashMap<>();
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.getJSONObject(i); String oldId=row.getString("id"),name=row.getString("name"),id;
            try(Cursor c=db.query(table,new String[]{"id"},"name=? COLLATE NOCASE",new String[]{name},null,null,null)) {
                if(c.moveToFirst()) id=c.getString(0);
                else {
                    // Names are resolved separately from stable IDs.
                    id=UUID.randomUUID().toString();
                    ContentValues values=values(row); values.put("id",id); db.insertOrThrow(table,null,values);
                }
            }
            mapping.put(oldId,id);
        }
        return mapping;
    }
    private static ContentValues values(JSONObject row) throws JSONException {
        ContentValues values=new ContentValues(); Iterator<String> keys=row.keys();
        while(keys.hasNext()) { String key=keys.next(); Object value=row.get(key);
            if(value==JSONObject.NULL) values.putNull(key); else if(value instanceof Number) values.put(key,((Number)value).longValue()); else values.put(key,(String)value);
        } return values;
    }
    private static void checkPath(String path) throws IOException {
        if(!path.matches("media/[A-Za-z0-9-]+\\.jpg")) throw new IOException("Invalid photo path in backup.");
    }
    private static long copy(InputStream input,OutputStream output,long limit) throws IOException {
        byte[] buffer=new byte[8192]; long total=0; int count;
        while((count=input.read(buffer))!=-1) { total+=count; if(total>limit) throw new IOException("Backup entry exceeds its size limit."); output.write(buffer,0,count); }
        return total;
    }
    private static String hash(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream input=new FileInputStream(file)) { byte[] buffer=new byte[8192]; int count; while((count=input.read(buffer))!=-1) digest.update(buffer,0,count); }
        StringBuilder result=new StringBuilder(); for(byte value:digest.digest()) result.append(String.format(Locale.ROOT,"%02x",value&255)); return result.toString();
    }
}
