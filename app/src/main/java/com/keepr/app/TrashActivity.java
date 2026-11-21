package com.keepr.app;
import android.app.*;
import android.os.Bundle;
import android.widget.*;
import com.keepr.app.data.*;
import com.keepr.app.domain.SavedItem;
import java.util.concurrent.*;

public final class TrashActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private SQLiteSavedItemRepository repository;
    private LinearLayout body;
    private LinearLayout destination;
    private boolean busy;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); repository=new SQLiteSavedItemRepository(this);
        body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); int pad=(int)(18*getResources().getDisplayMetrics().density);
        body.setOnApplyWindowInsetsListener((v,insets)->{ v.setPadding(pad+insets.getSystemWindowInsetLeft(),pad+insets.getSystemWindowInsetTop(),pad+insets.getSystemWindowInsetRight(),pad+insets.getSystemWindowInsetBottom()); return insets; });
        body.setBackgroundColor(UiStyles.PAPER);
        ScrollView scroll=new ScrollView(this); scroll.setBackgroundColor(UiStyles.PAPER); scroll.addView(body); setContentView(scroll); reload();
    }
    private void reload() {
        worker.execute(()->{
            try { repository.purgeExpired(); var posts=repository.trashContents(); int days=repository.retentionDays();
                runOnUiThread(()->{ if(isDestroyed()) return; body.removeAllViews(); destination=body;
                    addButton("‹  Back to collection",this::finish); text("Trash",26);
                    text("A second chance for your finds. Restore posts with their photos before they expire.",16);
                    addButton("Keep future deletions for "+days+" days",()->new AlertDialog.Builder(this).setTitle("Trash retention")
                        .setItems(new String[]{"7 days","30 days","90 days"},(d,index)->perform(()->repository.setRetentionDays(new int[]{7,30,90}[index]))).show());
                    text("Changing retention does not change existing expiry dates.",14);
                    if(posts.isEmpty()) {
                        text("All clear",24); text("Deleted finds will appear here. Your collection is waiting for you.",16);
                    }
                    for(var post:posts) {
                        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
                        int space=UiStyles.dp(this,18); card.setPadding(space,space,space,space);
                        card.setBackground(UiStyles.surface(this,UiStyles.SURFACE,14,UiStyles.LINE));
                        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.topMargin=UiStyles.dp(this,16);
                        body.addView(card,params); destination=card;
                        text(post.title(),20); text("Expires "+java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(post.expiresAt())),14);
                        addButton("Restore",()->perform(()->{ repository.restoreTrash(post.id()); SavedItem item=repository.find(post.id()); if(item!=null && !item.url().isEmpty() && (item.previewState()==SavedItem.PreviewState.PENDING || repository.needsPhotoDiscovery(item.id()))) SnapshotWorker.enqueue(this,item.id()); }));
                        addButton("Delete permanently",()->new AlertDialog.Builder(this).setTitle("Permanently delete this post?").setMessage("Its saved photos will also be removed. This cannot be undone.").setNegativeButton(android.R.string.cancel,null).setPositiveButton("Delete permanently",(d,w)->perform(()->repository.delete(post.id()))).show());
                    }
                });
            } catch(Exception error) { runOnUiThread(()->Toast.makeText(this,"Could not read Trash",Toast.LENGTH_LONG).show()); }
        });
    }
    private void perform(Runnable action) { if(busy) return; busy=true; worker.execute(()->{ try { action.run(); } catch(Exception error) { runOnUiThread(()->Toast.makeText(this,"Could not update Trash. Please try again.",Toast.LENGTH_LONG).show()); } finally { runOnUiThread(()->{ busy=false; if(!isDestroyed()) reload(); }); } }); }
    private void text(String value,int size) { TextView text=UiStyles.text(this,value,size,size>=20?UiStyles.INK:UiStyles.MUTED); destination.addView(text); }
    private void addButton(String label,Runnable action) { Button button=UiStyles.button(this,label,label.equals("Restore"));
        if(label.equals("Delete permanently")) { button.setTextColor(UiStyles.ACCENT_TEXT); button.setBackground(UiStyles.surface(this,UiStyles.SOFT,14,0)); } button.setOnClickListener(v->{ if(!busy) action.run(); }); LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.topMargin=UiStyles.dp(this,8); destination.addView(button,params); }
    @Override protected void onDestroy() { worker.execute(repository::close); worker.shutdown(); super.onDestroy(); }
}
