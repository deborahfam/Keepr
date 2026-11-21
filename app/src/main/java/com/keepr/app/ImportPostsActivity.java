package com.keepr.app;

import android.app.Activity;
import android.content.*;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import com.keepr.app.data.*;
import com.keepr.app.domain.ContactDetails;
import java.io.*;
import java.util.concurrent.*;

/** Untrusted incoming files are staged and reviewed before importing new copies. */
public final class ImportPostsActivity extends Activity {
    private Job job;
    private LinearLayout body;
    private static final class Job {
        volatile ImportPostsActivity screen;
        volatile boolean busy,done,abandoned;
        volatile String message="";
        volatile PostShare.Preview preview;
        String uri;
        boolean keepExisting=true;
        void refresh() { ImportPostsActivity activity=screen; if(activity!=null) activity.runOnUiThread(()->{ if(screen==activity && !activity.isDestroyed()) activity.render(); }); }
        void clean() { if(preview!=null) { preview.close(); preview=null; } }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); job=(Job)getLastNonConfigurationInstance(); boolean fresh=job==null;
        if(fresh) {
            job=new Job();
            if(state!=null) { job.uri=state.getString("uri"); job.done=state.getBoolean("done"); job.message=state.getString("message",""); job.keepExisting=state.getBoolean("keep",true); }
        }
        job.screen=this; body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); int pad=UiStyles.dp(this,18);
        body.setPadding(pad,pad,pad,pad); body.setBackgroundColor(UiStyles.PAPER);
        body.setOnApplyWindowInsetsListener((v,insets)->{ v.setPadding(pad+insets.getSystemWindowInsetLeft(),pad+insets.getSystemWindowInsetTop(),pad+insets.getSystemWindowInsetRight(),pad+insets.getSystemWindowInsetBottom()); return insets; });
        ScrollView scroll=new ScrollView(this); scroll.addView(body); setContentView(scroll); render();
        if(fresh && !job.done) {
            Uri uri=job.uri==null?incoming():Uri.parse(job.uri);
            if(uri!=null) read(uri);
        }
    }
    private Uri incoming() {
        if(Intent.ACTION_VIEW.equals(getIntent().getAction())) return getIntent().getData();
        if(Intent.ACTION_SEND.equals(getIntent().getAction())) {
            try { return getIntent().getParcelableExtra(Intent.EXTRA_STREAM); } catch(RuntimeException ignored) { return null; }
        } return null;
    }
    private void choose() {
        try { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),81); }
        catch(ActivityNotFoundException error) { job.message=getString(R.string.share_no_picker); render(); }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==81 && result==RESULT_OK && data!=null && data.getData()!=null && !job.busy) read(data.getData());
    }
    private void read(Uri uri) {
        if(!"content".equals(uri.getScheme())) { job.message=getString(R.string.share_invalid_file); render(); return; }
        Job running=job; Context app=getApplicationContext(); running.clean(); running.uri=uri.toString(); running.busy=true; running.message=""; render();
        execute(()->{
            try(InputStream input=app.getContentResolver().openInputStream(uri)) {
                if(input==null) throw new IOException();
                PostShare.Preview preview=PostShare.read(input,app.getCacheDir()); running.preview=preview;
                for(PostShare.Post post:preview.posts()) for(PostShare.Photo photo:post.photos()) {
                    BitmapFactory.Options options=new BitmapFactory.Options(); options.inJustDecodeBounds=true;
                    BitmapFactory.decodeFile(new File(preview.directory(),photo.entry()).getPath(),options);
                    if(options.outWidth<=0 || options.outHeight<=0 || (long)options.outWidth*options.outHeight>100_000_000L) throw new IOException();
                }
            } catch(Exception error) { running.clean(); running.message=app.getString(R.string.share_invalid_file); }
            finally { running.busy=false; if(running.abandoned) running.clean(); running.refresh(); }
        });
    }
    private void render() {
        body.removeAllViews();
        var header=UiStyles.header(this,getString(R.string.share_import_title),()->{
            if(job.busy) return;
            if(job.done) startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish();
        });
        header.getChildAt(0).setEnabled(!job.busy); body.addView(header);
        if(!job.message.isEmpty()) { TextView message=UiStyles.text(this,job.message,15,UiStyles.INK); message.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE); body.addView(message); }
        if(job.busy) { body.addView(UiStyles.text(this,getString(R.string.share_working),15,UiStyles.MUTED)); return; }
        if(job.done) {
            button(getString(R.string.share_open_collection),()->{ startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); },true);
            return;
        }
        if(job.preview==null) { body.addView(UiStyles.text(this,getString(R.string.share_import_help),15,UiStyles.MUTED)); button(getString(R.string.share_choose_file),this::choose,true); return; }
        body.addView(UiStyles.text(this,getString(R.string.share_import_explanation),15,UiStyles.MUTED));
        for(PostShare.Post post:job.preview.posts()) {
            body.addView(UiStyles.text(this,post.title(),20,UiStyles.INK));
            StringBuilder description=new StringBuilder();
            line(description,post.url()); line(description,(post.currency()+" "+post.price()).trim()+(post.period().isEmpty()?"":" / "+post.period()));
            line(description,post.location()); line(description,post.productType()); line(description,post.description());
            if(!post.notes().isEmpty()) line(description,"Notes: "+post.notes());
            ContactDetails c=post.contacts(); line(description,c.name()); line(description,c.phone()); line(description,c.email()); line(description,c.whatsapp());
            line(description,getResources().getQuantityString(R.plurals.share_photos,post.photos().size(),post.photos().size()));
            TextView details=UiStyles.text(this,description.toString(),15,UiStyles.MUTED); details.setTextIsSelectable(true); body.addView(details);
        }
        CheckBox duplicates=new CheckBox(this); duplicates.setText(R.string.share_keep_existing); duplicates.setChecked(job.keepExisting); duplicates.setOnCheckedChangeListener((b,value)->job.keepExisting=value); body.addView(duplicates);
        body.addView(UiStyles.text(this,getString(R.string.share_duplicates_help),13,UiStyles.MUTED));
        button(getString(R.string.share_import_copies),this::save,true);
        button(getString(R.string.share_choose_another),this::choose,true);
    }
    private static void line(StringBuilder text,String value) { if(!value.isEmpty()) text.append(value).append('\n'); }
    private void save() {
        Job running=job; Context app=getApplicationContext(); boolean keep=job.keepExisting; running.busy=true; running.message=""; render();
        execute(()->{
            try(SQLiteSavedItemRepository repository=new SQLiteSavedItemRepository(app)) {
                repository.initialize(); int total=running.preview.posts().size(); int added=repository.importShared(running.preview,keep);
                running.done=true; running.message=app.getString(R.string.share_import_result,added,total-added); running.clean();
            } catch(Exception error) { running.message=app.getString(R.string.share_import_failed); }
            finally { running.busy=false; if(running.abandoned) running.clean(); running.refresh(); }
        });
    }
    private void button(String label,Runnable click,boolean enabled) { Button button=UiStyles.button(this,label,false); button.setEnabled(enabled); button.setOnClickListener(v->click.run()); body.addView(button,new LinearLayout.LayoutParams(-1,-2)); }
    private static void execute(Runnable task) { ExecutorService worker=Executors.newSingleThreadExecutor(); worker.execute(()->{ try { task.run(); } finally { worker.shutdown(); } }); }
    @Override public Object onRetainNonConfigurationInstance() { return job; }
    @Override protected void onSaveInstanceState(Bundle state) { state.putString("uri",job.uri); state.putBoolean("done",job.done); state.putString("message",job.message); state.putBoolean("keep",job.keepExisting); super.onSaveInstanceState(state); }
    @Override protected void onDestroy() { if(job.screen==this) job.screen=null; if(isFinishing()) { job.abandoned=true; if(!job.busy) job.clean(); } super.onDestroy(); }
}
