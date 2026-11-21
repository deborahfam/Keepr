package com.keepr.app;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import androidx.core.content.FileProvider;
import com.keepr.app.data.*;
import com.keepr.app.domain.SavedItem;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** User-reviewed sharing; the Android chooser always controls the recipient. */
public final class SharePostsActivity extends Activity {
    private Job job;
    private LinearLayout body;
    private static final class Job {
        volatile SharePostsActivity screen;
        volatile boolean busy=true;
        volatile List<SavedItem> items=List.of();
        volatile String message="";
        volatile File file;
        boolean notes,contacts,caption;
        void refresh() { SharePostsActivity activity=screen; if(activity!=null) activity.runOnUiThread(()->{ if(screen==activity && !activity.isDestroyed()) activity.render(); }); }
    }
    public static void open(Activity activity,List<String> ids) {
        activity.startActivity(new Intent(activity,SharePostsActivity.class).putStringArrayListExtra("post_ids",new ArrayList<>(ids)));
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); job=(Job)getLastNonConfigurationInstance(); boolean fresh=job==null;
        if(fresh) { job=new Job(); if(state!=null) { job.notes=state.getBoolean("notes"); job.contacts=state.getBoolean("contacts"); job.caption=state.getBoolean("caption"); } }
        job.screen=this; body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); int pad=UiStyles.dp(this,18);
        body.setPadding(pad,pad,pad,pad); body.setBackgroundColor(UiStyles.PAPER);
        body.setOnApplyWindowInsetsListener((v,insets)->{ v.setPadding(pad+insets.getSystemWindowInsetLeft(),pad+insets.getSystemWindowInsetTop(),pad+insets.getSystemWindowInsetRight(),pad+insets.getSystemWindowInsetBottom()); return insets; });
        ScrollView scroll=new ScrollView(this); scroll.addView(body); setContentView(scroll); render();
        if(fresh) load();
    }
    private void load() {
        ArrayList<String> ids=getIntent().getStringArrayListExtra("post_ids"); Job running=job; Context app=getApplicationContext();
        execute(()->{
            try(SQLiteSavedItemRepository repository=new SQLiteSavedItemRepository(app)) {
                if(ids==null || ids.isEmpty() || ids.size()>PostShare.MAX_POSTS) throw new IOException("Select 1–100 posts to share.");
                List<SavedItem> items=new ArrayList<>();
                for(String id:new LinkedHashSet<>(ids)) { SavedItem item=repository.find(id); if(item==null) throw new IOException("A selected post is no longer available."); items.add(item); }
                running.items=List.copyOf(items);
            } catch(Exception error) { running.message=error instanceof IOException?error.getMessage():"Could not load the selected posts."; }
            finally { running.busy=false; running.refresh(); }
        });
    }
    private PostShare.Options options() { return new PostShare.Options(job.notes,job.contacts,job.caption); }
    private void render() {
        body.removeAllViews(); body.addView(UiStyles.header(this,getString(R.string.share_posts),this::finish));
        if(!job.message.isEmpty()) { TextView status=UiStyles.text(this,job.message,15,UiStyles.MUTED); status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE); body.addView(status); }
        if(job.items.isEmpty()) { if(job.busy) body.addView(UiStyles.text(this,getString(R.string.share_loading),15,UiStyles.MUTED)); return; }
        body.addView(UiStyles.text(this,getString(R.string.share_explanation),14,UiStyles.MUTED));
        check(R.string.share_include_notes,job.notes,value->{ job.notes=value; });
        check(R.string.share_include_contacts,job.contacts,value->{ job.contacts=value; });
        check(R.string.share_include_caption,job.caption,value->{ job.caption=value; });
        body.addView(UiStyles.text(this,getString(R.string.share_privacy),13,UiStyles.MUTED));
        int photos=0; for(SavedItem item:job.items) photos+=item.media().size();
        body.addView(UiStyles.text(this,getString(R.string.share_counts,job.items.size(),photos),15,UiStyles.INK));
        body.addView(UiStyles.text(this,getString(R.string.share_preview),18,UiStyles.INK));
        String summary;
        try { summary=PostShare.summary(job.items,options()); } catch(IllegalArgumentException error) { body.addView(UiStyles.text(this,error.getMessage(),15,UiStyles.INK)); return; }
        TextView preview=UiStyles.text(this,summary,15,UiStyles.INK); preview.setTextIsSelectable(true); body.addView(preview);
        addButton(getString(R.string.share_summary),false,()->send(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,summary)),!job.busy);
        addButton(getString(job.busy?R.string.share_preparing:job.file==null?R.string.share_prepare_file:R.string.share_send_file),true,()->{
            if(job.file==null) prepare(); else {
                Uri uri=FileProvider.getUriForFile(this,getPackageName()+".sharedfiles",job.file);
                Intent intent=new Intent(Intent.ACTION_SEND).setType(PostShare.MIME).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setClipData(ClipData.newRawUri("Keepr posts",uri)); send(intent);
            }
        },!job.busy);
    }
    private void check(int label,boolean checked,java.util.function.Consumer<Boolean> change) {
        CheckBox view=new CheckBox(this); view.setText(label); view.setChecked(checked); view.setEnabled(!job.busy); body.addView(view);
        view.setOnCheckedChangeListener((button,value)->{ change.accept(value); job.file=null; job.message=""; render(); });
    }
    private void addButton(String label,boolean primary,Runnable click,boolean enabled) { Button button=UiStyles.button(this,label,primary); button.setEnabled(enabled); button.setOnClickListener(v->click.run()); body.addView(button,new LinearLayout.LayoutParams(-1,-2)); }
    private void prepare() {
        Job running=job; Context app=getApplicationContext(); PostShare.Options chosen=options(); running.busy=true; running.message=""; render();
        execute(()->{
            File file=null;
            try {
                File directory=new File(app.getCacheDir(),"share-exports"); if(!directory.isDirectory() && !directory.mkdirs()) throw new IOException();
                File[] previous=directory.listFiles(); if(previous!=null) for(File old:previous) if(old.lastModified()<System.currentTimeMillis()-7L*24*60*60*1000) old.delete();
                file=new File(directory,"Keepr-"+UUID.randomUUID()+".keepr");
                try(OutputStream out=new FileOutputStream(file)) { PostShare.write(running.items,chosen,app.getFilesDir(),out); }
                running.file=file;
            } catch(Exception error) { if(file!=null) file.delete(); running.message="Could not prepare the file. Check storage and saved photos, or select fewer posts."; }
            finally { running.busy=false; running.refresh(); }
        });
    }
    private void send(Intent intent) { try { startActivity(Intent.createChooser(intent,getString(R.string.share_posts))); } catch(ActivityNotFoundException | SecurityException error) { Toast.makeText(this,R.string.share_no_app,Toast.LENGTH_LONG).show(); } }
    private static void execute(Runnable task) { ExecutorService worker=Executors.newSingleThreadExecutor(); worker.execute(()->{ try { task.run(); } finally { worker.shutdown(); } }); }
    @Override public Object onRetainNonConfigurationInstance() { return job; }
    @Override protected void onSaveInstanceState(Bundle state) { state.putBoolean("notes",job.notes); state.putBoolean("contacts",job.contacts); state.putBoolean("caption",job.caption); super.onSaveInstanceState(state); }
    @Override protected void onDestroy() { if(job.screen==this) job.screen=null; super.onDestroy(); }
}
