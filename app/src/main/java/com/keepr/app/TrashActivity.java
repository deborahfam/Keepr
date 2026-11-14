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
    private boolean busy;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); repository=new SQLiteSavedItemRepository(this);
        body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); int pad=(int)(20*getResources().getDisplayMetrics().density);
        body.setOnApplyWindowInsetsListener((v,insets)->{ v.setPadding(pad+insets.getSystemWindowInsetLeft(),pad+insets.getSystemWindowInsetTop(),pad+insets.getSystemWindowInsetRight(),pad+insets.getSystemWindowInsetBottom()); return insets; });
        ScrollView scroll=new ScrollView(this); scroll.addView(body); setContentView(scroll); reload();
    }
    private void reload() {
        worker.execute(()->{
            try { repository.purgeExpired(); var posts=repository.trashContents(); int days=repository.retentionDays();
                runOnUiThread(()->{ if(isDestroyed()) return; body.removeAllViews();
                    addButton("Back to collection",this::finish); text("Trash",26);
                    text("Posts retain their photos until restored or permanently deleted. Expired posts are removed on opening Keepr or during daily background cleanup.",16);
                    addButton("Keep future deletions for "+days+" days",()->new AlertDialog.Builder(this).setTitle("Trash retention")
                        .setItems(new String[]{"7 days","30 days","90 days"},(d,index)->perform(()->repository.setRetentionDays(new int[]{7,30,90}[index]))).show());
                    text("Changing retention does not change existing expiry dates.",14);
                    if(posts.isEmpty()) text("Trash is empty",18);
                    for(var post:posts) {
                        text(post.title(),20); text("Expires "+java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(post.expiresAt())),14);
                        addButton("Restore",()->perform(()->{ repository.restoreTrash(post.id()); SavedItem item=repository.find(post.id()); if(item!=null && !item.url().isEmpty() && (item.previewState()==SavedItem.PreviewState.PENDING || repository.needsPhotoDiscovery(item.id()))) SnapshotWorker.enqueue(this,item.id()); }));
                        addButton("Delete permanently",()->new AlertDialog.Builder(this).setTitle("Permanently delete this post?").setMessage("Its saved photos will also be removed. This cannot be undone.").setNegativeButton(android.R.string.cancel,null).setPositiveButton("Delete permanently",(d,w)->perform(()->repository.delete(post.id()))).show());
                    }
                });
            } catch(Exception error) { runOnUiThread(()->Toast.makeText(this,"Could not read Trash",Toast.LENGTH_LONG).show()); }
        });
    }
    private void perform(Runnable action) { if(busy) return; busy=true; worker.execute(()->{ try { action.run(); } catch(Exception error) { runOnUiThread(()->Toast.makeText(this,"Could not update Trash. Please try again.",Toast.LENGTH_LONG).show()); } finally { runOnUiThread(()->{ busy=false; if(!isDestroyed()) reload(); }); } }); }
    private void text(String value,int size) { TextView text=new TextView(this); text.setText(value); text.setTextSize(size); text.setPadding(0,12,0,12); body.addView(text); }
    private void addButton(String label,Runnable action) { Button button=new Button(this); button.setText(label); button.setAllCaps(false); button.setOnClickListener(v->{ if(!busy) action.run(); }); body.addView(button); }
    @Override protected void onDestroy() { worker.execute(repository::close); worker.shutdown(); super.onDestroy(); }
}
