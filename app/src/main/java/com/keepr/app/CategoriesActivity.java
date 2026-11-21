package com.keepr.app;

import android.app.*;
import android.content.Context;
import android.os.Bundle;
import android.text.*;
import android.view.Gravity;
import android.widget.*;
import com.keepr.app.data.SQLiteSavedItemRepository;
import java.util.*;
import java.util.concurrent.*;

/** Category management uses stable IDs; removing a category only moves its contents. */
public final class CategoriesActivity extends Activity {
    private Job job;
    private LinearLayout body;
    private AlertDialog dialog;
    private static final class Job {
        volatile CategoriesActivity screen;
        volatile boolean busy=true;
        volatile List<SQLiteSavedItemRepository.CategorySummary> categories=List.of();
        volatile String message="";
        boolean editing;
        String editId,draft="",removeId,destinationId;
        void refresh() { CategoriesActivity activity=screen; if(activity!=null) activity.runOnUiThread(()->{ if(screen==activity && !activity.isDestroyed()) activity.render(); }); }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); job=(Job)getLastNonConfigurationInstance(); boolean fresh=job==null;
        if(fresh) {
            job=new Job();
            if(state!=null) { job.editing=state.getBoolean("editing"); job.editId=state.getString("edit_id"); job.draft=state.getString("draft",""); job.removeId=state.getString("remove_id"); job.destinationId=state.getString("destination_id"); }
        }
        job.screen=this;
        body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setBackgroundColor(UiStyles.PAPER); int pad=UiStyles.dp(this,18);
        body.setPadding(pad,pad,pad,pad);
        body.setOnApplyWindowInsetsListener((v,insets)->{ v.setPadding(pad+insets.getSystemWindowInsetLeft(),pad+insets.getSystemWindowInsetTop(),pad+insets.getSystemWindowInsetRight(),pad+insets.getSystemWindowInsetBottom()); return insets; });
        ScrollView scroll=new ScrollView(this); scroll.addView(body); setContentView(scroll); render();
        if(fresh) run(null);
    }
    private void render() {
        if(dialog!=null) { dialog.dismiss(); dialog=null; }
        body.removeAllViews();
        LinearLayout toolbar=new LinearLayout(this); toolbar.setGravity(Gravity.CENTER_VERTICAL);
        Button back=UiStyles.button(this,"‹",false); back.setTextSize(26); back.setContentDescription(getString(R.string.categories_back)); back.setOnClickListener(v->finish()); toolbar.addView(back,new LinearLayout.LayoutParams(UiStyles.dp(this,48),UiStyles.dp(this,48)));
        TextView screenTitle=UiStyles.text(this,getString(R.string.categories_title),25,UiStyles.INK); LinearLayout.LayoutParams headingParams=new LinearLayout.LayoutParams(0,-2,1); headingParams.leftMargin=UiStyles.dp(this,14); toolbar.addView(screenTitle,headingParams); body.addView(toolbar);
        body.addView(UiStyles.text(this,getString(R.string.categories_description),15,UiStyles.MUTED));
        Button add=UiStyles.button(this,getString(R.string.category_add),true); add.setId(R.id.category_add); add.setEnabled(!job.busy); add.setOnClickListener(v->{ job.editing=true; job.editId=null; job.draft=""; job.message=""; edit(); }); body.addView(add,spacing());
        if(job.busy || !job.message.isEmpty()) { TextView status=UiStyles.text(this,job.busy?getString(R.string.categories_loading):job.message,14,UiStyles.MUTED); status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE); body.addView(status); }
        for(var category:job.categories) {
            LinearLayout panel=column(); panel.setPadding(UiStyles.dp(this,16),UiStyles.dp(this,14),UiStyles.dp(this,16),UiStyles.dp(this,14)); panel.setBackground(UiStyles.surface(this,UiStyles.SURFACE,14,UiStyles.LINE));
            LinearLayout heading=new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
            TextView initial=UiStyles.text(this,category.name().isEmpty()?"K":category.name().substring(0,category.name().offsetByCodePoints(0,1)).toUpperCase(Locale.ROOT),20,UiStyles.ACCENT_TEXT); initial.setGravity(Gravity.CENTER); initial.setBackground(UiStyles.surface(this,UiStyles.SOFT,14,0)); heading.addView(initial,new LinearLayout.LayoutParams(UiStyles.dp(this,48),UiStyles.dp(this,48)));
            LinearLayout title=column(); title.setPadding(UiStyles.dp(this,14),0,0,0); title.addView(UiStyles.text(this,category.name(),18,UiStyles.INK));
            title.addView(UiStyles.text(this,counts(category),13,UiStyles.MUTED)); heading.addView(title,new LinearLayout.LayoutParams(0,-2,1)); panel.addView(heading);
            LinearLayout actions=new LinearLayout(this);
            Button rename=UiStyles.button(this,getString(R.string.category_rename),false); rename.setContentDescription(getString(R.string.category_rename)+" "+category.name()); rename.setEnabled(!job.busy); rename.setOnClickListener(v->{ job.editing=true; job.editId=category.id(); job.draft=category.name(); job.message=""; edit(); });
            Button remove=UiStyles.button(this,getString(R.string.category_remove),false); remove.setContentDescription(getString(R.string.category_remove)+" "+category.name()); remove.setEnabled(!job.busy); remove.setOnClickListener(v->{ job.removeId=category.id(); job.destinationId=null; job.message=""; confirmRemove(); });
            LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1); left.rightMargin=UiStyles.dp(this,10); actions.addView(rename,left); actions.addView(remove,new LinearLayout.LayoutParams(0,-2,1)); panel.addView(actions,spacing()); body.addView(panel,spacing());
        }
        if(!job.busy) { if(job.editing) edit(); else if(job.removeId!=null) confirmRemove(); }
    }
    private String counts(SQLiteSavedItemRepository.CategorySummary category) { return getResources().getQuantityString(R.plurals.category_saved_count,category.saved(),category.saved())+(category.trashed()==0?"":getString(R.string.category_trash_count,category.trashed())); }
    private LinearLayout column() { LinearLayout view=new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout.LayoutParams spacing() { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=UiStyles.dp(this,12); return p; }
    private LinearLayout form() { LinearLayout form=column(); int pad=UiStyles.dp(this,18); form.setPadding(pad,pad,pad,pad); return form; }
    private void edit() {
        LinearLayout form=form(); form.addView(UiStyles.text(this,getString(R.string.category_name),14,UiStyles.MUTED));
        EditText name=new EditText(this); name.setId(R.id.category_name); name.setSingleLine(true); name.setText(job.draft); name.setSelectAllOnFocus(true); name.setContentDescription(getString(R.string.category_name)); form.addView(name);
        form.addView(UiStyles.text(this,getString(R.string.category_name_help),13,UiStyles.MUTED));
        name.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int start,int count,int after) {} public void onTextChanged(CharSequence s,int start,int before,int count) { job.draft=s.toString(); } public void afterTextChanged(Editable value) {} });
        dialog=new AlertDialog.Builder(this).setTitle(job.editId==null?R.string.category_add:R.string.category_rename).setView(form).setNegativeButton(android.R.string.cancel,(d,w)->job.editing=false).setPositiveButton(R.string.save_changes,null).create();
        dialog.setOnCancelListener(d->job.editing=false); dialog.show(); UiStyles.styleEditor(dialog);
        if(!job.message.isEmpty()) name.setError(job.message);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{ String id=job.editId,value=name.getText().toString(); run(repository->repository.saveCategory(id,value)); });
    }
    private void confirmRemove() {
        var source=job.categories.stream().filter(c->c.id().equals(job.removeId)).findFirst().orElse(null);
        if(source==null) { job.removeId=null; return; }
        List<SQLiteSavedItemRepository.CategorySummary> targets=new ArrayList<>(); for(var category:job.categories) if(!category.id().equals(source.id())) targets.add(category);
        if(targets.isEmpty()) { job.removeId=null; Toast.makeText(this,R.string.category_keep_one,Toast.LENGTH_LONG).show(); return; }
        LinearLayout form=form(); boolean hasPosts=source.saved()+source.trashed()>0;
        form.addView(UiStyles.text(this,getString(hasPosts?R.string.category_remove_move:R.string.category_remove_empty,source.name()),15,UiStyles.INK));
        if(hasPosts) {
            form.addView(UiStyles.text(this,getString(R.string.category_move_to),14,UiStyles.MUTED)); Spinner target=new Spinner(this); target.setId(R.id.category_destination); target.setContentDescription(getString(R.string.category_move_to));
            List<String> names=new ArrayList<>(); int selected=0; for(int i=0;i<targets.size();i++) { names.add(targets.get(i).name()); if(targets.get(i).id().equals(job.destinationId)) selected=i; }
            target.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names)); target.setSelection(selected); job.destinationId=targets.get(selected).id(); form.addView(target);
            target.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){ public void onItemSelected(android.widget.AdapterView<?> p,android.view.View v,int position,long id) { job.destinationId=targets.get(position).id(); } public void onNothingSelected(android.widget.AdapterView<?> p) {} });
        }
        if(!job.message.isEmpty()) form.addView(UiStyles.text(this,job.message,14,UiStyles.ACCENT_TEXT));
        dialog=new AlertDialog.Builder(this).setTitle(R.string.category_remove).setView(form).setNegativeButton(android.R.string.cancel,(d,w)->job.removeId=null).setPositiveButton(R.string.category_remove,null).create();
        dialog.setOnCancelListener(d->job.removeId=null); dialog.show(); UiStyles.styleEditor(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{ String destination=hasPosts?job.destinationId:null; run(repository->repository.removeCategory(source.id(),destination)); });
    }
    private interface Operation { void apply(SQLiteSavedItemRepository repository) throws Exception; }
    private void run(Operation operation) {
        Job running=job; Context app=getApplicationContext(); running.busy=true;
        if(dialog!=null) { dialog.setCancelable(false); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false); }
        ExecutorService worker=Executors.newSingleThreadExecutor(); worker.execute(()->{
            try(var repository=new SQLiteSavedItemRepository(app)) {
                repository.initialize(); if(operation!=null) { operation.apply(repository); running.editing=false; running.removeId=null; running.message=app.getString(R.string.category_saved); }
                running.categories=repository.categories();
            } catch(Exception error) { running.message=error instanceof IllegalArgumentException || error instanceof IllegalStateException?error.getMessage():app.getString(R.string.category_failed); }
            finally { running.busy=false; running.refresh(); worker.shutdown(); }
        });
    }
    @Override public Object onRetainNonConfigurationInstance() { return job; }
    @Override protected void onSaveInstanceState(Bundle state) { state.putBoolean("editing",job.editing); state.putString("edit_id",job.editId); state.putString("draft",job.draft); state.putString("remove_id",job.removeId); state.putString("destination_id",job.destinationId); super.onSaveInstanceState(state); }
    @Override protected void onDestroy() { if(job.screen==this) job.screen=null; if(dialog!=null) dialog.dismiss(); super.onDestroy(); }
}
