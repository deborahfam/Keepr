package com.keepr.app.data;

import android.content.Context;
import android.database.Cursor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;
import com.keepr.app.*;
import com.keepr.app.domain.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CollectionOrganizationTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private SavedItem item(String id,String folder,List<String> tags) { return new SavedItem(id,"","Title "+id,"Caption","","","20","USD","","Notes",folder,SavedItem.Status.ACTIVE,SavedItem.PreviewState.READY,"",1,1,"Original",tags,List.of()); }
    @Test public void bulkMoveTagsArchiveAndDeletePreserveUnselectedPosts() throws Exception {
        String name="bulk-"+UUID.randomUUID()+".db";
        try(SQLiteSavedItemRepository repo=new SQLiteSavedItemRepository(context,name)) {
            repo.add(item("a","Other",List.of("Home"))); repo.add(item("b","Other",List.of("Work"))); repo.add(item("c","Other",List.of()));
            assertEquals(2,repo.bulk(List.of("a","b"),SQLiteSavedItemRepository.BulkAction.MOVE,"New folder"));
            repo.bulk(List.of("a","b"),SQLiteSavedItemRepository.BulkAction.ADD_TAGS,"home, urgent");
            assertEquals(Set.of("Home","urgent"),new HashSet<>(repo.find("a").tags())); assertEquals(3,repo.find("b").tags().size());
            repo.bulk(List.of("a","b"),SQLiteSavedItemRepository.BulkAction.ARCHIVE,"");
            assertEquals(SavedItem.Status.ARCHIVED,repo.find("a").status()); assertEquals("Caption",repo.find("a").description()); assertEquals(1,repo.find("a").createdAt());
            assertEquals("Other",repo.find("c").categoryId()); assertEquals(SavedItem.Status.ACTIVE,repo.find("c").status());
            File photo=new File(context.getFilesDir(),"media/"+UUID.randomUUID()+".jpg"); photo.getParentFile().mkdirs(); try(FileOutputStream output=new FileOutputStream(photo)) { output.write(1); }
            repo.attach(new CachedMedia(UUID.randomUUID().toString(),"a","shared://test","media/"+photo.getName(),"image/jpeg",1,"hash",1));
            repo.bulk(List.of("a","b"),SQLiteSavedItemRepository.BulkAction.DELETE,""); assertNull(repo.find("a")); assertNull(repo.find("b")); assertNotNull(repo.find("c")); assertFalse(photo.exists());
        } finally { context.deleteDatabase(name); }
    }
    @Test public void bulkFailureRollsBackEarlierPostsAndNewFolder() throws Exception {
        String name="rollback-"+UUID.randomUUID()+".db";
        try(SQLiteSavedItemRepository repo=new SQLiteSavedItemRepository(context,name)) {
            List<String> full=new ArrayList<>(); for(int i=0;i<20;i++) full.add("tag"+i);
            repo.add(item("a","Other",List.of())); repo.add(item("b","Other",full));
            try { repo.bulk(List.of("a","b"),SQLiteSavedItemRepository.BulkAction.ADD_TAGS,"extra"); fail(); } catch(IllegalArgumentException expected) { }
            assertTrue(repo.find("a").tags().isEmpty()); assertEquals(20,repo.find("b").tags().size());
            try { repo.bulk(List.of("a","missing"),SQLiteSavedItemRepository.BulkAction.MOVE,"Rollback folder"); fail(); } catch(IllegalStateException expected) { }
            assertEquals("Other",repo.find("a").categoryId()); assertFalse(repo.folders().contains("Rollback folder"));
        } finally { context.deleteDatabase(name); }
    }
    @Test public void renameMergeAndBackupResolveStableFolderIds() throws Exception {
        String name="folders-"+UUID.randomUUID()+".db",target="restored-"+UUID.randomUUID()+".db";
        try(SQLiteSavedItemRepository repo=new SQLiteSavedItemRepository(context,name); SQLiteSavedItemRepository restored=new SQLiteSavedItemRepository(context,target)) {
            repo.add(item("a","Other",List.of("tag"))); repo.organizeFolder("Other","Wishlist",false);
            assertEquals("Wishlist",repo.find("a").categoryId()); assertFalse(repo.folders().contains("Other"));
            repo.add(item("b","Other",List.of())); assertEquals("Other",repo.find("b").categoryId());
            repo.edit("a","Edited","Notes","Wishlist","tag"); assertEquals("Wishlist",repo.find("a").categoryId());
            repo.organizeFolder("Wishlist","WISHLIST",false); assertEquals("WISHLIST",repo.find("a").categoryId());
            try { repo.organizeFolder("WISHLIST","Other",false); fail(); } catch(IllegalArgumentException expected) { }
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(); VaultBackup backup=new VaultBackup(context); backup.exportTo(repo,bytes); backup.restoreFrom(restored,new ByteArrayInputStream(bytes.toByteArray()));
            assertEquals("WISHLIST",restored.find("a").categoryId()); assertEquals("Other",restored.find("b").categoryId());
            repo.organizeFolder("WISHLIST","Other",true); assertEquals("Other",repo.find("a").categoryId()); assertFalse(repo.folders().contains("WISHLIST")); assertEquals(List.of("tag"),repo.find("a").tags());
            try(Cursor c=repo.backupDatabase().rawQuery("PRAGMA foreign_key_check",null)) { assertFalse(c.moveToFirst()); }
        } finally { context.deleteDatabase(name); context.deleteDatabase(target); }
    }
    @Test public void bulkSelectionSurvivesRecreationAndDropsHiddenItems() throws Exception {
        String id=UUID.randomUUID().toString();
        try(SQLiteSavedItemRepository repo=new SQLiteSavedItemRepository(context)) {
            repo.add(item(id,"Other",List.of()));
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
                awaitLoaded(scenario);
                scenario.onActivity(activity->{
                    activity.findViewById(R.id.collection_select).performClick(); Object controls=field(activity,"collectionControls");
                    ((Set<String>)field(controls,"selected")).add(id);
                });
                scenario.recreate(); awaitLoaded(scenario);
                scenario.onActivity(activity->{
                    assertTrue(((Set<?>)field(field(activity,"collectionControls"),"selected")).contains(id));
                    try { Object controls=field(activity,"collectionControls"); java.lang.reflect.Method show=controls.getClass().getDeclaredMethod("showTools"); show.setAccessible(true); show.invoke(controls); }
                    catch(Exception error) { throw new AssertionError(error); }
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(activity->{
                    ((android.widget.Spinner)field(activity,"statuses")).setSelection(SavedItem.Status.ARCHIVED.ordinal()+1);
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                awaitLoaded(scenario);
                scenario.onActivity(activity->assertTrue(((Set<?>)field(field(activity,"collectionControls"),"selected")).isEmpty()));
            } finally { repo.delete(id); }
        }
    }
    @Test public void viewChoicePersistsAndPreservesSelectionAndFilters() throws Exception {
        String id=UUID.randomUUID().toString();
        var preferences=context.getSharedPreferences("appearance",Context.MODE_PRIVATE);
        boolean previous=preferences.getBoolean("list_view",false);
        preferences.edit().putBoolean("list_view",false).commit();
        try(SQLiteSavedItemRepository repo=new SQLiteSavedItemRepository(context)) {
            repo.add(item(id,"Other",List.of()));
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
                awaitLoaded(scenario);
                scenario.onActivity(activity->{
                    ((android.widget.EditText)field(activity,"search")).setText(id);
                });
                awaitLoaded(scenario);
                scenario.onActivity(activity->{
                    activity.findViewById(R.id.collection_select).performClick();
                    ((Set<String>)field(field(activity,"collectionControls"),"selected")).add(id);
                    activity.findViewById(R.id.view_list).performClick();
                    android.widget.LinearLayout rows=activity.findViewById(R.id.collection_items);
                    assertEquals(1,rows.getChildCount()); assertEquals("list",rows.getChildAt(0).getTag());
                    assertTrue(activity.findViewById(R.id.view_list).isSelected());
                    assertTrue(((Set<?>)field(field(activity,"collectionControls"),"selected")).contains(id));
                });
                scenario.recreate(); awaitLoaded(scenario);
                scenario.onActivity(activity->{
                    assertTrue(activity.findViewById(R.id.view_list).isSelected());
                    assertEquals(id,((android.widget.EditText)field(activity,"search")).getText().toString());
                    assertTrue(((Set<?>)field(field(activity,"collectionControls"),"selected")).contains(id));
                    activity.findViewById(R.id.view_cards).performClick();
                    android.widget.LinearLayout rows=activity.findViewById(R.id.collection_items);
                    assertEquals(1,rows.getChildCount()); assertEquals("cards",rows.getChildAt(0).getTag());
                    assertTrue(activity.findViewById(R.id.view_cards).isSelected());
                    activity.findViewById(R.id.view_list).performClick();
                });
            }
            try(ActivityScenario<MainActivity> reopened=ActivityScenario.launch(MainActivity.class)) {
                awaitLoaded(reopened); reopened.onActivity(activity->assertTrue(activity.findViewById(R.id.view_list).isSelected()));
            } finally { repo.delete(id); }
        } finally { preferences.edit().putBoolean("list_view",previous).commit(); }
    }

    private void awaitLoaded(ActivityScenario<MainActivity> scenario) {
        java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean(); long until=android.os.SystemClock.elapsedRealtime()+5000;
        while(!ready.get() && android.os.SystemClock.elapsedRealtime()<until) { scenario.onActivity(activity->ready.set((boolean)field(activity,"storageHealthy") && !(boolean)field(activity,"pageLoading"))); if(!ready.get()) android.os.SystemClock.sleep(30); } assertTrue(ready.get());
    }
    private Object field(Object object,String name) { try { java.lang.reflect.Field field=object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); } catch(Exception error) { throw new AssertionError(error); } }
}
