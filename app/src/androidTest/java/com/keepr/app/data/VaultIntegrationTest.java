package com.keepr.app.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.keepr.app.domain.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class VaultIntegrationTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    @Test public void receivesSharedPhotoChoosesFolderAndSavesCaptionAndTags() throws Exception { verifyShare(1); }
    @Test public void receivesEveryPhotoInLargeSharedGallery() throws Exception { verifyShare(15); }
    private void verifyShare(int photoCount) throws Exception {
        String caption="Shared photo test "+UUID.randomUUID();
        ArrayList<android.net.Uri> uris=new ArrayList<>();
        String savedId=null;
        try {
            for(int i=0;i<photoCount;i++) {
                android.content.ContentValues values=new android.content.ContentValues();
                values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"keepr-test-"+i+".png");
                values.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");
                android.net.Uri uri=context.getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
                assertNotNull(uri); uris.add(uri);
                Bitmap bitmap=Bitmap.createBitmap(40,30,Bitmap.Config.ARGB_8888); bitmap.eraseColor(android.graphics.Color.rgb(i*13%255,180,210));
                try(java.io.OutputStream output=context.getContentResolver().openOutputStream(uri)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,output)); }
                bitmap.recycle();
            }
            android.content.Intent intent=new android.content.Intent(context,com.keepr.app.MainActivity.class)
                    .setAction(photoCount==1 ? android.content.Intent.ACTION_SEND : android.content.Intent.ACTION_SEND_MULTIPLE).setType("image/png")
                    .putExtra(android.content.Intent.EXTRA_TEXT,caption)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if(photoCount==1) intent.putExtra(android.content.Intent.EXTRA_STREAM,uris.get(0));
            else intent.putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM,uris);
            try (androidx.test.core.app.ActivityScenario<com.keepr.app.MainActivity> scenario=androidx.test.core.app.ActivityScenario.launch(intent)) {
                // Wait for asynchronous repository startup before tapping Save.
                long deadline=android.os.SystemClock.elapsedRealtime()+5000;
                java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean();
                while (!ready.get() && android.os.SystemClock.elapsedRealtime()<deadline) {
                    scenario.onActivity(activity->{
                        try { java.lang.reflect.Field field=activity.getClass().getDeclaredField("storageHealthy"); field.setAccessible(true); ready.set(field.getBoolean(activity)); }
                        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                    });
                    if (!ready.get()) android.os.SystemClock.sleep(30);
                }
                assertTrue(ready.get());
                scenario.onActivity(activity->{
                    try {
                        java.lang.reflect.Field tagField=activity.getClass().getDeclaredField("editorTags"); tagField.setAccessible(true);
                        ((android.widget.EditText)tagField.get(activity)).setText("apartment, wishlist");
                        java.lang.reflect.Field folderField=activity.getClass().getDeclaredField("editorCategory"); folderField.setAccessible(true);
                        android.widget.Spinner folder=(android.widget.Spinner)folderField.get(activity);
                        for (int i=0;i<folder.getCount();i++) if (folder.getItemAtPosition(i).equals("Real Estate")) folder.setSelection(i);
                        java.lang.reflect.Field dialogField=activity.getClass().getDeclaredField("editor"); dialogField.setAccessible(true);
                        ((android.app.AlertDialog)dialogField.get(activity)).getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
                    } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                });
                SavedItem saved=null; deadline=android.os.SystemClock.elapsedRealtime()+5000;
                try (SQLiteSavedItemRepository repository=new SQLiteSavedItemRepository(context)) {
                    while (saved==null && android.os.SystemClock.elapsedRealtime()<deadline) {
                        for (SavedItem item:repository.load()) if (item.sharedText().equals(caption)) saved=item;
                        if (saved==null) android.os.SystemClock.sleep(30);
                    }
                    assertNotNull(saved); savedId=saved.id(); assertEquals("Real Estate",saved.categoryId());
                    assertEquals(2,saved.tags().size()); assertEquals(photoCount,saved.media().size());
                    assertEquals(caption,saved.description()); assertEquals("",saved.url());
                    assertTrue(new File(context.getFilesDir(),saved.media().get(0).relativePath()).exists());
                }
            }
            if(photoCount>1) {
                android.content.Intent detail=new android.content.Intent(context,com.keepr.app.PostDetailActivity.class).putExtra("item_id",savedId);
                try(androidx.test.core.app.ActivityScenario<com.keepr.app.PostDetailActivity> scenario=androidx.test.core.app.ActivityScenario.launch(detail)) {
                    java.util.concurrent.atomic.AtomicBoolean loaded=new java.util.concurrent.atomic.AtomicBoolean();
                    long deadline=android.os.SystemClock.elapsedRealtime()+5000;
                    while(!loaded.get() && android.os.SystemClock.elapsedRealtime()<deadline) {
                        scenario.onActivity(activity->loaded.set(activity.findViewById(com.keepr.app.R.id.gallery_counter)!=null));
                        if(!loaded.get()) android.os.SystemClock.sleep(30);
                    }
                    assertTrue(loaded.get());
                    scenario.onActivity(activity->{
                        for(int index=1;index<photoCount;index++) activity.findViewById(com.keepr.app.R.id.next_photo).performClick();
                        assertEquals("Photo "+photoCount+" of "+photoCount,((android.widget.TextView)activity.findViewById(com.keepr.app.R.id.gallery_counter)).getText().toString());
                        activity.findViewById(com.keepr.app.R.id.detail_tab_photos).performClick();
                    });
                    scenario.recreate();
                    loaded.set(false); deadline=android.os.SystemClock.elapsedRealtime()+5000;
                    while(!loaded.get() && android.os.SystemClock.elapsedRealtime()<deadline) {
                        scenario.onActivity(activity->loaded.set(activity.findViewById(com.keepr.app.R.id.gallery_counter)!=null));
                        if(!loaded.get()) android.os.SystemClock.sleep(30);
                    }
                    assertTrue(loaded.get());
                    scenario.onActivity(activity->{
                        assertEquals("Photo "+photoCount+" of "+photoCount,((android.widget.TextView)activity.findViewById(com.keepr.app.R.id.gallery_counter)).getText().toString());
                        activity.findViewById(com.keepr.app.R.id.detail_tab_info).performClick();
                        assertNull(activity.findViewById(com.keepr.app.R.id.gallery_counter));
                        activity.findViewById(com.keepr.app.R.id.detail_tab_post).performClick();
                    });
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                    Bitmap screenshot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
                    try(java.io.FileOutputStream output=new java.io.FileOutputStream(new File(context.getCacheDir(),"detail-preview.png"))) { screenshot.compress(Bitmap.CompressFormat.PNG,100,output); }
                    screenshot.recycle();
                }
            }
        } finally {
            for(android.net.Uri uri:uris) context.getContentResolver().delete(uri,null,null);
            if (savedId!=null) try (SQLiteSavedItemRepository repository=new SQLiteSavedItemRepository(context)) { repository.delete(savedId); }
        }
    }
    @Test public void resumesEveryPlannedPhotoAcrossRepositoryRestart() throws Exception {
        String name="queue-test-"+UUID.randomUUID()+".db",id=UUID.randomUUID().toString();
        try {
            ArrayList<String> urls=new ArrayList<>(); for(int i=0;i<30;i++) urls.add("https://example.com/"+i+".png");
            try(SQLiteSavedItemRepository repository=new SQLiteSavedItemRepository(context,name)) {
                repository.add(new SavedItem(id,"https://example.com/post","Gallery","caption","","","","","","","Other",SavedItem.Status.ACTIVE,SavedItem.PreviewState.PENDING,"",1,1,"",List.of(),List.of()));
                repository.planPhotos(id,urls); repository.planPhotos(id,urls);
                assertEquals(30,repository.photoProgress(id).total()); assertEquals(12,repository.pendingPhotos(id).size());
                Bitmap bitmap=Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888); ByteArrayOutputStream bytes=new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes); bitmap.recycle();
                for(int i=0;i<15;i++) repository.attach(new MediaCache(context).store(id,urls.get(i),bytes.toByteArray()));
            }
            try(SQLiteSavedItemRepository repository=new SQLiteSavedItemRepository(context,name)) {
                assertEquals(15,repository.photoProgress(id).saved()); assertEquals(30,repository.photoProgress(id).total()); assertEquals(15,repository.photoProgress(id).pending());
                assertEquals(urls.get(15),repository.pendingPhotos(id).get(0));
                for(int i=0;i<3;i++) repository.failPhoto(id,urls.get(15));
                assertEquals(1,repository.photoProgress(id).failed());
                repository.retryPhotos(id); assertEquals(0,repository.photoProgress(id).failed()); assertEquals(15,repository.photoProgress(id).pending());
            }
        } finally {
            try(SQLiteSavedItemRepository repository=new SQLiteSavedItemRepository(context,name)) { repository.delete(id); assertFalse(repository.hasPhotoPlan(id)); }
            context.deleteDatabase(name);
        }
    }
    @Test public void upgradesV1WithoutLosingSavedItems() {
        String name="migration-"+UUID.randomUUID()+".db";
        try {
            try (SQLiteDatabase old=context.openOrCreateDatabase(name,0,null)) {
                old.execSQL("CREATE TABLE SavedItem(id TEXT PRIMARY KEY,title TEXT)");
                old.execSQL("INSERT INTO SavedItem VALUES('existing','Keep me')"); old.setVersion(1);
            }
            try (VaultDatabase helper=new VaultDatabase(context,name)) {
                try (android.database.Cursor cursor=helper.getReadableDatabase().rawQuery("SELECT title,shared_text,title_edited FROM SavedItem WHERE id='existing'",null)) {
                    assertTrue(cursor.moveToFirst()); assertEquals("Keep me",cursor.getString(0)); assertEquals("",cursor.getString(1)); assertEquals(0,cursor.getInt(2));
                }
                assertEquals(6,helper.getReadableDatabase().getVersion());
            }
        } finally { context.deleteDatabase(name); }
    }
    @Test public void storesTagsPhotosAndCaptionsOfflineAndDeletesFiles() throws Exception {
        String name="vault-test-"+UUID.randomUUID()+".db",id=UUID.randomUUID().toString();
        MediaCache cache=new MediaCache(context); CachedMedia photo=null;
        try (SQLiteSavedItemRepository repository=new SQLiteSavedItemRepository(context,name)) {
            Bitmap bitmap=Bitmap.createBitmap(32,24,Bitmap.Config.ARGB_8888); bitmap.eraseColor(android.graphics.Color.MAGENTA);
            ByteArrayOutputStream out=new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG,100,out); bitmap.recycle();
            photo=cache.store(id,"https://example.com/photo.png",out.toByteArray());
            SavedItem item=new SavedItem(id,"https://example.com/post","example.com","A full shared caption","","","","","","My notes","Wishlist",
                    SavedItem.Status.ACTIVE,SavedItem.PreviewState.PENDING,"",1,1,"A full shared caption",List.of("Apartment","Budget"),List.of(photo));
            repository.add(item);
            assertTrue(repository.folders().contains("Wishlist"));
            SavedItem saved=repository.find(id); assertEquals(2,saved.tags().size()); assertEquals(1,saved.media().size());
            assertTrue(new File(context.getFilesDir(),saved.media().get(0).relativePath()).isFile());
            repository.applyPreview(id,new PreviewMetadata(item.url(),"Listing title","Public description","Example","https://example.com/photo.png","10","USD",""));
            assertEquals("A full shared caption",repository.find(id).sharedText());
            assertEquals("Public description",repository.find(id).description());
            assertEquals("Listing title",repository.find(id).title());
            try { cache.store(id,"bad",new byte[]{1,2,3}); fail("Invalid photo accepted"); } catch (java.io.IOException expected) { }
            repository.delete(id); assertNull(repository.find(id)); assertFalse(new File(context.getFilesDir(),photo.relativePath()).exists());
            // A background download finishing after deletion must not leave an orphan file.
            CachedMedia late=cache.store(id,"https://example.com/late",out.toByteArray());
            repository.attach(late); assertFalse(new File(context.getFilesDir(),late.relativePath()).exists());
        } finally {
            context.deleteDatabase(name);
            if (photo!=null) new File(context.getFilesDir(),photo.relativePath()).delete();
        }
    }
}
