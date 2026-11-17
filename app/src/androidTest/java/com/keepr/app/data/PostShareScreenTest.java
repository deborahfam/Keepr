package com.keepr.app.data;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.keepr.app.*;
import com.keepr.app.domain.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PostShareScreenTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    @Test public void sharingPreviewAndImportSurviveRotationAndUseCorrectMime() throws Exception {
        String id=UUID.randomUUID().toString(),title="Share screen "+id;
        File[] file={null};
        try(var repository=new SQLiteSavedItemRepository(context)) {
            repository.add(new SavedItem(id,"",title,"Caption","","","","","","Secret notes","Other",SavedItem.Status.ACTIVE,SavedItem.PreviewState.READY,"",1,1,"",List.of(),List.of()));
            Intent intent=new Intent(context,SharePostsActivity.class).putStringArrayListExtra("post_ids",new ArrayList<>(List.of(id)));
            try(ActivityScenario<SharePostsActivity> screen=ActivityScenario.launch(intent)) {
                await(screen,activity->!busy(activity));
                screen.onActivity(activity->{
                    assertFalse(((CheckBox)find(activity,context.getString(R.string.share_include_notes))).isChecked());
                    assertFalse(((CheckBox)find(activity,context.getString(R.string.share_include_contacts))).isChecked());
                    find(activity,context.getString(R.string.share_include_notes)).performClick();
                });
                screen.recreate();
                screen.onActivity(activity->{ assertTrue(((CheckBox)find(activity,context.getString(R.string.share_include_notes))).isChecked()); find(activity,context.getString(R.string.share_prepare_file)).performClick(); });
                await(screen,activity->!busy(activity));
                screen.onActivity(activity->{ assertNotNull(find(activity,context.getString(R.string.share_send_file))); file[0]=(File)field(field(activity,"job"),"file"); });
                assertNotNull(file[0]); assertTrue(file[0].isFile());
            }
            Uri uri=FileProvider.getUriForFile(context,context.getPackageName()+".sharedfiles",file[0]);
            assertEquals(PostShare.MIME,context.getContentResolver().getType(uri));
            Intent incoming=new Intent(Intent.ACTION_VIEW,uri).setType(PostShare.MIME).setDataAndType(uri,PostShare.MIME).setClass(context,ImportPostsActivity.class).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try(ActivityScenario<ImportPostsActivity> screen=ActivityScenario.launch(incoming)) {
                await(screen,activity->!busy(activity));
                screen.onActivity(activity->{ assertNotNull(find(activity,title)); assertNotNull(find(activity,context.getString(R.string.share_import_copies))); });
                screen.recreate();
                screen.onActivity(activity->{ assertNotNull(find(activity,title)); find(activity,context.getString(R.string.share_import_copies)).performClick(); });
                await(screen,activity->!busy(activity));
                screen.onActivity(activity->assertNotNull(find(activity,context.getString(R.string.share_open_collection))));
            }
            assertEquals(2,repository.load().stream().filter(post->post.title().equals(title)).count());
        } finally {
            try(var repository=new SQLiteSavedItemRepository(context)) { for(SavedItem post:repository.load()) if(post.title().equals(title)) repository.delete(post.id()); }
            if(file[0]!=null) file[0].delete();
        }
    }
    private boolean busy(Activity activity) { return (boolean)field(field(activity,"job"),"busy"); }
    private Object field(Object object,String name) { try { var f=object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); } catch(Exception error) { throw new AssertionError(error); } }
    private View find(Activity activity,String text) { return find(activity.getWindow().getDecorView(),text); }
    private View find(View view,String text) {
        if(view instanceof TextView && ((TextView)view).getText().toString().equals(text)) return view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) { View match=find(((ViewGroup)view).getChildAt(i),text); if(match!=null) return match; }
        return null;
    }
    private <A extends Activity> void await(ActivityScenario<A> scenario,java.util.function.Predicate<A> predicate) {
        long deadline=android.os.SystemClock.elapsedRealtime()+10000; AtomicReference<Boolean> ready=new AtomicReference<>(false);
        do { scenario.onActivity(activity->ready.set(predicate.test(activity))); if(ready.get()) return; android.os.SystemClock.sleep(30); } while(android.os.SystemClock.elapsedRealtime()<deadline);
        fail("Screen did not become ready");
    }
}
