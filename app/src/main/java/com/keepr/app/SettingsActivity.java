package com.keepr.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import com.keepr.app.data.AppUpdates;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.Executors;

/** Settings and an explicit, browser-assisted update flow from the official GitHub release. */
public final class SettingsActivity extends Activity {
    private TextView status,latest,checked;
    private Button check,download;
    private ProgressBar progress;
    private String installedVersion;
    private CheckJob job;

    private static final class CheckJob {
        volatile boolean checking;
        volatile String message="";
        volatile AppUpdates.Release release;
        volatile long checkedAt;
        volatile SettingsActivity screen;
        void refresh() {
            SettingsActivity current=screen;
            if(current!=null) current.runOnUiThread(()->{ if(screen==current && !current.isDestroyed()) current.render(); });
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try { installedVersion=getPackageManager().getPackageInfo(getPackageName(),0).versionName; }
        catch(android.content.pm.PackageManager.NameNotFoundException error) { installedVersion="Unknown"; }
        job=(CheckJob)getLastNonConfigurationInstance(); if(job==null) job=new CheckJob(); job.screen=this;
        LinearLayout root=column(); root.setBackgroundColor(UiStyles.PAPER);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets;
        });
        LinearLayout toolbar=UiStyles.header(this,"Settings",this::finish);
        toolbar.setPadding(dp(18),dp(8),dp(18),dp(8)); root.addView(toolbar);
        ScrollView scroll=new ScrollView(this); LinearLayout body=column(); body.setPadding(dp(18),dp(12),dp(18),dp(24)); scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout updates=card(body);
        android.graphics.drawable.GradientDrawable updateBackground=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{0xff3B151C,0xff171214,0xff111113});
        updateBackground.setCornerRadius(dp(18)); updateBackground.setStroke(dp(1),0xff5D222C); updates.setBackground(updateBackground);

        updates.addView(UiStyles.text(this,"App updates",19,UiStyles.INK));
        updates.addView(UiStyles.text(this,"Installed version · "+installedVersion,11,0xffDFA5AC));
        updates.addView(UiStyles.text(this,"Check the latest public version and download its APK directly from GitHub Releases.",13,UiStyles.MUTED));
        TextView privacyNote=UiStyles.text(this,"Connects only when you ask",11,0xffB9DCC8);
        privacyNote.setPadding(dp(11),dp(10),dp(11),dp(10)); privacyNote.setBackground(UiStyles.surface(this,0xaa120F10,10,0)); updates.addView(privacyNote);
        latest=UiStyles.text(this,"",14,UiStyles.ACCENT_TEXT); updates.addView(latest);
        status=UiStyles.text(this,"",16,UiStyles.INK); status.setId(R.id.update_status); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); updates.addView(status);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setIndeterminate(true); progress.setContentDescription("Checking for updates"); updates.addView(progress);
        checked=UiStyles.text(this,"",12,UiStyles.MUTED); updates.addView(checked);
        check=UiStyles.button(this,"Check for updates",true); check.setId(R.id.check_updates); addButton(updates,check); check.setOnClickListener(v->checkForUpdates());
        download=UiStyles.button(this,"Download update",true); download.setId(R.id.download_update); addButton(updates,download);
        download.setOnClickListener(v->{ AppUpdates.Release release=job.release; if(release!=null && !job.checking && !release.downloadUrl().isEmpty()) openWeb(release.downloadUrl()); });
        Button releases=UiStyles.button(this,"View GitHub releases  ↗",false); addButton(updates,releases);
        releases.setOnClickListener(v->openWeb(job.release==null?AppUpdates.RELEASES:job.release.pageUrl()));
        updates.addView(UiStyles.text(this,"Download the APK from GitHub, then open it to install. Compatible updates keep your saved posts and photos.",13,UiStyles.MUTED));

        body.addView(UiStyles.text(this,"DATA AND SECURITY",10,0xff706A6D));
        LinearLayout data=card(body); data.setPadding(dp(8),dp(6),dp(8),dp(6));
        data.addView(UiStyles.menuRow(this,"Backup and restore","Password-encrypted copies",R.drawable.ic_backup,false,()->startActivity(new Intent(this,BackupActivity.class))));
        data.addView(UiStyles.menuRow(this,"Trash","Restore deleted posts before expiry",R.drawable.ic_trash,false,()->startActivity(new Intent(this,TrashActivity.class))));
        Button privacy=UiStyles.button(this,"Privacy policy",false); addButton(data,privacy);
        privacy.setOnClickListener(v->showDocument("Keepr privacy policy",R.raw.privacy));
        Button onlinePrivacy=UiStyles.button(this,"Privacy policy online",false); addButton(data,onlinePrivacy);
        onlinePrivacy.setOnClickListener(v->openWeb("https://deborahfam.github.io/Keepr/"));

        LinearLayout about=card(body); about.addView(UiStyles.text(this,"About Keepr",21,UiStyles.INK));
        about.addView(UiStyles.text(this,"Made by deborahfam",16,UiStyles.INK));
        about.addView(UiStyles.text(this,"© 2026 deborahfam. Licensed under the Apache License, Version 2.0. Saved content belongs to its respective owners.",14,UiStyles.MUTED));
        Button creator=UiStyles.button(this,"Creator on GitHub  ↗",false); addButton(about,creator);
        creator.setOnClickListener(v->openWeb("https://github.com/deborahfam"));
        Button source=UiStyles.button(this,"Source code  ↗",false); addButton(about,source);
        source.setOnClickListener(v->openWeb("https://github.com/deborahfam/Keepr"));
        Button license=UiStyles.button(this,"Apache 2.0 license",false); addButton(about,license);
        license.setOnClickListener(v->showDocument("Apache License 2.0",R.raw.license));
        Button notices=UiStyles.button(this,"Open-source notices",false); addButton(about,notices);
        notices.setOnClickListener(v->showDocument("Open-source notices",R.raw.third_party_notices));
        setContentView(root); render();
    }

    private void showDocument(String title,int resource) {
        try(java.io.InputStream input=getResources().openRawResource(resource)) {
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            byte[] buffer=new byte[4096]; int count;
            while((count=input.read(buffer))!=-1) bytes.write(buffer,0,count);
            String text=new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
            TextView content=UiStyles.text(this,text,15,UiStyles.INK); content.setTextIsSelectable(true);
            content.setPadding(dp(18),dp(16),dp(18),dp(16));
            ScrollView scroll=new ScrollView(this); scroll.addView(content);
            new android.app.AlertDialog.Builder(this).setTitle(title).setView(scroll)
                    .setPositiveButton(android.R.string.ok,null).show();
        } catch(java.io.IOException error) { Toast.makeText(this,"Could not open this document.",Toast.LENGTH_LONG).show(); }
    }

    private void checkForUpdates() {
        if(job.checking) return;
        job.checking=true; job.release=null; job.message="Checking GitHub…"; render();
        CheckJob running=job; String installed=installedVersion;
        var worker=Executors.newSingleThreadExecutor();
        worker.execute(()->{
            try {
                AppUpdates.Release release=new AppUpdates().latest();
                int comparison=AppUpdates.compareVersions(release.version(),installed);
                running.release=release; running.checkedAt=System.currentTimeMillis();
                running.message=comparison==0?"You’re up to date.":comparison<0?"You’re running a version newer than the published release.":
                        release.downloadUrl().isEmpty()?"A new release is available. Its APK is not ready to download yet.":"An update is available.";
            } catch(AppUpdates.CheckFailure error) { running.message=error.getMessage(); }
            catch(Exception error) { running.message="Could not compare versions. Open GitHub releases to check manually."; }
            finally { running.checking=false; running.refresh(); worker.shutdown(); }
        });
    }
    private void render() {
        status.setText(job.message); status.setVisibility(job.message.isEmpty()?View.GONE:View.VISIBLE); check.setEnabled(!job.checking); check.setText(job.checking?"Checking…":"Check for updates");
        progress.setVisibility(job.checking?View.VISIBLE:View.GONE);
        AppUpdates.Release release=job.release;
        latest.setVisibility(release==null?View.GONE:View.VISIBLE);
        if(release!=null) latest.setText("Latest release · "+release.version());
        checked.setVisibility(job.checkedAt==0?View.GONE:View.VISIBLE);
        if(job.checkedAt!=0) checked.setText("Last successful check · "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(job.checkedAt)));
        boolean available=false;
        if(release!=null && !job.checking) try { available=AppUpdates.compareVersions(release.version(),installedVersion)>0 && !release.downloadUrl().isEmpty(); } catch(IllegalArgumentException ignored) { }
        download.setVisibility(available?View.VISIBLE:View.GONE);
        if(available) download.setText("Download "+release.version()+" · "+android.text.format.Formatter.formatShortFileSize(this,release.bytes()));
    }
    private void openWeb(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); }
        catch(ActivityNotFoundException error) { Toast.makeText(this,"Install a browser to open GitHub and download updates.",Toast.LENGTH_LONG).show(); }
    }
    private LinearLayout column() { LinearLayout view=new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout card=column(); card.setPadding(dp(18),dp(16),dp(18),dp(18)); card.setBackground(UiStyles.surface(this,UiStyles.SURFACE,20,UiStyles.LINE));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.bottomMargin=dp(18); parent.addView(card,params); return card;
    }
    private void addButton(LinearLayout parent,Button button) { LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.topMargin=dp(10); parent.addView(button,params); }
    private int dp(int value) { return UiStyles.dp(this,value); }
    @Override public Object onRetainNonConfigurationInstance() { return job; }
    @Override protected void onDestroy() { if(job!=null && job.screen==this) job.screen=null; super.onDestroy(); }
}
