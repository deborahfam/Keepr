package com.keepr.app.data;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.keepr.app.domain.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class TrashTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private SavedItem item(String id) { return new SavedItem(id,"https://example.com/"+id,"Title","Caption","","","","","","Notes","Other",SavedItem.Status.PURCHASED,SavedItem.PreviewState.READY,"",1,1,"Original",List.of("tag"),List.of()); }
    @Test public void trashRestoreExpiryAndLateAttachment() throws Exception {
        String name="trash-"+UUID.randomUUID()+".db";
        try(SQLiteSavedItemRepository repo=new SQLiteSavedItemRepository(context,name)) {
            repo.add(item("a")); repo.planPhotos("a",List.of("https://example.com/1","https://example.com/2"));
            assertEquals("Downloading · 0 of 2 photos saved",repo.find("a").captureSummary());
            for(int i=0;i<3;i++) { repo.failPhoto("a","https://example.com/1"); repo.failPhoto("a","https://example.com/2"); }
            assertEquals("Needs attention · 0 of 2 photos saved",repo.find("a").captureSummary());
            repo.trash(List.of("a")); assertNull(repo.find("a")); assertNull(repo.findDuplicate("https://example.com/a")); assertTrue(repo.load().isEmpty()); assertEquals(1,repo.trashContents().size());
            File late=new File(context.getFilesDir(),"media/"+UUID.randomUUID()+".jpg"); late.getParentFile().mkdirs(); try(FileOutputStream out=new FileOutputStream(late)) { out.write(1); }
            repo.attach(new CachedMedia("late","a","shared://late","media/"+late.getName(),"image/jpeg",1,"hash",1)); assertFalse(late.exists());
            repo.restoreTrash("a"); assertEquals("Notes",repo.find("a").notes()); assertEquals(SavedItem.Status.PURCHASED,repo.find("a").status()); assertEquals(2,repo.photoProgress("a").failed());
            repo.trash(List.of("a")); repo.backupDatabase().execSQL("UPDATE Trash SET expires_at=0"); repo.purgeExpired(); assertTrue(repo.trashContents().isEmpty()); assertFalse(repo.hasPhotoPlan("a"));
        } finally { context.deleteDatabase(name); }
    }
    @Test public void backupPreservesTrashAndOldBackupsStillRestore() throws Exception {
        String a="trash-source-"+UUID.randomUUID()+".db",b="trash-target-"+UUID.randomUUID()+".db",c="old-target-"+UUID.randomUUID()+".db";
        try(SQLiteSavedItemRepository source=new SQLiteSavedItemRepository(context,a); SQLiteSavedItemRepository target=new SQLiteSavedItemRepository(context,b); SQLiteSavedItemRepository old=new SQLiteSavedItemRepository(context,c)) {
            source.add(item("a")); source.trash(List.of("a")); VaultBackup backup=new VaultBackup(context); ByteArrayOutputStream bytes=new ByteArrayOutputStream(); backup.exportTo(source,bytes);
            backup.restoreFrom(target,new ByteArrayInputStream(bytes.toByteArray())); assertNull(target.find("a")); assertEquals(1,target.trashContents().size()); target.restoreTrash("a"); assertEquals("Caption",target.find("a").description());
            org.json.JSONObject manifest;
            try(java.util.zip.ZipInputStream input=new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { input.getNextEntry(); ByteArrayOutputStream json=new ByteArrayOutputStream(); byte[] block=new byte[4096]; int n; while((n=input.read(block))!=-1) json.write(block,0,n); manifest=new org.json.JSONObject(json.toString("UTF-8")); }
            manifest.put("version",1); manifest.remove("Trash"); ByteArrayOutputStream legacy=new ByteArrayOutputStream();
            try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(legacy)) { zip.putNextEntry(new java.util.zip.ZipEntry("manifest.json")); zip.write(manifest.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry(); }
            backup.restoreFrom(old,new ByteArrayInputStream(legacy.toByteArray())); assertNotNull(old.find("a"));
        } finally { context.deleteDatabase(a); context.deleteDatabase(b); context.deleteDatabase(c); }
    }
}
