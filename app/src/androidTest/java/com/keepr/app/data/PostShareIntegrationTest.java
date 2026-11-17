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
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PostShareIntegrationTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private SavedItem item(String id,String url,List<CachedMedia> media) {
        return new SavedItem(id,url,"Shared apartment","Phone: +17875551234","","","1200","USD","","Secret notes","Rentals",SavedItem.Status.PURCHASED,SavedItem.PreviewState.READY,"",1,1,"",List.of("private"),media,media.size(),0,0,"San Juan","Home","month","",ContactDetails.EMPTY);
    }
    private PostShare.Preview preview(List<SavedItem> items,boolean caption) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); PostShare.write(items,new PostShare.Options(false,false,caption),context.getFilesDir(),out);
        return PostShare.read(new ByteArrayInputStream(out.toByteArray()),context.getCacheDir());
    }
    @Test public void importedCopiesPreservePrivacyAndNeverAutoFetch() throws Exception {
        String name="share-"+UUID.randomUUID()+".db";
        try(var repo=new SQLiteSavedItemRepository(context,name); var preview=preview(List.of(item("sender","https://example.com/post",List.of())),true)) {
            assertEquals(1,repo.importShared(preview,true)); SavedItem saved=repo.load().get(0);
            assertNotEquals("sender",saved.id()); assertEquals("Other",saved.categoryId()); assertEquals(SavedItem.Status.ACTIVE,saved.status());
            assertTrue(saved.contacts().isEmpty()); assertTrue(saved.notes().isEmpty()); assertTrue(saved.tags().isEmpty()); assertFalse(repo.needsPhotoDiscovery(saved.id())); assertTrue(repo.pendingItemIds().isEmpty());
            assertEquals("1200",saved.price()); assertEquals("San Juan",saved.location()); assertEquals("month",saved.pricePeriod());
            repo.applyPreview(saved.id(),new MetadataParser().parse("<meta property='og:description' content='Phone: +17875559999'>","https://example.com/post"));
            assertTrue(repo.load().get(0).contacts().isEmpty());
        } finally { context.deleteDatabase(name); }
    }
    @Test public void duplicateChoiceSkipsOrAddsWithoutOverwriting() throws Exception {
        String name="share-duplicates-"+UUID.randomUUID()+".db";
        try(var repo=new SQLiteSavedItemRepository(context,name); var preview=preview(List.of(item("sender","https://example.com/post",List.of())),false)) {
            repo.add(item("existing","https://example.com/post",List.of()));
            assertEquals(0,repo.importShared(preview,true)); assertEquals(1,repo.load().size());
            assertEquals(1,repo.importShared(preview,false)); assertEquals(2,repo.load().size());
            assertEquals("Secret notes",repo.find("existing").notes()); assertEquals(SavedItem.Status.PURCHASED,repo.find("existing").status());
        } finally { context.deleteDatabase(name); }
    }
    @Test public void photosAreCopiedAndFailedBatchRollsBack() throws Exception {
        String name="share-photos-"+UUID.randomUUID()+".db"; List<File> cleanup=new ArrayList<>();
        Bitmap bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888); ByteArrayOutputStream bytes=new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG,90,bytes); bitmap.recycle();
        CachedMedia photo=new MediaCache(context).store("sender","local-photo",bytes.toByteArray()); cleanup.add(new File(context.getFilesDir(),photo.relativePath()));
        try(var repo=new SQLiteSavedItemRepository(context,name); var preview=preview(List.of(item("sender","",List.of(photo))),false)) {
            assertEquals(1,repo.importShared(preview,true)); SavedItem saved=repo.load().get(0); assertEquals(1,saved.media().size());
            CachedMedia copy=saved.media().get(0); cleanup.add(new File(context.getFilesDir(),copy.relativePath())); assertNotEquals(photo.relativePath(),copy.relativePath()); assertTrue(cleanup.get(1).isFile());
            // Fail after a valid first post/photo has been installed; the complete batch must roll back.
            PostShare.Post valid=preview.posts().get(0);
            PostShare.Post invalid=new PostShare.Post("","Bad image","","","","","","","",ContactDetails.EMPTY,List.of(new PostShare.Photo("missing.jpg",1,"")));
            Set<String> before=new HashSet<>(Arrays.asList(Objects.requireNonNull(new File(context.getFilesDir(),"media").list())));
            try { repo.importShared(new PostShare.Preview(preview.directory(),List.of(valid,invalid)),false); fail("Expected atomic failure"); } catch(IOException expected) { }
            assertEquals(1,repo.load().size()); assertEquals(before,new HashSet<>(Arrays.asList(Objects.requireNonNull(new File(context.getFilesDir(),"media").list()))));
        } finally { context.deleteDatabase(name); for(File file:cleanup) file.delete(); }
    }
}
