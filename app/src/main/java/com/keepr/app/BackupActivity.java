package com.keepr.app;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import com.keepr.app.data.*;
import com.keepr.app.domain.SavedItem;
import java.io.*;
import java.util.concurrent.*;

/** System document picker gives users control over where their portable backup lives. */
public final class BackupActivity extends Activity {
    private Button export,restore;
    private TextView status;
    private Job job;
    private static final class Job {
        volatile boolean busy;
        volatile String message="Choose where to save your backup, or select a backup to restore.";
        volatile BackupActivity screen;
        void refresh() { BackupActivity activity=screen; if(activity!=null) activity.runOnUiThread(()->{ if(screen==activity && !activity.isDestroyed()) activity.render(); }); }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        job=(Job)getLastNonConfigurationInstance(); if(job==null) job=new Job(); job.screen=this;
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); int pad=(int)(24*getResources().getDisplayMetrics().density); root.setPadding(pad,pad,pad,pad);
        root.setOnApplyWindowInsetsListener((view,insets)->{ view.setPadding(pad+insets.getSystemWindowInsetLeft(),pad+insets.getSystemWindowInsetTop(),pad+insets.getSystemWindowInsetRight(),pad+insets.getSystemWindowInsetBottom()); return insets; });
        root.setBackgroundColor(UiStyles.PAPER);
        Button back=UiStyles.button(this,"‹  Back to collection",false); back.setOnClickListener(v->finish()); root.addView(back);
        TextView heading=UiStyles.text(this,"Protect your collection",28,UiStyles.INK);
        heading.setPadding(0,pad,0,UiStyles.dp(this,8)); root.addView(heading);
        TextView explanation=UiStyles.text(this,getString(R.string.backup_explanation),15,UiStyles.MUTED); root.addView(explanation);
        LinearLayout panel=new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(pad,pad,pad,pad);
        panel.setBackground(UiStyles.surface(this,UiStyles.SURFACE,20,UiStyles.LINE));
        LinearLayout.LayoutParams panelParams=new LinearLayout.LayoutParams(-1,-2); panelParams.topMargin=pad; root.addView(panel,panelParams);
        panel.addView(UiStyles.text(this,"Keep a safe copy",20,UiStyles.INK));
        panel.addView(UiStyles.text(this,"Choose a location for your posts and saved photos.",14,UiStyles.MUTED));
        export=UiStyles.button(this,getString(R.string.export_backup),true); panel.addView(export,new LinearLayout.LayoutParams(-1,-2));
        TextView restoreTitle=UiStyles.text(this,"Bring your finds back",20,UiStyles.INK); restoreTitle.setPadding(0,pad,0,UiStyles.dp(this,6)); panel.addView(restoreTitle);
        panel.addView(UiStyles.text(this,"Add missing posts from a Keepr backup. Your current posts stay unchanged.",14,UiStyles.MUTED));
        restore=UiStyles.button(this,getString(R.string.restore_backup),false); panel.addView(restore,new LinearLayout.LayoutParams(-1,-2));
        status=UiStyles.text(this,"",14,UiStyles.MUTED); status.setPadding(0,pad,0,pad);
        status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE); root.addView(status);
        export.setOnClickListener(v->pick(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE,"Keepr-"+new java.text.SimpleDateFormat("yyyy-MM-dd-HHmmss",java.util.Locale.ROOT).format(new java.util.Date())+".zip"),71));
        restore.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(R.string.restore_backup).setMessage(R.string.restore_explanation)
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.choose_backup,(d,w)->pick(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),72)).show());
        ScrollView scroll=new ScrollView(this); scroll.addView(root); setContentView(scroll); render();
    }
    private void pick(Intent intent,int request) { try { startActivityForResult(intent,request); } catch(ActivityNotFoundException error) { job.message="No document picker is available on this device."; render(); } }
    private void render() { export.setEnabled(!job.busy); restore.setEnabled(!job.busy); status.setText(job.message); }
    @Override public Object onRetainNonConfigurationInstance() { return job; }
    @Override protected void onDestroy() { if(job.screen==this) job.screen=null; super.onDestroy(); }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if((request!=71 && request!=72) || result!=RESULT_OK || data==null || data.getData()==null || job.busy) return;
        Uri uri=data.getData(); Job running=job; Context app=getApplicationContext();
        running.busy=true; running.message=request==71?"Exporting posts and photos…":"Checking backup and restoring posts…"; render();
        ExecutorService worker=Executors.newSingleThreadExecutor();
        worker.execute(()->{
            try(SQLiteSavedItemRepository repository=new SQLiteSavedItemRepository(app)) {
                repository.load(); VaultBackup backup=new VaultBackup(app);
                if(request==71) {
                    try(OutputStream output=app.getContentResolver().openOutputStream(uri,"wt")) { if(output==null) throw new IOException("Could not open destination."); backup.exportTo(repository,output); }
                    running.message="Backup saved successfully.";
                } else {
                    VaultBackup.RestoreResult restored;
                    try(InputStream input=app.getContentResolver().openInputStream(uri)) { if(input==null) throw new IOException("Could not open backup."); restored=backup.restoreFrom(repository,input); }
                    running.message=restored.added()+" posts restored. "+restored.skipped()+" existing posts left unchanged.";
                    try { for(SavedItem item:repository.load()) if(!item.url().isEmpty() && (item.previewState()==SavedItem.PreviewState.PENDING || repository.needsPhotoDiscovery(item.id()))) SnapshotWorker.enqueue(app,item.id()); }
                    catch(Exception ignored) { running.message+=" Pending downloads will resume when you reopen Keepr."; }
                }
            } catch(Exception error) {
                running.message=request==71?"Backup failed. The selected file may be incomplete; please export again.":"Restore failed. Your existing collection was preserved. Select a complete Keepr backup and check available storage.";
            } finally { running.busy=false; running.refresh(); worker.shutdown(); }
        });
    }
}
