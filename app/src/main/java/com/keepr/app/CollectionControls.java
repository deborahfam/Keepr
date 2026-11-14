package com.keepr.app;

import android.app.*;
import android.os.Bundle;
import android.widget.*;
import com.keepr.app.data.SQLiteSavedItemRepository;
import com.keepr.app.domain.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

/** Collection filters and explicit selection, scoped to the currently visible results. */
final class CollectionControls {
    private final Activity activity;
    private final SQLiteSavedItemRepository repository;
    private final ExecutorService worker;
    private final Runnable render,reload;
    private final BiConsumer<String,String> folderChanged;
    private String tag="",source="",from="",through="";
    private CollectionQuery.Sort sort=CollectionQuery.Sort.NEWEST;
    private boolean selecting,busy;
    private final Set<String> selected=new LinkedHashSet<>();
    private List<SavedItem> all=List.of(),visible=List.of();
    private LinearLayout selectionBar;
    private Button selectionButton,actions;
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
    void install(LinearLayout parent) {
        Button filters=button("Filters and sorting",this::showFilters); filters.setId(R.id.collection_filters); parent.addView(filters);
        summary=new TextView(activity); parent.addView(summary);
        LinearLayout tools=new LinearLayout(activity);
        selectionButton=button("Select posts",()->{ selecting=!selecting; selected.clear(); render.run(); }); selectionButton.setId(R.id.collection_select);
        tools.addView(selectionButton,new LinearLayout.LayoutParams(0,-2,1)); tools.addView(button("Manage folders",this::showFolders),new LinearLayout.LayoutParams(0,-2,1)); parent.addView(tools);
        selectionBar=new LinearLayout(activity);
        selectionBar.addView(button("Select all results",()->{ for(SavedItem item:visible) selected.add(item.id()); render.run(); }),new LinearLayout.LayoutParams(0,-2,1));
        actions=button("Actions",this::showActions); actions.setId(R.id.collection_actions); selectionBar.addView(actions,new LinearLayout.LayoutParams(0,-2,1)); parent.addView(selectionBar);
        update();
    }
    List<SavedItem> filter(List<SavedItem> items,String folder,String query,SavedItem.Status status,boolean loaded) {
        all=new ArrayList<>(items); visible=CollectionQuery.apply(items,folder,query,status,tag,source,bound(from,false),bound(through,true),sort);
        if(loaded) { Set<String> ids=new HashSet<>(); for(SavedItem item:visible) ids.add(item.id()); selected.retainAll(ids); }
        update(); return visible;
    }
    void addSelection(LinearLayout card,SavedItem item) {
        if(!selecting) return;
        CheckBox checkbox=new CheckBox(activity); checkbox.setText("Select "+item.title()); checkbox.setChecked(selected.contains(item.id())); checkbox.setEnabled(!busy);
        checkbox.setOnCheckedChangeListener((button,checked)->{ if(checked) selected.add(item.id()); else selected.remove(item.id()); update(); }); card.addView(checkbox);
    }
    private void update() {
        if(summary==null) return;
        String[] sorts={"Newest first","Oldest first","Price: low to high (by currency)","Price: high to low (by currency)"};
        summary.setText(sorts[sort.ordinal()]+(tag.isEmpty()?"":" · #"+tag)+(source.isEmpty()?"":" · "+source)+(from.isEmpty()?"":" · from "+from)+(through.isEmpty()?"":" · through "+through));
        selectionButton.setText(selecting?"Done selecting":"Select posts"); selectionButton.setEnabled(!busy);
        selectionBar.setVisibility(selecting?android.view.View.VISIBLE:android.view.View.GONE);
        actions.setText(selected.size()+" selected · Actions"); actions.setEnabled(!busy && !selected.isEmpty());
    }
    private long bound(String date,boolean end) { if(date.isEmpty()) return end?Long.MAX_VALUE:Long.MIN_VALUE; LocalDate day=LocalDate.parse(date); return (end?day.plusDays(1):day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
    private void showFilters() {
        LinearLayout form=form();
        List<String> tags=new ArrayList<>(),sources=new ArrayList<>();
        for(SavedItem item:all) { for(String value:item.tags()) addUnique(tags,value); addUnique(sources,CollectionQuery.source(item)); }
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
        if(busy || selected.isEmpty()) return;
        dialog=new AlertDialog.Builder(activity).setTitle(selected.size()+" selected posts")
            .setItems(new String[]{"Move to folder","Add tags","Archive","Move to trash"},(d,index)->{
                List<String> ids=new ArrayList<>(selected);
                SQLiteSavedItemRepository.BulkAction action=SQLiteSavedItemRepository.BulkAction.values()[index];
                if(action==SQLiteSavedItemRepository.BulkAction.MOVE) {
                    worker.execute(()->{ List<String> folders=repository.folders(); activity.runOnUiThread(()->{ if(activity.isDestroyed()) return;
                        LinearLayout form=form(); AutoCompleteTextView target=new AutoCompleteTextView(activity); target.setHint("Choose or type a folder"); target.setSingleLine(true); target.setThreshold(0);
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
    private void showFolders() {
        if(busy) return;
        worker.execute(()->{ List<String> folders=repository.folders(); activity.runOnUiThread(()->{ if(activity.isDestroyed()) return;
            dialog=new AlertDialog.Builder(activity).setTitle("Manage folders").setItems(folders.toArray(new String[0]),(d,index)->{
                String sourceFolder=folders.get(index);
                dialog=new AlertDialog.Builder(activity).setTitle(sourceFolder).setItems(new String[]{"Rename folder","Merge into another folder"},(choice,operation)->{
                    LinearLayout form=form(); boolean merge=operation==1;
                    if(merge) {
                        List<String> destinations=new ArrayList<>(folders); destinations.remove(sourceFolder);
                        if(destinations.isEmpty()) { toast("Create another folder before merging."); return; }
                        Spinner target=choices(form,"Destination folder",destinations,"",null);
                        TextView note=new TextView(activity); note.setText("All posts will move into the destination. The source folder will be removed."); form.addView(note);
                        dialog(form,"Merge "+sourceFolder,"Merge folders",()->folderOperation(sourceFolder,target.getSelectedItem().toString(),true));
                    } else { EditText name=field(form,"Folder name",sourceFolder); dialog(form,"Rename folder","Rename",()->folderOperation(sourceFolder,name.getText().toString().trim(),false)); }
                }).setNegativeButton(android.R.string.cancel,null).show();
            }).setNegativeButton(android.R.string.cancel,null).show();
        }); });
    }
    private void folderOperation(String sourceFolder,String destination,boolean merge) {
        run(()->{ repository.organizeFolder(sourceFolder,destination,merge); return merge?"Folders merged":"Folder renamed"; },()->folderChanged.accept(sourceFolder,destination));
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
    private Button button(String label,Runnable action) { Button button=new Button(activity); button.setText(label); button.setAllCaps(false); button.setOnClickListener(v->action.run()); return button; }
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
    void close() { if(dialog!=null) dialog.dismiss(); }
}
