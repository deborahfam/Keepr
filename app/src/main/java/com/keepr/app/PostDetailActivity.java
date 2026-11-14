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
    private final int ink=0xff27223b,muted=0xff6b657a,purple=0xff6648ca,paper=0xfff8f6f2;
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

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); id=getIntent().getStringExtra("item_id");
        if(id==null) { finish(); return; }
        if(state!=null) { photoIndex=state.getInt("photo",0); tab=state.getString("tab","Post"); }
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
        TextView local=text("OFFLINE VAULT",10,purple); local.setTypeface(null,Typeface.BOLD); toolbar.addView(local); root.addView(toolbar);
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
                    progress=state; render(); });
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
        TextView location=text((item.categoryId()==null ? "Other" : item.categoryId())+"  ·  "+status(item.status()),13,purple); body.addView(location);
        if(!item.tags().isEmpty()) body.addView(text("#"+String.join("  #",item.tags()),13,purple));
        if(tab.equals("Post") || tab.equals("Photos")) {
            gallery();
            if(tab.equals("Post")) {
                if(!item.price().isEmpty()) body.addView(text(item.currency()+" "+item.price(),24,purple));
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
            empty.setPadding(dp(20),dp(30),dp(20),dp(30)); empty.setBackground(shape(0xffeee8fa,18)); body.addView(empty); return;
        }
        photoIndex=Math.max(0,Math.min(photoIndex,item.media().size()-1));
        CachedMedia media=item.media().get(photoIndex);
        ImageView photo=new ImageView(this); photo.setId(R.id.detail_photo); photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photo.setBackground(shape(0xffeee8fa,20)); photo.setClipToOutline(true);
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
        TextView counter=text(getString(R.string.gallery_position,photoIndex+1,item.media().size()),13,purple); counter.setId(R.id.gallery_counter); counter.setGravity(Gravity.CENTER);
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
        TextView caption=text(name.toUpperCase(Locale.ROOT),11,purple); caption.setTypeface(null,Typeface.BOLD); caption.setPadding(0,dp(22),0,dp(6)); body.addView(caption);
        TextView content=text(value,16,ink); content.setTextIsSelectable(true); content.setLineSpacing(dp(3),1); body.addView(content);
    }
    private String status(SavedItem.Status status) { String value=status.name().replace('_',' ').toLowerCase(Locale.ROOT); return value.substring(0,1).toUpperCase(Locale.ROOT)+value.substring(1); }
    private LinearLayout column() { LinearLayout result=new LinearLayout(this); result.setOrientation(LinearLayout.VERTICAL); return result; }
    private TextView text(String value,int size,int color) { TextView view=new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setPadding(0,dp(6),0,dp(6)); return view; }
    private GradientDrawable shape(int color,int radius) { GradientDrawable drawable=new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable; }
    private Button button(String text,boolean primary) {
        Button button=new Button(this); button.setText(text); button.setTextSize(14); button.setAllCaps(false); button.setTextColor(primary?Color.WHITE:purple);
        button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x226648ca),shape(primary?purple:0xffeee8fa,14),shape(Color.WHITE,14)));
        button.setMinWidth(0); button.setMinimumWidth(0); button.setPadding(dp(12),0,dp(12),0); button.setStateListAnimator(null); return button;
    }
    private LinearLayout.LayoutParams block() { LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(52)); params.topMargin=dp(14); return params; }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putInt("photo",photoIndex); state.putString("tab",tab); super.onSaveInstanceState(state); }
    @Override protected void onDestroy() { if(photoDialog!=null) { photoDialog.setOnDismissListener(null); photoDialog.dismiss(); } if(updates!=null && observer!=null) updates.removeObserver(observer); images.shutdownNow(); if(repository!=null) { database.execute(repository::close); database.shutdown(); } super.onDestroy(); }
}
