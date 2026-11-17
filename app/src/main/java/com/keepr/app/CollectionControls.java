package com.keepr.app;

import android.app.*;
import android.os.Bundle;
import android.widget.*;
import com.keepr.app.data.SQLiteSavedItemRepository;
import com.keepr.app.data.FeedQuery;
import com.keepr.app.domain.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

/** Collection filters and explicit selection, scoped to all matching results across pages. */
final class CollectionControls {
    private final Activity activity;
    private final SQLiteSavedItemRepository repository;
    private final ExecutorService worker;
    private final Runnable render,reload;
    private final BiConsumer<String,String> folderChanged;
    private String tag="",source="",from="",through="";
    private CollectionQuery.Sort sort=CollectionQuery.Sort.NEWEST;
    private boolean selecting,busy,queryBusy=true;
    private FeedQuery activeQuery;
    private int selectionGeneration;
    private final Set<String> selected=new LinkedHashSet<>();
    private List<String> availableTags=List.of(),availableSources=List.of();
    private LinearLayout selectionBar,toolsPanel;
    private Button selectionButton,actions,selectAll;
    private Spinner statusChoice;
    private TextView summary;
    private AlertDialog dialog;

    CollectionControls(Activity activity,SQLiteSavedItemRepository repository,ExecutorService worker,Runnable render,Runnable reload,BiConsumer<String,String> folderChanged,Bundle state) {
        this.activity=activity; this.repository=repository; this.worker=worker; this.render=render; this.reload=reload; this.folderChanged=folderChanged;
        if(state!=null) {
            tag=state.getString("collection_tag",""); source=state.getString("collection_source",""); from=state.getString("collection_from",""); through=state.getString("collection_through","");
            try { sort=CollectionQuery.Sort.valueOf(state.getString("collection_sort","NEWEST")); } catch(IllegalArgumentException ignored) { }
            selecting=state.getBoolean("collection_selecting"); ArrayList<String> ids=state.getStringArrayList("collection_selected"); if(ids!=null) selected.addAll(ids);
        }
    }
    void install(LinearLayout parent, Spinner statuses) {
        statusChoice=statuses;
        toolsPanel=form();
        toolsPanel.addView(statuses,new LinearLayout.LayoutParams(-1,-2));
        Button advanced=button("Tags, sources, dates & sort",()->{ if(dialog!=null) dialog.dismiss(); showFilters(); });
        advanced.setId(R.id.collection_filters); toolsPanel.addView(advanced);
        summary=UiStyles.text(activity,"",12,UiStyles.MUTED); toolsPanel.addView(summary);
        selectionButton=button("Done",this::toggleSelection); selectionButton.setId(R.id.collection_select);
        selectionBar=new LinearLayout(activity);
        selectionBar.addView(selectionButton);
        selectAll=button("Select all results",this::selectAllResults);
        selectionBar.addView(selectAll,new LinearLayout.LayoutParams(0,-2,1));
        actions=button("Actions",this::showActions); actions.setId(R.id.collection_actions); selectionBar.addView(actions,new LinearLayout.LayoutParams(0,-2,1)); parent.addView(selectionBar);
        update();
    }
    void showTools() {
        if(toolsPanel.getParent()!=null) ((android.view.ViewGroup)toolsPanel.getParent()).removeView(toolsPanel);
        dialog=new AlertDialog.Builder(activity).setTitle("Filters and sorting").setView(toolsPanel).setPositiveButton("Done",null).show();
    }
    void toggleSelection() { if(busy || queryBusy) return; selectionGeneration++; selecting=!selecting; selected.clear(); render.run(); }
    boolean hasFilters() { return !tag.isEmpty() || !source.isEmpty() || !from.isEmpty() || !through.isEmpty(); }
    FeedQuery query(String folder,String text,SavedItem.Status status) { return new FeedQuery(folder,text,status,tag,source,bound(from,false),bound(through,true),sort); }
    boolean hasSelection() { return !selected.isEmpty(); }
    void loading(FeedQuery query) { activeQuery=query; queryBusy=true; selectionGeneration++; update(); }
    void loaded(List<String> matching,List<String> tags,List<String> sources) {
        if(matching!=null) selected.retainAll(new HashSet<>(matching));
        availableTags=tags; availableSources=sources; queryBusy=false; update();
    }
    private void selectAllResults() {
        if(busy || queryBusy || activeQuery==null) return;
        int generation=++selectionGeneration; FeedQuery filter=activeQuery; queryBusy=true; update();
        worker.execute(()->{
            try {
                List<String> ids=repository.matchingIds(filter);
                activity.runOnUiThread(()->{ if(activity.isDestroyed() || generation!=selectionGeneration) return; selected.addAll(ids); queryBusy=false; update(); render.run(); });
            } catch(Exception error) { activity.runOnUiThread(()->{ if(activity.isDestroyed() || generation!=selectionGeneration) return; queryBusy=false; update(); toast("Could not select results. Please try again."); }); }
        });
    }
    void addSelection(LinearLayout card,SavedItem item) {
        if(!selecting) return;
        CheckBox checkbox=new CheckBox(activity); checkbox.setText("Select "+item.title()); checkbox.setChecked(selected.contains(item.id())); checkbox.setEnabled(!busy && !queryBusy);
        checkbox.setOnCheckedChangeListener((button,checked)->{ if(busy || queryBusy) { checkbox.setChecked(selected.contains(item.id())); return; } if(checked) selected.add(item.id()); else selected.remove(item.id()); update(); }); card.addView(checkbox);
    }
    private void update() {
        if(summary==null) return;
        String[] sorts={"Newest first","Oldest first","Price: low to high (by currency)","Price: high to low (by currency)"};
        summary.setText(sorts[sort.ordinal()]+(tag.isEmpty()?"":" · #"+tag)+(source.isEmpty()?"":" · "+source)+(from.isEmpty()?"":" · from "+from)+(through.isEmpty()?"":" · through "+through));
        selectionButton.setText("Done"); selectionButton.setEnabled(!busy && !queryBusy); selectAll.setEnabled(!busy && !queryBusy);
        selectionBar.setVisibility(selecting?android.view.View.VISIBLE:android.view.View.GONE);
        actions.setText(selected.size()+" selected · Actions"); actions.setEnabled(!busy && !queryBusy && !selected.isEmpty());
    }
    private long bound(String date,boolean end) { if(date.isEmpty()) return end?Long.MAX_VALUE:Long.MIN_VALUE; LocalDate day=LocalDate.parse(date); return (end?day.plusDays(1):day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
    private void showFilters() {
        LinearLayout form=form();
        List<String> tags=new ArrayList<>(availableTags),sources=new ArrayList<>(availableSources);
        tags.sort(String.CASE_INSENSITIVE_ORDER); sources.sort(String.CASE_INSENSITIVE_ORDER);
        Spinner tagChoice=choices(form,"Tag",tags,tag,"Any tag"),sourceChoice=choices(form,"Source",sources,source,"Any source");
        EditText start=field(form,"Saved from (YYYY-MM-DD)",from),end=field(form,"Saved through (YYYY-MM-DD)",through);
        Spinner order=choices(form,"Sort",Arrays.asList("Newest first","Oldest first","Price: low to high","Price: high to low"),"",null); order.setSelection(sort.ordinal());
        TextView note=new TextView(activity); note.setText("Dates use this device’s time zone. Prices are grouped by currency; missing or unrecognized prices appear last."); form.addView(note);
        AlertDialog current=dialog(form,"Filters and sorting","Apply",()->{});
        current.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String first=start.getText().toString().trim(),last=end.getText().toString().trim();
            try {
                if(bound(first,false)>=bound(last,true)) throw new IllegalArgumentException();
                tag=tagChoice.getSelectedItemPosition()==0?"":tagChoice.getSelectedItem().toString(); source=sourceChoice.getSelectedItemPosition()==0?"":sourceChoice.getSelectedItem().toString();
                from=first; through=last; sort=CollectionQuery.Sort.values()[order.getSelectedItemPosition()]; current.dismiss(); render.run();
            } catch(RuntimeException error) { end.setError("Enter valid YYYY-MM-DD dates with the end on or after the start."); }
        });
        Button reset=button("Reset these filters",()->{ tag=""; source=""; from=""; through=""; sort=CollectionQuery.Sort.NEWEST; current.dismiss(); render.run(); }); form.addView(reset);
    }
    private static void addUnique(List<String> list,String value) { if(list.stream().noneMatch(old->old.equalsIgnoreCase(value))) list.add(value); }
    private void showActions() {
        if(busy || queryBusy || selected.isEmpty()) return;
        dialog=new AlertDialog.Builder(activity).setTitle(selected.size()+" selected posts")
            .setItems(new String[]{"Move to category","Add tags","Archive","Move to trash",activity.getString(R.string.share_posts)},(d,index)->{
                List<String> ids=new ArrayList<>(selected);
                if(index==4) { SharePostsActivity.open(activity,ids); return; }
                SQLiteSavedItemRepository.BulkAction action=SQLiteSavedItemRepository.BulkAction.values()[index];
                if(action==SQLiteSavedItemRepository.BulkAction.MOVE) {
                    worker.execute(()->{ List<String> folders=repository.folders(); activity.runOnUiThread(()->{ if(activity.isDestroyed()) return;
                        LinearLayout form=form(); AutoCompleteTextView target=new AutoCompleteTextView(activity); target.setHint("Choose or type a category"); target.setSingleLine(true); target.setThreshold(0);
                        target.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_dropdown_item_1line,folders)); target.setOnClickListener(v->target.showDropDown()); form.addView(target);
                        dialog(form,"Move "+ids.size()+" posts","Move",()->runBulk(ids,action,target.getText().toString()));
                    }); });
                } else if(action==SQLiteSavedItemRepository.BulkAction.ADD_TAGS) {
                    LinearLayout form=form(); EditText tags=field(form,"Tags to add (comma separated)",""); dialog(form,"Add tags","Add tags",()->runBulk(ids,action,tags.getText().toString()));
                } else {
                    dialog=new AlertDialog.Builder(activity).setTitle((index==2?"Archive ":"Move to trash ")+ids.size()+" posts?")
                        .setMessage(index==2?"The posts and photos will stay saved. Find them with the Archived status filter.":"These posts and photos can be restored from Trash until their retention period ends.")
                        .setNegativeButton(android.R.string.cancel,null).setPositiveButton(index==2?"Archive":"Move to trash",(confirm,which)->runBulk(ids,action,"")).show();
                }
            }).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void runBulk(List<String> ids,SQLiteSavedItemRepository.BulkAction action,String value) {
        run(()->{
            int changed;
            if(action==SQLiteSavedItemRepository.BulkAction.DELETE) { repository.trash(ids); changed=ids.size(); } else changed=repository.bulk(ids,action,value);
            if(action==SQLiteSavedItemRepository.BulkAction.DELETE) for(String id:ids) androidx.work.WorkManager.getInstance(activity.getApplicationContext()).cancelUniqueWork("snapshot-"+id);
            return changed+" posts updated";
        },()->{ selected.clear(); selecting=false; });
    }
    void showFolders() {
        if(!busy) activity.startActivity(new android.content.Intent(activity,CategoriesActivity.class));
    }
    private interface Operation { String execute() throws Exception; }
    private void run(Operation operation,Runnable after) {
        if(busy) return; busy=true; update(); AlertDialog current=dialog;
        if(current!=null) { current.setCancelable(false); if(current.getButton(AlertDialog.BUTTON_POSITIVE)!=null) current.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false); }
        worker.execute(()->{
            try { String message=operation.execute(); activity.runOnUiThread(()->{ if(activity.isDestroyed()) return; busy=false; if(current!=null) current.dismiss(); after.run(); reload.run(); toast(message); update(); }); }
            catch(Exception error) { activity.runOnUiThread(()->{ if(activity.isDestroyed()) return; busy=false; if(current!=null) { current.setCancelable(true); if(current.getButton(AlertDialog.BUTTON_POSITIVE)!=null) current.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); } update(); toast(error instanceof IllegalArgumentException || error instanceof IllegalStateException?error.getMessage():"Could not update the collection. Please try again."); }); }
        });
    }
    private LinearLayout form() { LinearLayout form=new LinearLayout(activity); form.setOrientation(LinearLayout.VERTICAL); int pad=(int)(20*activity.getResources().getDisplayMetrics().density); form.setPadding(pad,pad,pad,pad); return form; }
    private Button button(String label,Runnable action) { Button button=UiStyles.button(activity,label,false); button.setOnClickListener(v->action.run()); return button; }
    private EditText field(LinearLayout form,String label,String value) { TextView caption=new TextView(activity); caption.setText(label); form.addView(caption); EditText input=new EditText(activity); input.setSingleLine(true); input.setText(value); input.setContentDescription(label); form.addView(input); return input; }
    private Spinner choices(LinearLayout form,String label,List<String> options,String chosen,String any) {
        TextView caption=new TextView(activity); caption.setText(label); form.addView(caption); List<String> values=new ArrayList<>(options); if(!chosen.isEmpty()) addUnique(values,chosen); if(any!=null) values.add(0,any);
        Spinner spinner=new Spinner(activity); spinner.setContentDescription(label); spinner.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,values)); spinner.setSelection(Math.max(0,values.indexOf(chosen))); form.addView(spinner); return spinner;
    }
    private AlertDialog dialog(LinearLayout form,String title,String positive,Runnable action) {
        ScrollView scroll=new ScrollView(activity); scroll.addView(form);
        AlertDialog current=new AlertDialog.Builder(activity).setTitle(title).setView(scroll).setNegativeButton(android.R.string.cancel,null).setPositiveButton(positive,null).create();
        current.show(); current.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->action.run()); dialog=current; return current;
    }
    private void toast(String text) { Toast.makeText(activity,text,Toast.LENGTH_LONG).show(); }
    void save(Bundle state) { state.putString("collection_tag",tag); state.putString("collection_source",source); state.putString("collection_from",from); state.putString("collection_through",through); state.putString("collection_sort",sort.name()); state.putBoolean("collection_selecting",selecting); state.putStringArrayList("collection_selected",new ArrayList<>(selected)); }
    void reset() { tag=""; source=""; from=""; through=""; sort=CollectionQuery.Sort.NEWEST; }
    void close() { if(dialog!=null) dialog.dismiss(); }
}
