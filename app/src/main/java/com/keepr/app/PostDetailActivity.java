package com.keepr.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.lifecycle.*;
import androidx.work.*;
import com.keepr.app.data.*;
import com.keepr.app.domain.*;
import java.io.File;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Offline item detail, lazy photo carousel, and capture diagnostics. */
public final class PostDetailActivity extends Activity {
    private final int ink=UiStyles.INK,muted=UiStyles.MUTED,accent=UiStyles.ACCENT_TEXT,paper=UiStyles.PAPER;
    private final ExecutorService database=Executors.newSingleThreadExecutor(),images=Executors.newSingleThreadExecutor();
    private SQLiteSavedItemRepository repository;
    private SavedItem item;
    private SQLiteSavedItemRepository.PhotoProgress progress;
    private String id,tab="Post";
    private int photoIndex=0;
    private LinearLayout body,tabs;
    private ScrollView scroll;
    private TextView heading;
    private LiveData<List<WorkInfo>> updates;
    private androidx.lifecycle.Observer<List<WorkInfo>> observer;
    private Dialog photoDialog;
    private AlertDialog editDialog,statusDialog;
    private EditText editTitle,editNotes,editTags;
    private AutoCompleteTextView editFolder;
    private Bundle pendingEdit;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); id=getIntent().getStringExtra("item_id");
        if(id==null) { finish(); return; }
        if(state!=null) { photoIndex=state.getInt("photo",0); tab=state.getString("tab","Post"); }
        if(state!=null && state.getBoolean("editing")) pendingEdit=new Bundle(state);
        repository=new SQLiteSavedItemRepository(this);
        LinearLayout root=column(); root.setBackgroundColor(paper);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets;
        });
        LinearLayout toolbar=new LinearLayout(this); toolbar.setGravity(Gravity.CENTER_VERTICAL); toolbar.setPadding(dp(16),dp(8),dp(16),dp(8));
        Button back=button("‹",false); back.setTextSize(28); back.setContentDescription(getString(R.string.back_to_collection));
        back.setOnClickListener(v->finish()); toolbar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView title=text("Saved find",18,ink); title.setTypeface(null,Typeface.BOLD);
        LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(0,-2,1); titleParams.leftMargin=dp(12); toolbar.addView(title,titleParams);
        TextView local=text("OFFLINE VAULT",10,accent); local.setTypeface(null,Typeface.BOLD); toolbar.addView(local); root.addView(toolbar);
        tabs=new LinearLayout(this); tabs.setPadding(dp(24),dp(4),dp(24),dp(8)); root.addView(tabs);
        scroll=new ScrollView(this); body=column(); body.setPadding(dp(24),dp(12),dp(24),dp(24)); scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root); renderTabs(); body.addView(text("Opening saved post…",16,muted));
        updates=WorkManager.getInstance(this).getWorkInfosByTagLiveData("keepr-snapshot");
        observer=infos->reload(); updates.observeForever(observer); reload();
    }
    private void reload() {
        if(isDestroyed()) return;
        database.execute(()->{
            try {
                SavedItem loaded=repository.find(id); SQLiteSavedItemRepository.PhotoProgress state=repository.photoProgress(id);
                runOnUiThread(()->{ if(isDestroyed()) return; if(loaded==null) { finish(); return; }
                    String selected=item!=null && photoIndex<item.media().size() ? item.media().get(photoIndex).id() : null;
                    item=loaded;
                    if(selected!=null) for(int i=0;i<item.media().size();i++) if(item.media().get(i).id().equals(selected)) photoIndex=i;
                    progress=state; render(); if(pendingEdit!=null) { Bundle draft=pendingEdit; pendingEdit=null; showEdit(draft); } });
            } catch(Exception error) { runOnUiThread(()->{ if(!isDestroyed()) Toast.makeText(this,"Could not open this saved post",Toast.LENGTH_LONG).show(); }); }
        });
    }
    private void renderTabs() {
        tabs.removeAllViews();
        for(String name:new String[]{"Post","Photos","Info"}) {
            Button button=button(name,tab.equals(name)); button.setId(name.equals("Post")?R.id.detail_tab_post:name.equals("Photos")?R.id.detail_tab_photos:R.id.detail_tab_info); button.setSelected(tab.equals(name));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(48),1); params.rightMargin=dp(6);
            tabs.addView(button,params); button.setOnClickListener(v->{ tab=name; renderTabs(); render(); scroll.scrollTo(0,0); });
        }
    }
    private void render() {
        if(item==null) return;
        body.removeAllViews();
        heading=text(item.title(),28,ink); heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); body.addView(heading);
        TextView location=text((item.categoryId()==null ? "Other" : item.categoryId())+"  ·  "+status(item.status()),13,accent); body.addView(location);
        if(!item.tags().isEmpty()) body.addView(text("#"+String.join("  #",item.tags()),13,accent));
        if(tab.equals("Post") || tab.equals("Photos")) {
            gallery();
            if(tab.equals("Post")) {
                if(!item.price().isEmpty()) body.addView(text(item.currency()+" "+item.price(),24,accent));
                section("Publication",item.description().isEmpty() ? item.sharedText() : item.description());
                if(!item.sharedText().isEmpty() && !item.sharedText().equals(item.description())) section("Original shared text",item.sharedText());
                section("My notes",item.notes());
                if(!item.contact().isEmpty()) section("Contact",item.contact());
            } else {
                section("Photo downloads",captureSummary());
                body.addView(text("Swipe the photo or use the arrows. Tap it to zoom. Photos with a local copy remain available offline.",14,muted));
                if(progress.failed()>0 || progress.unexposed()>0 || !item.previewError().isEmpty()) section("Unavailable content",item.previewError());
            }
        } else {
            section("Folder",item.categoryId()==null ? "Other" : item.categoryId());
            section("Tags",item.tags().isEmpty() ? "No tags" : String.join(", ",item.tags()));
            section("Tracking status",status(item.status()));
            section("Saved on",DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(item.createdAt())));
            section("Last updated",DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(item.updatedAt())));
            section("Photo downloads",captureSummary());
            long bytes=0; for(CachedMedia photo:item.media()) bytes+=photo.byteCount();
            section("Photo storage",android.text.format.Formatter.formatShortFileSize(this,bytes)+" on this device");
            section("Source",item.siteName().isEmpty() ? "Shared content" : item.siteName());
            section("Original link",item.url()); section("Capture notes",item.previewError());
        }
        Button changeStatus=button(getString(R.string.change_status)+": "+StatusLabels.label(this,item.status()),false);
        changeStatus.setId(R.id.change_status); body.addView(changeStatus,block()); changeStatus.setOnClickListener(v->showStatusPicker());
        Button edit=button(getString(R.string.edit_post),false); body.addView(edit,block());
        edit.setOnClickListener(v->showEdit(null));
        Button addPhotos=button(getString(R.string.add_photos),false); body.addView(addPhotos,block());
        addPhotos.setOnClickListener(v->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true),42));
        if(!item.url().isEmpty()) {
            Button open=button(getString(R.string.open_link),true); body.addView(open,block());
            open.setOnClickListener(v->{ try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(item.url()))); }
                catch(ActivityNotFoundException e) { Toast.makeText(this,"No browser installed",Toast.LENGTH_SHORT).show(); } });
            Button retry=button(getString(R.string.retry_missing_photos),false); body.addView(retry,block());
            retry.setEnabled(item.previewState()!=SavedItem.PreviewState.PENDING);
            retry.setAlpha(retry.isEnabled()?1f:0.5f);
            retry.setOnClickListener(v->{ retry.setEnabled(false); database.execute(()->{
                repository.retryPhotos(id); SnapshotWorker.enqueue(this,id); runOnUiThread(this::reload);
            }); });
        }
    }
    private void showStatusPicker() {
        int[] selected={item.status().ordinal()};
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(R.string.change_status)
                .setSingleChoiceItems(StatusLabels.choices(this),selected[0],(d,which)->selected[0]=which)
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.save_changes,null).create();
        statusDialog=dialog;
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            SavedItem.Status status=SavedItem.Status.values()[selected[0]]; dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            database.execute(()->{
                try { repository.setStatus(id,status); runOnUiThread(()->{ if(!isDestroyed()) { dialog.dismiss(); reload(); Toast.makeText(this,R.string.status_saved,Toast.LENGTH_SHORT).show(); } }); }
                catch(Exception error) { runOnUiThread(()->{ if(!isDestroyed()) { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); Toast.makeText(this,"Could not update status. Please try again.",Toast.LENGTH_LONG).show(); } }); }
            });
        })); dialog.show();
    }
    private void showEdit(Bundle draft) {
        if(item==null || (editDialog!=null && editDialog.isShowing())) return;
        LinearLayout form=column(); form.setPadding(dp(20),dp(12),dp(20),dp(12));
        editTitle=editField(form,R.string.edit_title,draft==null?item.title():draft.getString("edit_title"),false);
        editNotes=editField(form,R.string.edit_notes,draft==null?item.notes():draft.getString("edit_notes"),true);
        form.addView(text(getString(R.string.edit_folder),14,ink)); editFolder=new AutoCompleteTextView(this);
        editFolder.setSingleLine(true); editFolder.setThreshold(0);
        editFolder.setText(draft==null?(item.categoryId()==null?"Other":item.categoryId()):draft.getString("edit_folder")); form.addView(editFolder);
        editTags=editField(form,R.string.edit_tags,draft==null?String.join(", ",item.tags()):draft.getString("edit_tags"),false);
        database.execute(()->{ List<String> folders=repository.folders(); runOnUiThread(()->{ if(!isDestroyed() && editDialog!=null && editDialog.isShowing()) {
            editFolder.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_dropdown_item_1line,folders));
            editFolder.setOnClickListener(v->editFolder.showDropDown());
        } }); });
        ScrollView scroll=new ScrollView(this); scroll.addView(form);
        editDialog=new AlertDialog.Builder(this).setTitle(R.string.edit_post).setView(scroll).setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.save_changes,null).create();
        editDialog.setOnShowListener(d->editDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String title=editTitle.getText().toString(),notes=editNotes.getText().toString(),folder=editFolder.getText().toString(),tags=editTags.getText().toString();
            AlertDialog dialog=editDialog; dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            database.execute(()->{
                try { repository.edit(id,title,notes,folder,tags); runOnUiThread(()->{ if(!isDestroyed()) { dialog.dismiss(); reload(); Toast.makeText(this,"Changes saved",Toast.LENGTH_SHORT).show(); } }); }
                catch(Exception error) { String message=error instanceof IllegalArgumentException || error instanceof IllegalStateException?error.getMessage():"Could not save changes. Please try again.";
                    runOnUiThread(()->{ if(!isDestroyed()) { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); Toast.makeText(this,message,Toast.LENGTH_LONG).show(); } }); }
            });
        })); editDialog.show();
    }
    private EditText editField(LinearLayout form,int label,String value,boolean multiline) {
        form.addView(text(getString(label),14,ink)); EditText input=new EditText(this); input.setSingleLine(!multiline); if(multiline) input.setMinLines(3);
        input.setText(value); input.setContentDescription(getString(label)); form.addView(input); return input;
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=42 || result!=RESULT_OK || data==null) return;
        ArrayList<Uri> uris=new ArrayList<>();
        if(data.getClipData()!=null) for(int i=0;i<data.getClipData().getItemCount();i++) {
            Uri uri=data.getClipData().getItemAt(i).getUri(); if(uri!=null && !uris.contains(uri)) uris.add(uri);
        }
        if(data.getData()!=null && !uris.contains(data.getData())) uris.add(data.getData());
        database.execute(()->{
            int saved=0,failed=0;
            for(Uri uri:uris) {
                CachedMedia photo=null;
                try { photo=new MediaCache(this).copyShared(id,uri); repository.attach(photo); saved++; }
                catch(Exception error) { if(photo!=null) new File(getFilesDir(),photo.relativePath()).delete(); failed++; }
            }
            SQLiteSavedItemRepository.PhotoProgress state=repository.photoProgress(id);
            if(state.pending()==0) repository.previewState(id,state.unexposed()>0 || state.failed()>0 ? SavedItem.PreviewState.FAILED : SavedItem.PreviewState.READY,
                    state.unexposed()>0 ? state.unexposed()+" source photos remain unavailable. You can add them from your phone." : "");
            String message=saved+" photos added"+(failed>0?" · "+failed+" could not be copied":"");
            runOnUiThread(()->{ if(!isDestroyed()) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); reload(); } });
        });
    }
    private String captureSummary() {
        String result=progress.saved()+" of "+progress.total()+" discovered photos saved";
        if(progress.pending()>0) result+=" · "+progress.pending()+" queued";
        if(progress.unexposed()>0) result+=" · "+progress.unexposed()+" not exposed by source";
        if(progress.failed()>0) result+=" · "+progress.failed()+" unavailable";
        if(item.previewState()==SavedItem.PreviewState.PENDING && progress.total()==0) result="Discovering available public photos…";
        return result;
    }
    private void gallery() {
        TextView summary=text(captureSummary(),12,muted); body.addView(summary);
        if(item.media().isEmpty()) {
            TextView empty=text(item.previewState()==SavedItem.PreviewState.PENDING ? "Photos are being fetched. You can leave this screen; downloads continue in the background." : "No photos have been saved for this post.",15,muted);
            empty.setPadding(dp(20),dp(30),dp(20),dp(30)); empty.setBackground(shape(UiStyles.SOFT,18)); body.addView(empty); return;
        }
        photoIndex=Math.max(0,Math.min(photoIndex,item.media().size()-1));
        CachedMedia media=item.media().get(photoIndex);
        ImageView photo=new ImageView(this); photo.setId(R.id.detail_photo); photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photo.setBackground(shape(UiStyles.SOFT,20)); photo.setClipToOutline(true);
        photo.setContentDescription(getString(R.string.gallery_position,photoIndex+1,item.media().size()));
        photo.setOnClickListener(v->showPhoto());
        body.addView(photo,new LinearLayout.LayoutParams(-1,dp(270))); load(photo,media,1000);
        GestureDetector gestures=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) { photo.performClick(); return true; }
            @Override public boolean onFling(MotionEvent start,MotionEvent end,float velocityX,float velocityY) {
                if(start!=null && Math.abs(end.getX()-start.getX())>dp(45) && Math.abs(velocityX)>Math.abs(velocityY)) {
                    move(end.getX()<start.getX()?1:-1); return true;
                } return false;
            }
        });
        photo.setOnTouchListener((v,event)->{
            if(event.getAction()==MotionEvent.ACTION_DOWN) v.getParent().requestDisallowInterceptTouchEvent(true);
            if(event.getAction()==MotionEvent.ACTION_UP || event.getAction()==MotionEvent.ACTION_CANCEL) v.getParent().requestDisallowInterceptTouchEvent(false);
            return gestures.onTouchEvent(event);
        });
        LinearLayout controls=new LinearLayout(this); controls.setGravity(Gravity.CENTER_VERTICAL);
        Button previous=button("‹",false); previous.setId(R.id.previous_photo); previous.setTextSize(24); previous.setContentDescription(getString(R.string.previous_photo));
        previous.setEnabled(photoIndex>0); previous.setAlpha(previous.isEnabled()?1f:0.35f); controls.addView(previous,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView counter=text(getString(R.string.gallery_position,photoIndex+1,item.media().size()),13,accent); counter.setId(R.id.gallery_counter); counter.setGravity(Gravity.CENTER);
        controls.addView(counter,new LinearLayout.LayoutParams(0,-2,1));
        Button next=button("›",false); next.setId(R.id.next_photo); next.setTextSize(24); next.setContentDescription(getString(R.string.next_photo));
        next.setEnabled(photoIndex<item.media().size()-1); next.setAlpha(next.isEnabled()?1f:0.35f); controls.addView(next,new LinearLayout.LayoutParams(dp(48),dp(48)));
        previous.setOnClickListener(v->move(-1)); next.setOnClickListener(v->move(1));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(56)); params.topMargin=dp(6); body.addView(controls,params);
    }
    private void move(int delta) { int next=photoIndex+delta; if(next>=0 && next<item.media().size()) { photoIndex=next; render(); } }
    private void showPhoto() {
        Dialog dialog=new Dialog(this,android.R.style.Theme_Material_NoActionBar);
        LinearLayout root=column(); root.setBackgroundColor(Color.BLACK);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets;
        });
        TextView hint=text("Pinch to zoom · double tap to reset",13,Color.WHITE); hint.setGravity(Gravity.CENTER); root.addView(hint);
        ZoomPhotoView photo=new ZoomPhotoView(this); photo.setId(R.id.zoom_photo_view); root.addView(photo,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=new LinearLayout(this); actions.setPadding(dp(16),dp(8),dp(16),dp(8));
        Button previous=button("‹",false),next=button("›",false),close=button("Close",true);
        TextView count=text("",13,Color.WHITE); count.setGravity(Gravity.CENTER);
        actions.addView(previous,new LinearLayout.LayoutParams(dp(48),dp(48))); actions.addView(count,new LinearLayout.LayoutParams(0,dp(48),1));
        actions.addView(next,new LinearLayout.LayoutParams(dp(48),dp(48))); actions.addView(close,new LinearLayout.LayoutParams(-2,dp(48))); root.addView(actions);
        previous.setContentDescription(getString(R.string.previous_photo)); next.setContentDescription(getString(R.string.next_photo));
        Runnable update=()->{
            String mediaId=item.media().get(photoIndex).id(); photo.setTag(mediaId); photo.clear();
            count.setText(getString(R.string.gallery_position,photoIndex+1,item.media().size()));
            previous.setEnabled(photoIndex>0); next.setEnabled(photoIndex<item.media().size()-1);
            String path=new File(getFilesDir(),item.media().get(photoIndex).relativePath()).getPath();
            images.execute(()->{
                Bitmap bitmap=decode(path,1800);
                runOnUiThread(()->{ if(!isDestroyed() && dialog.isShowing() && mediaId.equals(photo.getTag())) { photo.setPhoto(bitmap); } });
            });
        };
        previous.setOnClickListener(v->{ if(photoIndex>0) { photoIndex--; update.run(); } });
        next.setOnClickListener(v->{ if(photoIndex<item.media().size()-1) { photoIndex++; update.run(); } });
        close.setOnClickListener(v->dialog.dismiss()); dialog.setOnDismissListener(d->{ photo.clear(); if(!isDestroyed()) render(); });
        photoDialog=dialog; dialog.setContentView(root); dialog.show();
        if(dialog.getWindow()!=null) dialog.getWindow().setLayout(-1,-1); update.run();
    }
    private void load(ImageView view,CachedMedia media,int pixels) {
        view.setTag(media.id()); images.execute(()->{
            Bitmap bitmap=decode(new File(getFilesDir(),media.relativePath()).getPath(),pixels);
            runOnUiThread(()->{ if(!isDestroyed() && media.id().equals(view.getTag()) && bitmap!=null) view.setImageBitmap(bitmap); });
        });
    }
    private Bitmap decode(String path,int max) {
        BitmapFactory.Options options=new BitmapFactory.Options(); options.inJustDecodeBounds=true; BitmapFactory.decodeFile(path,options);
        options.inSampleSize=1; while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>max) options.inSampleSize*=2;
        options.inJustDecodeBounds=false; return BitmapFactory.decodeFile(path,options);
    }
    private void section(String name,String value) {
        if(value==null || value.isEmpty()) return;
        TextView caption=text(name,13,accent); caption.setTypeface(null,Typeface.BOLD); caption.setPadding(0,dp(22),0,dp(6)); body.addView(caption);
        TextView content=text(value,16,ink); content.setTextIsSelectable(true); content.setLineSpacing(dp(3),1); body.addView(content);
    }
    private String status(SavedItem.Status status) { return StatusLabels.label(this,status); }
    private LinearLayout column() { LinearLayout result=new LinearLayout(this); result.setOrientation(LinearLayout.VERTICAL); return result; }
    private TextView text(String value,int size,int color) { TextView view=new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setPadding(0,dp(6),0,dp(6)); return view; }
    private GradientDrawable shape(int color,int radius) { GradientDrawable drawable=new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable; }
    private Button button(String text,boolean primary) {
        return UiStyles.button(this,text,primary);
    }
    private LinearLayout.LayoutParams block() { LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.topMargin=dp(14); return params; }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    @Override protected void onSaveInstanceState(Bundle state) { if(editDialog!=null && editDialog.isShowing()) {
        state.putBoolean("editing",true); state.putString("edit_title",editTitle.getText().toString()); state.putString("edit_notes",editNotes.getText().toString());
        state.putString("edit_folder",editFolder.getText().toString()); state.putString("edit_tags",editTags.getText().toString());
    } state.putInt("photo",photoIndex); state.putString("tab",tab); super.onSaveInstanceState(state); }
    @Override protected void onDestroy() { if(statusDialog!=null) statusDialog.dismiss(); if(editDialog!=null) editDialog.dismiss(); if(photoDialog!=null) { photoDialog.setOnDismissListener(null); photoDialog.dismiss(); } if(updates!=null && observer!=null) updates.removeObserver(observer); images.shutdownNow(); if(repository!=null) { database.execute(repository::close); database.shutdown(); } super.onDestroy(); }
}
