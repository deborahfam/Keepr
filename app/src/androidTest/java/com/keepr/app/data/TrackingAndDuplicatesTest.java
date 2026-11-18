package com.keepr.app.data;

import android.app.*;
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
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TrackingAndDuplicatesTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private SavedItem item(String id,String url,String title,String folder) {
        return new SavedItem(id,url,title,"Caption","","","100","USD","","Keep my note",folder,SavedItem.Status.ACTIVE,
            SavedItem.PreviewState.READY,"",100,100,"Original",List.of("tag"),List.of());
    }
    @Test public void failedCapturesStayStoppedUntilExplicitRetry() throws Exception {
        String db="capture-retry-"+UUID.randomUUID()+".db";
        try {
            try(SQLiteSavedItemRepository repository=new SQLiteSavedItemRepository(context,db)) {
                repository.add(item("failed","https://example.invalid/failed","Failed","Other"));
                repository.add(item("legacy","https://example.invalid/legacy","Legacy","Other"));
                repository.add(item("pending","https://example.invalid/pending","Pending","Other"));
                repository.previewState("failed",SavedItem.PreviewState.FAILED,"Unavailable");
                repository.previewState("pending",SavedItem.PreviewState.PENDING,"");
                repository.finishPhotoDiscovery("pending");
            }
            // Reopening storage must not turn a terminal failure into another download batch.
            try(SQLiteSavedItemRepository repository=new SQLiteSavedItemRepository(context,db)) {
                assertTrue(repository.needsPhotoDiscovery("failed"));
                assertFalse(repository.pendingItemIds().contains("failed"));
                assertTrue(repository.pendingItemIds().containsAll(List.of("legacy","pending")));
                repository.retryPhotos("failed");
                assertTrue(repository.pendingItemIds().contains("failed"));
            }
        } finally { context.deleteDatabase(db); }
    }

    @Test public void statusChangesPreserveContentAndDuplicateMatchingPreservesQueries() throws Exception {
        String db="tracking-"+UUID.randomUUID()+".db";
        try(SQLiteSavedItemRepository repository=new SQLiteSavedItemRepository(context,db)) {
            repository.add(item("old","HTTPS://EXAMPLE.COM/post?id=1","My title","Other"));
            repository.add(item("text","","Text only","Other"));
            for(SavedItem.Status status:SavedItem.Status.values()) { repository.setStatus("old",status); assertEquals(status,repository.find("old").status()); }
            assertEquals("Keep my note",repository.find("old").notes()); assertEquals("Caption",repository.find("old").description()); assertEquals(100,repository.find("old").createdAt());
            assertEquals("old",repository.findDuplicate("https://example.com/post?id=1").id());
            assertNull(repository.findDuplicate("https://example.com/post?id=2")); assertNull(repository.findDuplicate(""));
            try { repository.setStatus("missing",SavedItem.Status.ARCHIVED); fail("Missing item accepted"); } catch(IllegalStateException expected) { }
        } finally { context.deleteDatabase(db); }
    }
    @Test public void filterCombinesStatusFolderAndSearchAndSurvivesRecreation() throws Exception {
        String token="Filter-"+UUID.randomUUID(),a=UUID.randomUUID().toString(),b=UUID.randomUUID().toString();
        try(SQLiteSavedItemRepository repository=new SQLiteSavedItemRepository(context)) {
            repository.add(item(a,"",token,"Other")); repository.add(item(b,"",token,"Electronics")); repository.setStatus(a,SavedItem.Status.PURCHASED);
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
                await(scenario,activity->(boolean)field(activity,"storageHealthy") && !(boolean)field(activity,"pageLoading"));
                scenario.onActivity(activity->{
                    set(activity,"query",token); set(activity,"category","Other");
                    invoke(field(activity,"collectionControls"),"showTools",new Class<?>[]{});
                    ((Spinner)field(activity,"statuses")).setSelection(SavedItem.Status.PURCHASED.ordinal()+1);
                });
                await(scenario,activity->((TextView)field(activity,"count")).getText().toString().startsWith("1 result"));
                scenario.recreate(); await(scenario,activity->(boolean)field(activity,"storageHealthy") && !(boolean)field(activity,"pageLoading"));
                scenario.onActivity(activity->{
                    assertEquals("Other",field(activity,"category")); assertEquals(token,field(activity,"query")); assertEquals(SavedItem.Status.PURCHASED,field(activity,"statusFilter"));
                    assertTrue(((TextView)field(activity,"count")).getText().toString().startsWith("1 result"));
                    invoke(field(activity,"collectionControls"),"showTools",new Class<?>[]{});
                    ((Spinner)field(activity,"statuses")).setSelection(SavedItem.Status.ARCHIVED.ordinal()+1);
                });
                await(scenario,activity->((TextView)field(activity,"count")).getText().toString().startsWith("0 results"));
            } finally { repository.delete(a); repository.delete(b); }
        }
    }
    @Test public void duplicatePromptCancelsWithoutSavingAndAllowsExplicitCopy() throws Exception { duplicateChoice(AlertDialog.BUTTON_POSITIVE); }
    @Test public void duplicatePromptOpensExistingWithoutCreatingCopy() throws Exception { duplicateChoice(AlertDialog.BUTTON_NEUTRAL); }
    @Test public void duplicateRefreshPreservesSavedFieldsWithoutCreatingCopy() throws Exception { duplicateChoice(AlertDialog.BUTTON_NEGATIVE); }
    private void duplicateChoice(int choice) throws Exception {
        String id=UUID.randomUUID().toString(),url="https://example.invalid/"+id;
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        android.app.Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(PostDetailActivity.class.getName(),null,false);
        try(SQLiteSavedItemRepository repository=new SQLiteSavedItemRepository(context)) {
            repository.add(item(id,url,"Saved title","Other")); repository.finishPhotoDiscovery(id); repository.setStatus(id,SavedItem.Status.PURCHASED);
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
                await(scenario,activity->(boolean)field(activity,"storageHealthy") && !(boolean)field(activity,"pageLoading"));
                scenario.onActivity(activity->{ invoke(activity,"showEditor",new Class<?>[]{String.class},url); ((EditText)field(activity,"editorTitle")).setText("New draft title"); });
                instrumentation.waitForIdleSync();
                scenario.onActivity(activity->((AlertDialog)field(activity,"editor")).getButton(AlertDialog.BUTTON_POSITIVE).performClick());
                await(scenario,activity->field(activity,"duplicateDialog")!=null);
                scenario.onActivity(activity->{ ((AlertDialog)field(activity,"duplicateDialog")).cancel(); });
                assertEquals(1,matches(repository,url).size());
                scenario.onActivity(activity->((AlertDialog)field(activity,"editor")).getButton(AlertDialog.BUTTON_POSITIVE).performClick());
                await(scenario,activity->((AlertDialog)field(activity,"duplicateDialog")).isShowing());
                scenario.onActivity(activity->((AlertDialog)field(activity,"duplicateDialog")).getButton(choice).performClick());
                if(choice==AlertDialog.BUTTON_POSITIVE) {
                    long deadline=android.os.SystemClock.elapsedRealtime()+5000;
                    while(matches(repository,url).size()<2 && android.os.SystemClock.elapsedRealtime()<deadline) android.os.SystemClock.sleep(30);
                    assertEquals(2,matches(repository,url).size());
                    assertTrue(matches(repository,url).stream().anyMatch(item->item.title().equals("New draft title")));
                } else {
                    Activity detail=instrumentation.waitForMonitorWithTimeout(monitor,5000); assertNotNull(detail);
                    assertEquals(id,detail.getIntent().getStringExtra("item_id")); instrumentation.runOnMainSync(detail::finish);
                    assertEquals(1,matches(repository,url).size());
                }
                SavedItem original=repository.find(id); assertEquals("Saved title",original.title()); assertEquals("Keep my note",original.notes()); assertEquals(SavedItem.Status.PURCHASED,original.status()); assertEquals(List.of("tag"),original.tags());
            } finally {
                for(SavedItem item:matches(repository,url)) { androidx.work.WorkManager.getInstance(context).cancelUniqueWork("snapshot-"+item.id()).getResult().get(); repository.delete(item.id()); }
            }
        } finally { instrumentation.removeMonitor(monitor); }
    }
    @Test public void statusPickerSavesFromDetailScreen() throws Exception {
        String id=UUID.randomUUID().toString();
        try(SQLiteSavedItemRepository repository=new SQLiteSavedItemRepository(context)) {
            repository.add(item(id,"","Status UI","Other"));
            try(ActivityScenario<PostDetailActivity> scenario=ActivityScenario.launch(new Intent(context,PostDetailActivity.class).putExtra("item_id",id))) {
                await(scenario,activity->activity.findViewById(R.id.change_status)!=null);
                scenario.onActivity(activity->activity.findViewById(R.id.change_status).performClick());
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(activity->{
                    AlertDialog dialog=(AlertDialog)field(activity,"statusDialog");
                    dialog.getListView().performItemClick(null,SavedItem.Status.REACHED_OUT.ordinal(),SavedItem.Status.REACHED_OUT.ordinal());
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                });
                long deadline=android.os.SystemClock.elapsedRealtime()+5000;
                while(repository.find(id).status()!=SavedItem.Status.REACHED_OUT && android.os.SystemClock.elapsedRealtime()<deadline) android.os.SystemClock.sleep(30);
                assertEquals(SavedItem.Status.REACHED_OUT,repository.find(id).status());
            } finally { repository.delete(id); }
        }
    }
    private List<SavedItem> matches(SQLiteSavedItemRepository repository,String url) throws Exception { List<SavedItem> result=new ArrayList<>(); for(SavedItem item:repository.load()) if(item.url().equals(url)) result.add(item); return result; }
    private interface Check<T> { boolean ready(T activity); }
    private <T extends Activity> void await(ActivityScenario<T> scenario,Check<T> check) {
        java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean(); long deadline=android.os.SystemClock.elapsedRealtime()+5000;
        while(!ready.get() && android.os.SystemClock.elapsedRealtime()<deadline) { scenario.onActivity(activity->ready.set(check.ready(activity))); if(!ready.get()) android.os.SystemClock.sleep(30); }
        assertTrue("UI did not become ready",ready.get());
    }
    private Object field(Object object,String name) { try { java.lang.reflect.Field field=object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); } catch(Exception error) { throw new AssertionError(error); } }
    private void set(Object object,String name,Object value) { try { java.lang.reflect.Field field=object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object,value); } catch(Exception error) { throw new AssertionError(error); } }
    private void invoke(Object object,String name,Class<?>[] types,Object... values) { try { java.lang.reflect.Method method=object.getClass().getDeclaredMethod(name,types); method.setAccessible(true); method.invoke(object,values); } catch(Exception error) { throw new AssertionError(error); } }
}
