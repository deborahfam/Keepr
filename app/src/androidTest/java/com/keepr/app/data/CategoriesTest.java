package com.keepr.app.data;

import android.app.AlertDialog;
import android.content.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.keepr.app.*;
import com.keepr.app.domain.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CategoriesTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    @Test public void collectionAndCategoryLayoutsRender() throws Exception {
        String id=UUID.randomUUID().toString(); boolean old=context.getSharedPreferences("appearance",Context.MODE_PRIVATE).getBoolean("list_view",false);
        try(var repository=new SQLiteSavedItemRepository(context)) {
            repository.add(new SavedItem(id,"","Lounge chair for the reading corner","A comfortable find for home.","","","85","USD","","","Marketplace",SavedItem.Status.ACTIVE,SavedItem.PreviewState.READY,"",System.currentTimeMillis(),System.currentTimeMillis(),"",List.of(),List.of(),0,0,0,"San Juan","Furniture","",""));
            try(ActivityScenario<MainActivity> screen=ActivityScenario.launch(MainActivity.class)) {
                readyMain(screen); screen.onActivity(activity->{ activity.findViewById(R.id.view_cards).performClick(); assertNotNull(activity.findViewById(R.id.manage_categories)); assertTrue(((LinearLayout)activity.findViewById(R.id.collection_items)).getChildCount()>0); }); screenshot("collection-cards-preview.png");
                screen.onActivity(activity->{ activity.findViewById(R.id.view_list).performClick(); assertTrue(activity.findViewById(R.id.view_list).isSelected()); }); screenshot("collection-list-preview.png");
            }
            try(ActivityScenario<CategoriesActivity> screen=ActivityScenario.launch(CategoriesActivity.class)) { ready(screen); screen.onActivity(activity->assertTrue(activity.findViewById(R.id.category_add).isEnabled())); screenshot("categories-preview.png"); }
        } finally { try(var repository=new SQLiteSavedItemRepository(context)) { repository.delete(id); } context.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putBoolean("list_view",old).commit(); }
    }
    private void screenshot(String name) throws Exception {
        var instrumentation=InstrumentationRegistry.getInstrumentation(); instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(400); // Allow the Activity transition to finish before visual QA.
        android.graphics.Bitmap image=instrumentation.getUiAutomation().takeScreenshot(); assertNotNull(image);
        try(var output=new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null),name))) { assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output)); } finally { image.recycle(); }
    }
    private SavedItem item(String id,String category) { return new SavedItem(id,"","Keep this post","Caption","","","10","USD","","Keep notes",category,SavedItem.Status.PURCHASED,SavedItem.PreviewState.READY,"",1,1,"",List.of("tag"),List.of()); }
    @Test public void createRenameAndRemoveKeepPostsAndTrashIntact() throws Exception {
        String db="categories-"+UUID.randomUUID()+".db";
        try(var repository=new SQLiteSavedItemRepository(context,db)) {
            repository.saveCategory(null,"Wishlist"); var category=repository.categories().stream().filter(c->c.name().equals("Wishlist")).findFirst().orElseThrow();
            repository.add(item("active","Wishlist")); repository.add(item("trashed","Wishlist")); repository.trash(List.of("trashed"));
            long expiry=repository.trashContents().get(0).expiresAt();
            repository.saveCategory(category.id(),"Weekend plans"); var renamed=repository.categories().stream().filter(c->c.id().equals(category.id())).findFirst().orElseThrow();
            assertEquals("Weekend plans",renamed.name()); assertEquals(1,renamed.saved()); assertEquals(1,renamed.trashed()); assertEquals("Weekend plans",repository.find("active").categoryId());
            try { repository.saveCategory(null,"weekend plans"); fail(); } catch(IllegalArgumentException expected) { }
            try { repository.removeCategory(category.id(),null); fail(); } catch(IllegalArgumentException expected) { }
            assertEquals(2,repository.categories().stream().filter(c->c.id().equals(category.id())).findFirst().orElseThrow().saved()+renamed.trashed());
            repository.removeCategory(category.id(),"Other");
            assertEquals("Other",repository.find("active").categoryId()); assertEquals("Keep notes",repository.find("active").notes()); assertEquals(SavedItem.Status.PURCHASED,repository.find("active").status()); assertEquals(List.of("tag"),repository.find("active").tags());
            assertEquals(expiry,repository.trashContents().get(0).expiresAt()); repository.restoreTrash("trashed"); assertEquals("Other",repository.find("trashed").categoryId());
            assertFalse(repository.folders().contains("Weekend plans"));
            repository.saveCategory(null,"Empty"); String empty=repository.categories().stream().filter(c->c.name().equals("Empty")).findFirst().orElseThrow().id(); repository.removeCategory(empty,null); assertFalse(repository.folders().contains("Empty"));
        } finally { context.deleteDatabase(db); }
    }
    @Test public void rejectsInvalidNamesAndMissingDestinationsWithoutChangingContents() throws Exception {
        String db="category-validation-"+UUID.randomUUID()+".db";
        try(var repository=new SQLiteSavedItemRepository(context,db)) {
            for(String value:List.of(" ","All","x".repeat(61),"other")) { try { repository.saveCategory(null,value); fail(); } catch(IllegalArgumentException expected) { } }
            repository.add(item("keep","Other"));
            for(String destination:List.of("missing","Other")) { try { repository.removeCategory("Other",destination); fail(); } catch(IllegalArgumentException | IllegalStateException expected) { } }
            assertEquals("Other",repository.find("keep").categoryId());
            for(var category:repository.categories()) if(!category.id().equals("Other")) repository.removeCategory(category.id(),null);
            try { repository.removeCategory("Other",null); fail(); } catch(IllegalStateException expected) { }
            assertEquals(1,repository.categories().size());
        } finally { context.deleteDatabase(db); }
    }
    @Test public void categoryEditorRetainsDraftAndSupportsCreateRenameRemove() throws Exception {
        String name="Category UI "+UUID.randomUUID(),renamed=name+"!"; String[] categoryId={null};
        try(ActivityScenario<CategoriesActivity> screen=ActivityScenario.launch(CategoriesActivity.class)) {
            ready(screen); screen.onActivity(activity->{ activity.findViewById(R.id.category_add).performClick(); AlertDialog dialog=(AlertDialog)field(activity,"dialog"); ((EditText)dialog.findViewById(R.id.category_name)).setText(name); });
            screen.recreate(); ready(screen);
            screen.onActivity(activity->{ AlertDialog dialog=(AlertDialog)field(activity,"dialog"); assertEquals(name,((EditText)dialog.findViewById(R.id.category_name)).getText().toString()); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); }); ready(screen);
            try(var repository=new SQLiteSavedItemRepository(context)) { categoryId[0]=repository.categories().stream().filter(c->c.name().equals(name)).findFirst().orElseThrow().id(); }
            screen.onActivity(activity->{ findDescription(activity.getWindow().getDecorView(),"Rename "+name).performClick(); AlertDialog dialog=(AlertDialog)field(activity,"dialog"); ((EditText)dialog.findViewById(R.id.category_name)).setText(renamed); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); }); ready(screen);
            screen.onActivity(activity->{ findDescription(activity.getWindow().getDecorView(),"Remove category "+renamed).performClick(); ((AlertDialog)field(activity,"dialog")).getButton(AlertDialog.BUTTON_POSITIVE).performClick(); }); ready(screen);
            try(var repository=new SQLiteSavedItemRepository(context)) { assertFalse(repository.folders().contains(renamed)); }
        } finally { if(categoryId[0]!=null) try(var repository=new SQLiteSavedItemRepository(context)) { if(repository.categories().stream().anyMatch(c->c.id().equals(categoryId[0]))) repository.removeCategory(categoryId[0],null); } }
    }
    @Test public void selectedCategoryFollowsRenameAndResetsAfterRemoval() throws Exception {
        String name="Filter category "+UUID.randomUUID(),post=UUID.randomUUID().toString(); String id;
        try(var repository=new SQLiteSavedItemRepository(context)) {
            repository.saveCategory(null,name); id=repository.categories().stream().filter(c->c.name().equals(name)).findFirst().orElseThrow().id(); repository.add(item(post,name));
            try(ActivityScenario<MainActivity> screen=ActivityScenario.launch(MainActivity.class)) {
                readyMain(screen); screen.onActivity(activity->{ set(activity,"category",name); set(activity,"selectedCategoryId",id); invoke(activity,"renderCards"); }); readyMain(screen);
                repository.saveCategory(id,name+" renamed"); screen.onActivity(activity->invoke(activity,"reloadCollection"));
                awaitMain(screen,activity->(name+" renamed").equals(field(activity,"category")) && !(boolean)field(activity,"pageLoading"));
                screen.onActivity(activity->assertEquals(1,field(activity,"totalResults")));
                repository.removeCategory(id,"Other"); screen.onActivity(activity->invoke(activity,"reloadCollection")); awaitMain(screen,activity->"All".equals(field(activity,"category")) && !(boolean)field(activity,"pageLoading"));
                assertEquals("Other",repository.find(post).categoryId());
            } finally { repository.delete(post); if(repository.categories().stream().anyMatch(c->c.id().equals(id))) repository.removeCategory(id,null); }
        }
    }
    private android.view.View findDescription(android.view.View view,String description) { if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription())) return view; if(view instanceof android.view.ViewGroup group) for(int i=0;i<group.getChildCount();i++) { var match=findDescription(group.getChildAt(i),description); if(match!=null) return match; } return null; }
    private Object field(Object object,String name) { try { var f=object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); } catch(Exception e) { throw new AssertionError(e); } }
    private void set(Object object,String name,Object value) { try { var f=object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object,value); } catch(Exception e) { throw new AssertionError(e); } }
    private void invoke(Object object,String name) { try { var m=object.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(object); } catch(Exception e) { throw new AssertionError(e); } }
    private void ready(ActivityScenario<CategoriesActivity> screen) { AtomicBoolean ready=new AtomicBoolean(); long until=android.os.SystemClock.elapsedRealtime()+10000; do { screen.onActivity(activity->ready.set(!(boolean)field(field(activity,"job"),"busy"))); if(ready.get()) return; android.os.SystemClock.sleep(30); } while(android.os.SystemClock.elapsedRealtime()<until); fail("Category screen not ready"); }
    private void readyMain(ActivityScenario<MainActivity> screen) { awaitMain(screen,activity->(boolean)field(activity,"storageHealthy") && !(boolean)field(activity,"pageLoading") && field(activity,"displayedQuery")!=null); }
    private void awaitMain(ActivityScenario<MainActivity> screen,java.util.function.Predicate<MainActivity> predicate) { AtomicBoolean ready=new AtomicBoolean(); long until=android.os.SystemClock.elapsedRealtime()+10000; do { screen.onActivity(activity->ready.set(predicate.test(activity))); if(ready.get()) return; android.os.SystemClock.sleep(30); } while(android.os.SystemClock.elapsedRealtime()<until); fail("Collection not ready"); }
}
