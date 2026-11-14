package com.keepr.app.data;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.keepr.app.domain.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class VaultBackupTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private SavedItem item(String id) {
        return new SavedItem(id,"https://example.com/post","Shared post","Caption","Example","","125","USD","seller","Notes","Custom folder",
            SavedItem.Status.REACHED_OUT,SavedItem.PreviewState.PENDING,"",100,200,"Original caption",List.of("wishlist"),List.of());
    }
    private CachedMedia photo(String id) throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888); bitmap.eraseColor(android.graphics.Color.BLUE);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes); bitmap.recycle();
        return new MediaCache(context).store(id,"https://example.com/photo.jpg",bytes.toByteArray());
    }
    @Test public void roundTripPreservesPhotosQueueMetadataAndEditsAndSkipsExisting() throws Exception {
        String source="source-"+UUID.randomUUID()+".db",target="target-"+UUID.randomUUID()+".db",id=UUID.randomUUID().toString();
        try(SQLiteSavedItemRepository from=new SQLiteSavedItemRepository(context,source); SQLiteSavedItemRepository to=new SQLiteSavedItemRepository(context,target)) {
            from.add(item(id)); CachedMedia photo=photo(id); from.attach(photo);
            from.planPhotos(id,List.of(photo.sourceUrl(),"https://example.com/missing.jpg")); from.failPhoto(id,"https://example.com/missing.jpg");
            from.edit(id,"My chosen title","Updated notes","New folder","home, favorites");
            from.applyPreview(id,new PreviewMetadata("https://example.com/post","Replacement","New caption","Example","","","",""));
            assertEquals("My chosen title",from.find(id).title());
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(); VaultBackup backup=new VaultBackup(context); backup.exportTo(from,bytes);
            VaultBackup.RestoreResult restored=backup.restoreFrom(to,new ByteArrayInputStream(bytes.toByteArray()));
            assertEquals(1,restored.added()); assertEquals(0,restored.skipped());
            SavedItem actual=to.find(id); assertEquals("My chosen title",actual.title()); assertEquals("Updated notes",actual.notes()); assertEquals("New folder",actual.categoryId());
            assertEquals(Set.of("home","favorites"),new HashSet<>(actual.tags())); assertEquals("Original caption",actual.sharedText());
            assertEquals("Caption",actual.description()); assertEquals(100,actual.createdAt()); assertEquals(SavedItem.Status.REACHED_OUT,actual.status());
            assertEquals(1,actual.media().size()); assertEquals(photo.sha256(),actual.media().get(0).sha256());
            assertTrue(new File(context.getFilesDir(),actual.media().get(0).relativePath()).isFile());
            assertEquals(1,to.photoProgress(id).pending()); assertEquals(1,to.photoProgress(id).saved());
            to.edit(id,"Newer local edit","Keep these notes","Local folder","local");
            restored=backup.restoreFrom(to,new ByteArrayInputStream(bytes.toByteArray()));
            assertEquals(0,restored.added()); assertEquals(1,restored.skipped()); assertEquals("Newer local edit",to.find(id).title());
            from.delete(id); to.delete(id);
        } finally { context.deleteDatabase(source); context.deleteDatabase(target); }
    }
    @Test public void missingPhotoAndTraversalRejectEntireRestore() throws Exception {
        String source="source-"+UUID.randomUUID()+".db",target="target-"+UUID.randomUUID()+".db",id=UUID.randomUUID().toString();
        try(SQLiteSavedItemRepository from=new SQLiteSavedItemRepository(context,source); SQLiteSavedItemRepository to=new SQLiteSavedItemRepository(context,target)) {
            from.add(item(id)); from.attach(photo(id)); to.add(item("existing"));
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(); VaultBackup backup=new VaultBackup(context); backup.exportTo(from,bytes);
            byte[] manifest;
            try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                zip.getNextEntry(); ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] block=new byte[4096]; int count;
                while((count=zip.read(block))!=-1) out.write(block,0,count); manifest=out.toByteArray();
            }
            for(int invalid=0;invalid<3;invalid++) {
                ByteArrayOutputStream broken=new ByteArrayOutputStream();
                try(ZipOutputStream zip=new ZipOutputStream(broken)) {
                    zip.putNextEntry(new ZipEntry("manifest.json")); zip.write(manifest); zip.closeEntry();
                    if(invalid>0) {
                        String path=invalid==1?"../escape.jpg":from.find(id).media().get(0).relativePath();
                        zip.putNextEntry(new ZipEntry(path)); zip.write(1); zip.closeEntry();
                    }
                }
                try { backup.restoreFrom(to,new ByteArrayInputStream(broken.toByteArray())); fail("Invalid backup accepted"); } catch(IOException expected) { }
                assertNull(to.find(id)); assertNotNull(to.find("existing"));
            }
            from.delete(id); to.delete("existing");
        } finally { context.deleteDatabase(source); context.deleteDatabase(target); }
    }
    @Test public void editDialogRetainsDraftAndSavesFromDetailScreen() throws Exception {
        String id=UUID.randomUUID().toString();
        try(SQLiteSavedItemRepository repository=new SQLiteSavedItemRepository(context)) {
            repository.add(item(id));
            android.content.Intent intent=new android.content.Intent(context,com.keepr.app.PostDetailActivity.class).putExtra("item_id",id);
            try(androidx.test.core.app.ActivityScenario<com.keepr.app.PostDetailActivity> scenario=androidx.test.core.app.ActivityScenario.launch(intent)) {
                awaitDetail(scenario,"item");
                scenario.onActivity(activity->{
                    try {
                        java.lang.reflect.Method show=activity.getClass().getDeclaredMethod("showEdit",android.os.Bundle.class); show.setAccessible(true); show.invoke(activity,(Object)null);
                        ((android.widget.EditText)field(activity,"editTitle")).setText("Draft survives rotation");
                        ((android.widget.EditText)field(activity,"editNotes")).setText("My edited note");
                        ((android.widget.EditText)field(activity,"editFolder")).setText("UI folder");
                        ((android.widget.EditText)field(activity,"editTags")).setText("ui, draft");
                    } catch(Exception error) { throw new AssertionError(error); }
                });
                scenario.recreate(); awaitDetail(scenario,"editDialog");
                scenario.onActivity(activity->{
                    try {
                        assertEquals("Draft survives rotation",((android.widget.EditText)field(activity,"editTitle")).getText().toString());
                        ((android.app.AlertDialog)field(activity,"editDialog")).getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
                    } catch(Exception error) { throw new AssertionError(error); }
                });
                long deadline=android.os.SystemClock.elapsedRealtime()+5000;
                while(!repository.find(id).title().equals("Draft survives rotation") && android.os.SystemClock.elapsedRealtime()<deadline) android.os.SystemClock.sleep(30);
                SavedItem saved=repository.find(id); assertEquals("Draft survives rotation",saved.title()); assertEquals("My edited note",saved.notes()); assertEquals("UI folder",saved.categoryId()); assertEquals(2,saved.tags().size());
            } finally { repository.delete(id); }
        }
    }
    private Object field(Object object,String name) throws Exception {
        java.lang.reflect.Field field=object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private void awaitDetail(androidx.test.core.app.ActivityScenario<com.keepr.app.PostDetailActivity> scenario,String name) {
        java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean(); long deadline=android.os.SystemClock.elapsedRealtime()+5000;
        while(!ready.get() && android.os.SystemClock.elapsedRealtime()<deadline) {
            scenario.onActivity(activity->{ try { ready.set(field(activity,name)!=null); } catch(Exception error) { throw new AssertionError(error); } });
            if(!ready.get()) android.os.SystemClock.sleep(30);
        }
        assertTrue("Detail did not become ready",ready.get());
    }
    @Test public void editingDoesNotReplaceMediaOrQueueAndInvalidEditRollsBack() throws Exception {
        String name="edit-"+UUID.randomUUID()+".db",id=UUID.randomUUID().toString();
        try(SQLiteSavedItemRepository repository=new SQLiteSavedItemRepository(context,name)) {
            repository.add(item(id)); CachedMedia photo=photo(id); repository.attach(photo); repository.planPhotos(id,List.of(photo.sourceUrl(),"https://example.com/pending.jpg"));
            repository.edit(id,"Edited","New notes","electRONics","#Home, home, Favorite");
            SavedItem edited=repository.find(id); assertEquals("Electronics",edited.categoryId()); assertEquals(2,edited.tags().size());
            assertEquals(photo.id(),edited.media().get(0).id()); assertEquals(1,repository.pendingPhotos(id).size()); assertEquals(100,edited.createdAt());
            try { repository.edit(id,"","Bad notes","Bad folder",""); fail("Empty title accepted"); } catch(IllegalArgumentException expected) { }
            assertEquals("New notes",repository.find(id).notes()); assertFalse(repository.folders().contains("Bad folder"));
            repository.edit(id,"Shared post","New notes","Electronics","home");
            repository.applyPreview(id,new PreviewMetadata("https://example.com/post","Unwanted replacement","","Example","","","",""));
            assertEquals("Shared post",repository.find(id).title());
            repository.delete(id);
        } finally { context.deleteDatabase(name); }
    }
}
