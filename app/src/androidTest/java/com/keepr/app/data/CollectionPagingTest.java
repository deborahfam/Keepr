package com.keepr.app.data;

import android.content.Context;
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
public class CollectionPagingTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private SavedItem item(String id,String title,long time,String price,String currency,List<String> tags) {
        return new SavedItem(id,"",title,"","","",price,currency,"","","Other",SavedItem.Status.ACTIVE,SavedItem.PreviewState.READY,"",time,time,"",tags,List.of());
    }
    @Test public void pagesSortBeforeLimitingAndSelectionIdsCoverEveryMatch() throws Exception {
        String db="pages-"+UUID.randomUUID()+".db";
        try(var repo=new SQLiteSavedItemRepository(context,db)) {
            for(int i=0;i<95;i++) repo.add(item("post-"+i,"Find "+i,100+i,Integer.toString(95-i),"USD",List.of("Home")));
            repo.trash(List.of("post-0"));
            FeedQuery query=new FeedQuery("Other","Find",SavedItem.Status.ACTIVE,"home","Shared content",0,1000,CollectionQuery.Sort.PRICE_LOW);
            Set<String> ids=new HashSet<>();
            for(int offset=0;offset<94;offset+=30) {
                var page=repo.page(query,offset,30); assertEquals(94,page.total()); assertTrue(page.items().size()<=30);
                for(SavedItem post:page.items()) assertTrue(ids.add(post.id()));
            }
            assertEquals("1",repo.page(query,0,30).items().get(0).price()); assertEquals(94,ids.size()); assertEquals(ids,new HashSet<>(repo.matchingIds(query)));
            assertEquals(94,repo.collectionStats().total()); assertEquals(0,repo.collectionStats().attention());
            assertTrue(repo.page(query,120,30).items().isEmpty());
        } finally { context.deleteDatabase(db); }
    }
    @Test public void databaseSearchHandlesAccentedCaseAndLiteralWildcards() throws Exception {
        String db="search-"+UUID.randomUUID()+".db";
        try(var repo=new SQLiteSavedItemRepository(context,db)) {
            repo.add(item("match","ÁRBOL * [Casa]?",1,"","",List.of("Niñez")));
            repo.add(item("other","ÁRBOL and Casa",2,"","",List.of()));
            FeedQuery query=new FeedQuery("All","árbol * [casa]?",null,"NIÑEZ","",0,100,CollectionQuery.Sort.NEWEST);
            assertEquals(List.of("match"),repo.matchingIds(query));
            assertEquals(1,repo.page(query,0,30).total());
        } finally { context.deleteDatabase(db); }
    }
    @Test public void feedKeepsThirtyRowsSelectsAcrossPagesAndRejectsOldSearchResults() throws Exception {
        String token="Paging-"+UUID.randomUUID(); List<String> ids=new ArrayList<>();
        try(var repo=new SQLiteSavedItemRepository(context)) {
            var db=repo.backupDatabase(); db.beginTransaction();
            try { for(int i=0;i<95;i++) { String id=UUID.randomUUID().toString(); ids.add(id); repo.add(item(id,token+" "+i,100+i,"","",List.of())); } db.setTransactionSuccessful(); }
            finally { db.endTransaction(); }
            try(ActivityScenario<MainActivity> screen=ActivityScenario.launch(MainActivity.class)) {
                ready(screen);
                screen.onActivity(activity->((EditText)field(activity,"search")).setText(token)); ready(screen);
                screen.onActivity(activity->{
                    assertEquals(95,field(activity,"totalResults")); assertEquals(30,((List<?>)field(activity,"links")).size());
                    assertEquals(30,((LinearLayout)activity.findViewById(R.id.collection_items)).getChildCount());
                    activity.findViewById(R.id.collection_select).performClick();
                    invoke(field(activity,"collectionControls"),"selectAllResults");
                });
                await(screen,activity->((Set<?>)field(field(activity,"collectionControls"),"selected")).size()==95);
                screen.onActivity(activity->activity.findViewById(R.id.next_page).performClick()); ready(screen);
                screen.onActivity(activity->{ assertEquals(30,field(activity,"pageOffset")); assertEquals(95,((Set<?>)field(field(activity,"collectionControls"),"selected")).size()); });
                screen.recreate(); ready(screen);
                screen.onActivity(activity->{ assertEquals(30,field(activity,"pageOffset")); activity.findViewById(R.id.view_list).performClick(); assertEquals(30,((List<?>)field(activity,"links")).size()); });
                screen.onActivity(activity->{
                    EditText search=(EditText)field(activity,"search"); search.setText(token+" 1"); search.setText(token+" 2"); search.setText(token+" 94");
                }); ready(screen);
                screen.onActivity(activity->{
                    assertEquals(0,field(activity,"pageOffset")); assertEquals(1,field(activity,"totalResults"));
                    assertEquals(token+" 94",((List<SavedItem>)field(activity,"links")).get(0).title());
                    assertEquals(1,((Set<?>)field(field(activity,"collectionControls"),"selected")).size());
                    ((EditText)field(activity,"search")).setText(token);
                }); ready(screen);
                for(int i=0;i<3;i++) { screen.onActivity(activity->activity.findViewById(R.id.next_page).performClick()); ready(screen); }
                screen.onActivity(activity->{ assertEquals(90,field(activity,"pageOffset")); assertEquals(5,((List<?>)field(activity,"links")).size()); });
                repo.trash(ids.subList(0,5)); // Oldest entries occupy the last page.
                screen.onActivity(activity->invoke(activity,"reloadCollection"));
                await(screen,activity->!(boolean)field(activity,"pageLoading") && (int)field(activity,"totalResults")==90);
                screen.onActivity(activity->{ assertEquals(60,field(activity,"pageOffset")); assertEquals(30,((List<?>)field(activity,"links")).size()); });
            }
        } finally { try(var repo=new SQLiteSavedItemRepository(context)) { for(String id:ids) repo.delete(id); } }
    }
    private static Object field(Object object,String name) { try { var f=object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); } catch(Exception e) { throw new AssertionError(e); } }
    private static void invoke(Object object,String name) { try { var m=object.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(object); } catch(Exception e) { throw new AssertionError(e); } }
    private void ready(ActivityScenario<MainActivity> screen) { await(screen,a->(boolean)field(a,"storageHealthy") && !(boolean)field(a,"pageLoading") && field(a,"displayedQuery")!=null); }
    private void await(ActivityScenario<MainActivity> screen,java.util.function.Predicate<MainActivity> predicate) {
        long deadline=android.os.SystemClock.elapsedRealtime()+15000; AtomicBoolean result=new AtomicBoolean();
        do { screen.onActivity(activity->result.set(predicate.test(activity))); if(result.get()) return; android.os.SystemClock.sleep(30); } while(android.os.SystemClock.elapsedRealtime()<deadline);
        fail("Collection did not reach expected state");
    }
}
