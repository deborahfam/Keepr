package com.keepr.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.keepr.app.domain.SavedItem;
import com.keepr.app.domain.SavedItemRepository;
import com.keepr.app.data.SQLiteSavedItemRepository;
import com.keepr.app.data.UrlExtractor;
import com.keepr.app.data.MetadataService;
import com.keepr.app.data.PreviewMetadata;
import com.keepr.app.data.MediaCache;
import com.keepr.app.data.SnapshotWorker;
import com.keepr.app.domain.CachedMedia;
import com.keepr.app.domain.TagNames;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String[] CATEGORIES = {"Real Estate", "Electronics", "Vehicles", "Jobs", "Marketplace", "Rentals", "Social", "Other"};
    private final List<SavedItem> links = new ArrayList<>();
    private SavedItemRepository store;
    private final ExecutorService databaseWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService previewWorker = Executors.newSingleThreadExecutor();
    private LinearLayout cards;
    private TextView count, collectionSummary;
    private AlertDialog editor;
    private EditText editorUrl, editorTitle, editorNotes, editorText, editorTags;
    private EditText search;
    private Spinner statuses;
    private boolean listView;
    private Button cardViewButton, listViewButton;
    private String editorSharedText = "";
    private ArrayList<Uri> editorAttachments = new ArrayList<>();
    private Spinner editorCategory;
    private String category = "All", query = "";
    private SavedItem.Status statusFilter;
    private AlertDialog duplicateDialog;
    private CollectionControls collectionControls;
    private boolean storageHealthy = false;
    private boolean resumedOnce;
    private final int ink = UiStyles.INK;
    private final int muted = UiStyles.MUTED;
    private final int accent = UiStyles.ACCENT;
    private final int canvas = UiStyles.PAPER;
    private final int line = UiStyles.LINE;
    private final List<String> folders = new ArrayList<>(java.util.Arrays.asList(CATEGORIES));
    private LinearLayout folderChips;
    private final ExecutorService imageWorker = Executors.newFixedThreadPool(2);
    private androidx.lifecycle.Observer<List<androidx.work.WorkInfo>> workObserver;
    private androidx.lifecycle.LiveData<List<androidx.work.WorkInfo>> workUpdates;
    private final List<Button> categoryButtons = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        listView = getSharedPreferences("appearance", MODE_PRIVATE).getBoolean("list_view", false);
        if(state!=null) {
            category=state.getString("filter_folder","All"); query=state.getString("filter_query","");
            String status=state.getString("filter_status"); if(status!=null) try { statusFilter=SavedItem.Status.valueOf(status); } catch(IllegalArgumentException ignored) { }
        }
        store = new SQLiteSavedItemRepository(this);
        collectionControls=new CollectionControls(this,(SQLiteSavedItemRepository)store,databaseWorker,this::renderCards,this::reloadCollection,
                (oldName,newName)->{ if(category.equals(oldName)) category=newName; },state);
        com.keepr.app.data.TrashWorker.schedule(this);
        buildScreen();
        databaseWorker.execute(() -> {
            try {
                store.observe(snapshot -> runOnUiThread(() -> {
                    if (!isDestroyed()) { links.clear(); links.addAll(snapshot); renderCards(); }
                }));
                ((SQLiteSavedItemRepository)store).purgeExpired();
                List<SavedItem> loaded = store.load();
                List<String> savedFolders = store.folders();
                for (SavedItem item : loaded) if (!item.url().isEmpty() && (item.previewState() == SavedItem.PreviewState.PENDING || store.needsPhotoDiscovery(item.id()))) SnapshotWorker.enqueue(this,item.id());
                runOnUiThread(() -> { if (!isDestroyed()) {
                    storageHealthy = true; links.clear(); links.addAll(loaded);
                    String selectedFolder=editorCategory==null || editorCategory.getSelectedItem()==null ? null : editorCategory.getSelectedItem().toString();
                    folders.clear(); folders.addAll(savedFolders);
                    if (selectedFolder!=null) {
                        if (!folders.contains(selectedFolder)) folders.add(selectedFolder);
                        ((ArrayAdapter<?>)editorCategory.getAdapter()).notifyDataSetChanged();
                        editorCategory.setSelection(folders.indexOf(selectedFolder));
                    }
                    rebuildFolderChips(); renderCards();
                } });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    storageHealthy = false;
                    if (!isDestroyed()) new AlertDialog.Builder(this).setTitle("Saved links could not be read")
                        .setMessage("Your stored data has been preserved. Saving is disabled.")
                        .setPositiveButton("OK", null).show();
                });
            }
        });
        if (state != null && state.getBoolean("editor_open")) {
            ArrayList<String> savedUris = state.getStringArrayList("draft_attachments");
            ArrayList<Uri> attachments = new ArrayList<>();
            if (savedUris != null) for (String value : savedUris) attachments.add(Uri.parse(value));
            showEditor(state.getString("draft_url", ""),state.getString("shared_text", ""),attachments,false);
            editorTitle.setText(state.getString("draft_title", ""));
            editorNotes.setText(state.getString("draft_notes", ""));
            editorText.setText(state.getString("draft_text", "")); editorTags.setText(state.getString("draft_tags", ""));
            String folder = state.getString("draft_folder", "Other");
            if (!folders.contains(folder)) folders.add(folder);
            ((ArrayAdapter<?>)editorCategory.getAdapter()).notifyDataSetChanged();
            editorCategory.setSelection(folders.indexOf(folder));
        } else if (state == null) receiveShare(getIntent());
        workObserver = infos -> {
            if (isDestroyed() || !storageHealthy) return;
            databaseWorker.execute(() -> {
                try {
                    List<SavedItem> refreshed = store.load();
                    runOnUiThread(() -> { if (!isDestroyed()) { links.clear(); links.addAll(refreshed); renderCards(); } });
                } catch (Exception ignored) { }
            });
        };
        workUpdates=androidx.work.WorkManager.getInstance(this).getWorkInfosByTagLiveData("keepr-snapshot");
        workUpdates.observeForever(workObserver);
    }

    @Override protected void onResume() {
        super.onResume();
        if(!resumedOnce) { resumedOnce=true; return; }
        if(store==null) return;
        reloadCollection();
    }
    private void reloadCollection() {
        databaseWorker.execute(()->{
            try {
                List<SavedItem> refreshed=store.load(); List<String> savedFolders=store.folders();
                runOnUiThread(()->{ if(!isDestroyed()) { links.clear(); links.addAll(refreshed); folders.clear(); folders.addAll(savedFolders); rebuildFolderChips(); renderCards(); } });
            } catch(Exception ignored) { }
        });
    }

    private void buildScreen() {
        LinearLayout root = column();
        root.setBackgroundColor(canvas);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });

        LinearLayout brand = new LinearLayout(this); brand.setGravity(android.view.Gravity.CENTER_VERTICAL);
        brand.setPadding(dp(24), dp(12), dp(24), dp(12));
        View mark = new View(this); mark.setBackground(surface(accent, 4, 0));
        brand.addView(mark, new LinearLayout.LayoutParams(dp(10), dp(32)));
        LinearLayout identity = column();
        TextView name = label("Keepr", 30, ink); name.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        name.setPadding(0,0,0,0); identity.addView(name);
        collectionSummary = label("Opening your collection…", 12, muted); identity.addView(collectionSummary);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1); nameParams.leftMargin = dp(12);
        brand.addView(identity, nameParams);
        Button menuButton = button("⋯", false); menuButton.setTextSize(24);
        menuButton.setContentDescription("Collection options");
        menuButton.setBackground(interactive(UiStyles.SURFACE, 16, line));
        brand.addView(menuButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        menuButton.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, menuButton);
            menu.getMenu().add("Settings").setOnMenuItemClickListener(item -> { startActivity(new Intent(this, SettingsActivity.class)); return true; });
            menu.getMenu().add("Filters and sorting").setOnMenuItemClickListener(item -> { collectionControls.showTools(); return true; });
            menu.getMenu().add("Select posts").setOnMenuItemClickListener(item -> { collectionControls.toggleSelection(); return true; });
            menu.getMenu().add("Clear search and filters").setOnMenuItemClickListener(item -> {
                category="All"; updateFilters(); query=""; statusFilter=null; collectionControls.reset(); search.setText(""); statuses.setSelection(0); renderCards(); return true;
            });
            menu.getMenu().add("Manage folders").setOnMenuItemClickListener(item -> { collectionControls.showFolders(); return true; });
            menu.getMenu().add(R.string.backup_restore).setOnMenuItemClickListener(item -> { startActivity(new Intent(this, BackupActivity.class)); return true; });
            menu.getMenu().add("Trash").setOnMenuItemClickListener(item -> { startActivity(new Intent(this, TrashActivity.class)); return true; });
            menu.show();
        });
        root.addView(brand);

        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        LinearLayout content = column(); content.setPadding(dp(20), 0, dp(20), dp(24));
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Button add = button("＋  Save new post", true);
        add.setOnClickListener(v -> showEditor(""));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(-1, dp(54)); addParams.bottomMargin=dp(16);
        content.addView(add, addParams);
        LinearLayout searchRow = new LinearLayout(this); searchRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        searchRow.setBackground(surface(UiStyles.SURFACE, 16, line));
        search = new EditText(this); search.setSingleLine(true);
        search.setHint(R.string.search_finds); search.setTextSize(15); search.setTextColor(ink); search.setHintTextColor(muted);
        search.setPadding(dp(16), dp(12), dp(16), dp(12)); search.setBackground(surface(UiStyles.SURFACE, 16, line));
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        search.setCompoundDrawablePadding(dp(10));
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setContentDescription("Search titles, text, notes and tags");
        searchRow.addView(search, new LinearLayout.LayoutParams(0, -2, 1)); search.setMinHeight(dp(54));
        Button clearSearch = button("×", false); clearSearch.setTextSize(22);
        clearSearch.setContentDescription("Clear search"); clearSearch.setBackground(interactive(UiStyles.SURFACE, 16, 0));
        clearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
        clearSearch.setOnClickListener(v -> search.setText(""));
        searchRow.addView(clearSearch, new LinearLayout.LayoutParams(dp(48), dp(48))); content.addView(searchRow);
        search.setOnEditorActionListener((v, action, event) -> {
            if (action != android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) return false;
            ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(), 0);
            search.clearFocus(); return true;
        });
        search.setText(query);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { query = s.toString(); clearSearch.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE); renderCards(); }
            public void afterTextChanged(Editable e) {}
        });
        LinearLayout categoryHeading = new LinearLayout(this); categoryHeading.setGravity(android.view.Gravity.CENTER_VERTICAL);
        categoryHeading.setPadding(0,dp(12),0,0);
        categoryHeading.addView(label("Categories",14,muted),new LinearLayout.LayoutParams(0,-2,1));
        Button manage=button("Manage",false); manage.setBackgroundColor(Color.TRANSPARENT); manage.setOnClickListener(v->collectionControls.showFolders());
        categoryHeading.addView(manage); content.addView(categoryHeading);
        HorizontalScrollView filters = new HorizontalScrollView(this); filters.setHorizontalScrollBarEnabled(false);
        filters.setPadding(0, dp(8), 0, dp(6));
        folderChips = new LinearLayout(this);
        filters.addView(folderChips); content.addView(filters); rebuildFolderChips();
        statuses=new Spinner(this); statuses.setId(R.id.status_filter); statuses.setContentDescription(getString(R.string.status_filter));
        List<String> labels=new ArrayList<>(); labels.add(getString(R.string.all_statuses)); labels.addAll(java.util.Arrays.asList(StatusLabels.choices(this)));
        statuses.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
        statuses.setSelection(statusFilter==null?0:statusFilter.ordinal()+1);
        statuses.setMinimumHeight(dp(48));

        statuses.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id) { statusFilter=position==0?null:SavedItem.Status.values()[position-1]; renderCards(); }
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        collectionControls.install(content, statuses);
        LinearLayout collectionHeading = new LinearLayout(this);
        collectionHeading.setGravity(android.view.Gravity.CENTER_VERTICAL);
        collectionHeading.setPadding(0, dp(14), 0, dp(14));
        LinearLayout collectionTitle=column();
        TextView savedHeading=label("Your saves",20,ink); savedHeading.setTypeface(null,Typeface.BOLD); collectionTitle.addView(savedHeading);
        count = label("", 12, muted); collectionTitle.addView(count);
        collectionHeading.addView(collectionTitle, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout viewChoices = new LinearLayout(this);
        viewChoices.setPadding(dp(3), dp(3), dp(3), dp(3));
        viewChoices.setBackground(surface(UiStyles.SURFACE, 14, line));
        cardViewButton = button("Cards", false); cardViewButton.setId(R.id.view_cards);
        listViewButton = button("List", false); listViewButton.setId(R.id.view_list);
        cardViewButton.setContentDescription("Card view"); listViewButton.setContentDescription("List view");
        viewChoices.addView(cardViewButton); viewChoices.addView(listViewButton);
        cardViewButton.setOnClickListener(v -> changeView(false)); listViewButton.setOnClickListener(v -> changeView(true));
        collectionHeading.addView(viewChoices); updateViewButtons();
        content.addView(collectionHeading);
        cards = column(); cards.setId(R.id.collection_items); content.addView(cards);

        TextView offline=label("Downloaded photos work offline. Some captures may still be pending.",13,0xffA5D5BD);
        offline.setPadding(dp(14),dp(14),dp(14),dp(14)); offline.setBackground(surface(0xff10201A,16,0));
        content.addView(offline);
        setContentView(root); renderCards();
    }

    private void rebuildFolderChips() {
        if (folderChips == null) return;
        folderChips.removeAllViews(); categoryButtons.clear();
        List<String> options = new ArrayList<>(); options.add("All"); options.addAll(folders);
        for (String option : options) {
            Button chip = button(option.equals("All") ? getString(R.string.all_finds) : option, false);
            chip.setTag(option); chip.setTextSize(13); chip.setMinHeight(dp(48));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, dp(48)); cp.rightMargin = dp(8);
            folderChips.addView(chip, cp); categoryButtons.add(chip);
            chip.setOnClickListener(v -> { category = option; updateFilters(); renderCards(); });
        }
        updateFilters();
    }

    private void updateFilters() {
        for (Button chip : categoryButtons) {
            boolean selected = category.equals(chip.getTag());
            chip.setSelected(selected); chip.setTextColor(selected ? Color.WHITE : muted);
            chip.setBackground(interactive(selected ? accent : UiStyles.SURFACE, 24, selected ? 0 : line));
            chip.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void renderCards() {
        if (cards == null) return;
        cards.removeAllViews(); int shown = 0;
        for (SavedItem link : collectionControls.filter(links,category,query,statusFilter,storageHealthy)) {
            shown++;
            cards.addView(savedItemView(link));
        }
        count.setText(shown + (shown == 1 ? " result" : " results") + ((!query.isEmpty() || statusFilter!=null || collectionControls.hasFilters()) ? " · filtered" : ""));
        int attention=0;
        for(SavedItem saved:links) if(saved.previewState()==SavedItem.PreviewState.FAILED || saved.failedPhotos()>0 || saved.expectedPhotos()>saved.media().size()) attention++;
        collectionSummary.setText(links.size()+" saved"+(attention>0?" · "+attention+" need attention":" · Your personal collection"));
        count.setContentDescription(getResources().getQuantityString(R.plurals.saved_count, shown, shown));
        if (shown == 0) {
            LinearLayout empty = column(); empty.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            empty.setPadding(dp(24), dp(28), dp(24), dp(30));
            empty.setBackground(surface(UiStyles.SURFACE, 22, line));
            ImageView illustration = new ImageView(this); illustration.setImageResource(R.drawable.empty_collection);
            illustration.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            empty.addView(illustration, new LinearLayout.LayoutParams(dp(160), dp(110)));
            TextView title = label(!storageHealthy ? "Opening your collection…" : links.isEmpty() ? "Keep something good." : "No matching finds", 20, ink);
            title.setGravity(android.view.Gravity.CENTER); title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); title.setTextSize(24); empty.addView(title);
            TextView explanation = label(!storageHealthy ? "Your saved posts will appear here." : links.isEmpty() ? "Tap Save new post to keep a link. You can also share text and photos to Keepr from another app." : "Try another search or clear your filters to see your collection.", 14, muted);
            explanation.setGravity(android.view.Gravity.CENTER); explanation.setLineSpacing(dp(3), 1); empty.addView(explanation);
            if (storageHealthy && !links.isEmpty()) {
                Button reset = button("Clear search and filters", false);
                reset.setOnClickListener(v -> { category = "All"; statusFilter = null; query = "";
                    collectionControls.reset(); search.setText(""); statuses.setSelection(0); updateFilters(); renderCards(); });
                LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(-1, -2); resetParams.topMargin = dp(16);
                empty.addView(reset, resetParams);
            }
            cards.addView(empty);
        }
    }

    private void changeView(boolean compact) {
        if (listView == compact) return;
        listView = compact;
        getSharedPreferences("appearance", MODE_PRIVATE).edit().putBoolean("list_view", compact).apply();
        updateViewButtons(); renderCards();
    }

    private void updateViewButtons() {
        for (Button choice : new Button[]{cardViewButton, listViewButton}) {
            boolean selected = choice == (listView ? listViewButton : cardViewButton);
            choice.setSelected(selected);
            choice.setTextColor(selected ? UiStyles.ACCENT_TEXT : muted);
            choice.setBackground(interactive(selected ? UiStyles.SOFT : UiStyles.SURFACE, 11, 0));
            choice.setTextSize(13);
        }
    }

    private LinearLayout savedItemView(SavedItem item) {
        LinearLayout card=column(); card.setTag(listView?"list":"cards");
        card.setPadding(dp(12),dp(12),dp(12),dp(12));
        card.setBackground(surface(UiStyles.SURFACE,20,line));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.bottomMargin=dp(12); card.setLayoutParams(params);
        collectionControls.addSelection(card,item);
        String folder=item.categoryId()==null?"Other":item.categoryId();
        LinearLayout row=new LinearLayout(this); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int width=listView?68:100, height=listView?90:138;
        android.widget.FrameLayout cover=new android.widget.FrameLayout(this);
        cover.setBackground(surface(categoryTint(folder),14,0)); cover.setClipToOutline(true);
        View thumbnail;
        if(item.media().isEmpty()) {
            TextView placeholder=label(categorySymbol(folder),36,categoryColor(folder)); placeholder.setGravity(android.view.Gravity.CENTER);
            GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{categoryTint(folder),UiStyles.PAPER});
            placeholder.setBackground(gradient); thumbnail=placeholder;
        } else thumbnail=photo(item.media().get(0),height);
        cover.addView(thumbnail,new android.widget.FrameLayout.LayoutParams(-1,-1));
        if(!listView && !item.media().isEmpty()) {
            TextView photos=badge(item.media().size()+(item.media().size()==1?" photo":" photos"),ink,0xdd09090B);
            android.widget.FrameLayout.LayoutParams pp=new android.widget.FrameLayout.LayoutParams(-2,-2,android.view.Gravity.BOTTOM|android.view.Gravity.CENTER_HORIZONTAL);
            pp.bottomMargin=dp(8); cover.addView(photos,pp);
        }
        cover.setContentDescription(getString(R.string.view_saved_post)+": "+item.title());
        cover.setOnClickListener(v->showSavedPost(item)); row.addView(cover,new LinearLayout.LayoutParams(dp(width),dp(height)));
        LinearLayout info=column(); info.setPadding(dp(12),0,0,0);
        LinearLayout top=new LinearLayout(this); top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView source=label(item.siteName().isEmpty()?folder:item.siteName(),11,UiStyles.ACCENT_TEXT);
        source.setMaxLines(1); source.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(source,new LinearLayout.LayoutParams(0,-2,1)); top.addView(itemMenu(item),new LinearLayout.LayoutParams(dp(48),dp(48))); info.addView(top);
        TextView title=itemTitle(item,listView?15:18); title.setPadding(0,0,0,dp(4)); info.addView(title);
        if(!item.price().isEmpty()) { TextView price=label((item.currency()+" "+item.price()).trim()+(item.pricePeriod().isEmpty()?"":" / "+item.pricePeriod()),listView?14:16,ink); price.setTypeface(null,Typeface.BOLD); info.addView(price); }
        if(!item.location().isEmpty()) { TextView location=label(item.location(),12,muted); location.setMaxLines(1); location.setEllipsize(android.text.TextUtils.TruncateAt.END); info.addView(location); }
        int statusColor=item.status()==SavedItem.Status.ACTIVE?0xff71D5AD:item.status()==SavedItem.Status.REACHED_OUT?0xffF4C567:muted;
        TextView status=label("●  "+StatusLabels.label(this,item.status()),11,statusColor); info.addView(status);
        TextView capture=label(item.captureSummary(),11,muted); capture.setMaxLines(2); capture.setEllipsize(android.text.TextUtils.TruncateAt.END); info.addView(capture);
        info.setOnClickListener(v->showSavedPost(item));
        row.addView(info,new LinearLayout.LayoutParams(0,-2,1)); card.addView(row); return card;
    }

    private TextView itemTitle(SavedItem item, int size) {
        TextView title=label(item.title(),size,ink); title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END); return title;
    }

    private Button itemMenu(SavedItem item) {
        Button more=button("⋯",false); more.setTextSize(22);
        more.setContentDescription(getString(R.string.item_options,item.title())); more.setBackground(interactive(UiStyles.SURFACE,12,0));
        more.setOnClickListener(v->{ PopupMenu menu=new PopupMenu(this,more);
            menu.getMenu().add(R.string.view_saved_post).setOnMenuItemClickListener(option->{ showSavedPost(item); return true; });
            menu.getMenu().add("Move to trash").setOnMenuItemClickListener(option->{ confirmDelete(item); return true; }); menu.show(); });
        return more;
    }

    private void confirmDelete(SavedItem link) {
        new AlertDialog.Builder(this).setTitle("Move to trash?").setMessage(link.title()+"\n\nRecover this post and its photos from Trash before its retention period ends.")
            .setNegativeButton("Cancel", null).setPositiveButton("Move to trash", (d, which) -> {
                if (!storageHealthy) return;
                androidx.work.WorkManager.getInstance(this).cancelUniqueWork("snapshot-"+link.id());
                databaseWorker.execute(() -> {
                    try { ((SQLiteSavedItemRepository)store).trash(java.util.List.of(link.id())); }
                    catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, "Could not delete link", Toast.LENGTH_LONG).show()); }
                });
            }).show();
    }

    private void showEditor(String sharedUrl) { showEditor(sharedUrl,"",new ArrayList<>(),false); }

    private void showEditor(String sharedUrl, String sharedText, ArrayList<Uri> attachments, boolean automatic) {
        if (duplicateDialog != null) duplicateDialog.dismiss();
        if (editor != null) editor.dismiss();
        editorSharedText = sharedText; editorAttachments = new ArrayList<>(attachments);
        LinearLayout form = column(); form.setPadding(dp(24), dp(8), dp(24), dp(16));
        TextView intro=label(attachments.isEmpty()?"Save a link or text to your private collection.":attachments.size()+" shared photos · Ready to save locally",14,UiStyles.ACCENT_TEXT);
        intro.setPadding(dp(16),dp(16),dp(16),dp(16)); intro.setBackground(surface(UiStyles.SOFT,16,0)); form.addView(intro);
        EditText url = field(form,"Original link (optional for a shared photo)");
        url.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); url.setText(sharedUrl);
        EditText title = field(form,"Title (optional)");
        form.addView(label("FOLDER",11,muted));
        Spinner categories = new Spinner(this);
        ArrayAdapter<String> folderAdapter = new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,folders);
        categories.setAdapter(folderAdapter); categories.setContentDescription("Folder");
        categories.setBackground(surface(canvas,12,line)); form.addView(categories,new LinearLayout.LayoutParams(-1,dp(52)));
        categories.setSelection(Math.max(0,folders.indexOf(category.equals("All") ? "Other" : category)));
        Button newFolder = button(getString(R.string.new_folder),false); form.addView(newFolder,new LinearLayout.LayoutParams(-1,dp(48)));
        newFolder.setOnClickListener(v -> {
            EditText name = new EditText(this); name.setHint(R.string.folder_name); name.setSingleLine(true);
            AlertDialog create = new AlertDialog.Builder(this).setTitle(R.string.new_folder).setView(name)
                    .setNegativeButton("Cancel",null).setPositiveButton("Create",null).create();
            create.setOnShowListener(d -> create.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String value = name.getText().toString().trim();
                if (value.isEmpty() || value.length() > 60 || value.equalsIgnoreCase("All")) { name.setError("Use a folder name from 1 to 60 characters, other than All."); return; }
                int selected = -1;
                for (int i=0;i<folders.size();i++) if (folders.get(i).equalsIgnoreCase(value)) selected=i;
                if (selected < 0) { folders.add(value); selected=folders.size()-1; }
                folderAdapter.notifyDataSetChanged(); categories.setSelection(selected); create.dismiss();
            })); create.show();
        });
        EditText tags = field(form,"Tags (comma separated, e.g. apartment, wishlist)");
        EditText text = field(form,"Post text / caption"); text.setSingleLine(false); text.setMinLines(3);
        text.setText(sharedText);
        if (!attachments.isEmpty()) form.addView(label(attachments.size()+" shared photo(s) will be copied for offline viewing.",13,UiStyles.ACCENT_TEXT));
        PreviewMetadata[] preview = {null};
        String[] previewUrl = {""};
        TextView previewStatus = label("Keepr will fetch available public text and photos after saving. Some sites only expose a link.",13,muted); form.addView(previewStatus);
        Button fetch = button(getString(R.string.fetch_preview),false); form.addView(fetch,new LinearLayout.LayoutParams(-1,dp(48)));
        fetch.setOnClickListener(v -> {
            String requested;
            try { requested = UrlExtractor.normalize(url.getText().toString()); }
            catch (IllegalArgumentException e) { url.setError(e.getMessage()); return; }
            preview[0]=null; previewUrl[0]=requested;
            fetch.setEnabled(false); previewStatus.setText(R.string.fetching_preview);
            String originalText = text.getText().toString();
            previewWorker.execute(() -> {
                try {
                    PreviewMetadata result = new MetadataService().fetch(requested);
                    runOnUiThread(() -> {
                        if (isDestroyed()) return;
                        fetch.setEnabled(true);
                        if (!url.getText().toString().trim().equals(requested)) return;
                        preview[0]=result;
                        if (title.getText().toString().trim().isEmpty()) title.setText(result.title());
                        if (text.getText().toString().equals(originalText) && (originalText.isEmpty() || originalText.equals(sharedText)) && !result.description().isEmpty()) text.setText(result.description());
                        previewStatus.setText(getString(R.string.preview_photo_count,result.imageUrls().size()));
                    });
                } catch (Exception e) { runOnUiThread(() -> {
                    if (!isDestroyed()) { fetch.setEnabled(true); previewStatus.setText(R.string.preview_not_available); }
                }); }
            });
        });
        EditText notes = field(form,"My notes (optional)"); notes.setSingleLine(false);
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Save to Keepr")
                .setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save post",null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!storageHealthy) { Toast.makeText(this,"Storage is unavailable",Toast.LENGTH_LONG).show(); return; }
            try {
                String input = url.getText().toString().trim();
                String normalized = input.isEmpty() ? "" : UrlExtractor.normalize(input);
                if (normalized.isEmpty() && text.getText().toString().trim().isEmpty() && attachments.isEmpty()) { url.setError("Add a link, post text, or shared photo."); return; }
                List<String> tagNames = TagNames.parse(tags.getText().toString());
                PreviewMetadata snapshot = normalized.equals(previewUrl[0]) ? preview[0] : null;
                String itemTitle = title.getText().toString().trim();
                if (itemTitle.isEmpty()) itemTitle = normalized.isEmpty() ? "Shared post" : java.net.URI.create(normalized).getHost();
                long now=System.currentTimeMillis(); String id=java.util.UUID.randomUUID().toString();
                String savedTitle=itemTitle, postText=text.getText().toString().trim(), myNotes=notes.getText().toString().trim();
                String folder=categories.getSelectedItem().toString();
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
                dialog.setCancelable(false);
                Runnable saveCopy=() -> databaseWorker.execute(() -> {
                    List<CachedMedia> copied = new ArrayList<>(); int failed=0;
                    try {
                        for (Uri uri : attachments) {
                            try { copied.add(new MediaCache(this).copyShared(id,uri)); } catch (Exception e) { failed++; }
                        }
                        SavedItem link = new SavedItem(id,normalized,savedTitle,postText,
                                snapshot==null ? "" : snapshot.siteName(),snapshot==null ? "" : snapshot.imageUrl(),
                                snapshot==null ? "" : snapshot.price(),snapshot==null ? "" : snapshot.currency(),snapshot==null ? "" : snapshot.contact(),
                                myNotes,folder,SavedItem.Status.ACTIVE,normalized.isEmpty() ? SavedItem.PreviewState.READY : SavedItem.PreviewState.PENDING,
                                failed>0 ? "Some shared photos could not be copied. Share the missing photo again to save it." : "",now,now,
                                sharedText,java.util.Collections.unmodifiableList(tagNames),java.util.Collections.unmodifiableList(copied),0,0,0,
                                snapshot==null?"":snapshot.location(),snapshot==null?"":snapshot.productType(),snapshot==null?"":snapshot.pricePeriod(),snapshot==null?"":snapshot.metadataSource());
                        store.add(link);
                        if (!normalized.isEmpty()) {
                            try { SnapshotWorker.enqueue(this,id); } catch (Exception ignored) { /* Pending items are scheduled on next launch. */ }
                        }
                        runOnUiThread(() -> { if (!isDestroyed()) { rebuildFolderChips(); dialog.dismiss(); } });
                    } catch (Exception e) {
                        for (CachedMedia media : copied) new java.io.File(getFilesDir(),media.relativePath()).delete();
                        runOnUiThread(() -> { if (!isDestroyed()) {
                            dialog.setCancelable(true); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true); url.setError("Could not save. Please try again.");
                        } });
                    }
                });
                checkDuplicate(normalized,dialog,saveCopy);
            } catch (IllegalArgumentException e) { Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show(); }
        }));
        editor=dialog; editorUrl=url; editorTitle=title; editorNotes=notes; editorText=text; editorTags=tags; editorCategory=categories;
        dialog.show();
        UiStyles.styleEditor(dialog);
        if (automatic && !sharedUrl.isEmpty()) fetch.performClick();
    }

    private void unlockEditor(AlertDialog dialog) {
        dialog.setCancelable(true); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
    }
    private void checkDuplicate(String url,AlertDialog dialog,Runnable saveCopy) {
        if(url.isEmpty()) { saveCopy.run(); return; }
        databaseWorker.execute(()->{
            try {
                SavedItem existing=((SQLiteSavedItemRepository)store).findDuplicate(url);
                runOnUiThread(()->{
                    if(isDestroyed() || !dialog.isShowing()) return;
                    if(existing==null) { saveCopy.run(); return; }
                    unlockEditor(dialog);
                    duplicateDialog=new AlertDialog.Builder(this).setTitle(R.string.already_saved)
                        .setMessage(getString(R.string.duplicate_explanation,existing.title()))
                        .setNeutralButton(R.string.open_existing,(d,w)->{ dialog.dismiss(); showSavedPost(existing); })
                        .setNegativeButton(R.string.update_capture,(d,w)->{
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false); dialog.setCancelable(false);
                            databaseWorker.execute(()->{
                                try {
                                    SQLiteSavedItemRepository repository=(SQLiteSavedItemRepository)store;
                                    if(repository.find(existing.id())==null) throw new IllegalStateException("Saved post no longer exists");
                                    repository.retryPhotos(existing.id()); SnapshotWorker.refresh(this,existing.id());
                                    runOnUiThread(()->{ if(!isDestroyed()) { dialog.dismiss(); Toast.makeText(this,R.string.capture_queued,Toast.LENGTH_SHORT).show(); showSavedPost(existing); } });
                                } catch(Exception error) { runOnUiThread(()->{ if(!isDestroyed()) { unlockEditor(dialog); Toast.makeText(this,"Could not queue capture. Please try again.",Toast.LENGTH_LONG).show(); } }); }
                            });
                        })
                        .setPositiveButton(R.string.save_another_copy,(d,w)->{
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false); dialog.setCancelable(false); saveCopy.run();
                        }).create();
                    duplicateDialog.show();
                });
            } catch(Exception error) { runOnUiThread(()->{ if(!isDestroyed()) { unlockEditor(dialog); Toast.makeText(this,"Could not check existing posts. Please try again.",Toast.LENGTH_LONG).show(); } }); }
        });
    }

    private ImageView photo(CachedMedia media, int height) {
        ImageView view = new ImageView(this); view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setBackground(surface(canvas,14,line)); view.setClipToOutline(true); view.setTag(media.id());
        view.setContentDescription(getString(R.string.saved_photo));
        imageWorker.execute(() -> {
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options(); options.inJustDecodeBounds=true;
            String path = new java.io.File(getFilesDir(),media.relativePath()).getPath();
            android.graphics.BitmapFactory.decodeFile(path,options);
            options.inSampleSize=1;
            while (Math.max(options.outWidth,options.outHeight)/options.inSampleSize > 900) options.inSampleSize*=2;
            options.inJustDecodeBounds=false;
            android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeFile(path,options);
            runOnUiThread(() -> { if (!isDestroyed() && bitmap!=null && media.id().equals(view.getTag())) view.setImageBitmap(bitmap); });
        });
        return view;
    }

    private void showSavedPost(SavedItem item) {
        startActivity(new Intent(this,PostDetailActivity.class).putExtra("item_id",item.id()));
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        if (editor != null && editor.isShowing()) {
            out.putBoolean("editor_open", true);
            out.putString("draft_url", editorUrl.getText().toString());
            out.putString("draft_title", editorTitle.getText().toString());
            out.putString("draft_notes", editorNotes.getText().toString());
            out.putString("draft_folder",editorCategory.getSelectedItem().toString());
            out.putString("draft_text",editorText.getText().toString()); out.putString("draft_tags",editorTags.getText().toString());
            out.putString("shared_text",editorSharedText);
            ArrayList<String> uris=new ArrayList<>(); for (Uri uri : editorAttachments) uris.add(uri.toString());
            out.putStringArrayList("draft_attachments",uris);
        }
        out.putString("filter_folder",category); out.putString("filter_query",query); out.putString("filter_status",statusFilter==null?null:statusFilter.name());
        collectionControls.save(out);
        super.onSaveInstanceState(out);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); receiveShare(intent);
    }

    @Override protected void onDestroy() {
        if (workUpdates!=null && workObserver!=null) workUpdates.removeObserver(workObserver);
        if(collectionControls!=null) collectionControls.close();
        if(duplicateDialog!=null) duplicateDialog.dismiss();
        if(editor!=null) editor.dismiss();
        imageWorker.shutdownNow();
        previewWorker.shutdownNow();
        databaseWorker.execute(store::close);
        databaseWorker.shutdown();
        super.onDestroy();
    }

    private void receiveShare(Intent intent) {
        if (!Intent.ACTION_SEND.equals(intent.getAction()) && !Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) return;
        String type=intent.getType(); if (type==null || !(type.startsWith("text/") || type.startsWith("image/"))) return;
        String text=""; ArrayList<Uri> attachments=new ArrayList<>();
        try {
            CharSequence shared=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (shared!=null) text=shared.toString();
            if (text.isEmpty()) {
                String html=intent.getStringExtra(Intent.EXTRA_HTML_TEXT);
                if (html!=null) text=android.text.Html.fromHtml(html,android.text.Html.FROM_HTML_MODE_LEGACY).toString();
            }
            if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
                ArrayList<Uri> streams=intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (streams!=null) for (Uri uri : streams) addAttachment(attachments,uri);
            } else addAttachment(attachments,intent.getParcelableExtra(Intent.EXTRA_STREAM));
            if (intent.getClipData()!=null) for (int i=0;i<intent.getClipData().getItemCount();i++) {
                android.content.ClipData.Item clip=intent.getClipData().getItemAt(i);
                addAttachment(attachments,clip.getUri());
                if (text.isEmpty() && clip.getText()!=null) text=clip.getText().toString();
            }
        } catch (RuntimeException e) { Toast.makeText(this,"Some shared content could not be read",Toast.LENGTH_LONG).show(); }
        text=text.substring(0,Math.min(65536,text.length()));
        List<String> urls=UrlExtractor.extract(text);
        String sharedText=text; ArrayList<Uri> images=attachments;
        if (urls.isEmpty()) showEditor("",sharedText,images,false);
        else if (urls.size()==1) showEditor(urls.get(0),sharedText,images,true);
        else new AlertDialog.Builder(this).setTitle("Choose the original post link")
                .setItems(urls.toArray(new String[0]),(dialog,index)->showEditor(urls.get(index),sharedText,images,true))
                .setNegativeButton("Cancel",null).show();
    }
    private void addAttachment(ArrayList<Uri> attachments, Uri uri) {
        if (uri!=null && "content".equals(uri.getScheme()) && !attachments.contains(uri)) attachments.add(uri);
    }

    private void openLink(String url) {
        try { UrlExtractor.normalize(url); } catch (IllegalArgumentException e) { return; }
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, "No browser is installed", Toast.LENGTH_LONG).show(); }
    }
    private EditText field(LinearLayout form, String hint) {
        TextView caption=label(hint,13,muted); caption.setPadding(0,dp(18),0,dp(8)); form.addView(caption);
        EditText input = new EditText(this); input.setHint(hint); input.setSingleLine(true);
        input.setTextSize(15); input.setTextColor(ink); input.setHintTextColor(muted);
        input.setPadding(dp(14), dp(12), dp(14), dp(12)); input.setBackground(surface(UiStyles.SURFACE, 16, line));
        form.addView(input, new LinearLayout.LayoutParams(-1, -2)); input.setMinHeight(dp(52)); return input;
    }
    private GradientDrawable surface(int fill, int radius, int stroke) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(fill); shape.setCornerRadius(dp(radius));
        if (stroke != 0) shape.setStroke(dp(1), stroke); return shape;
    }
    private android.graphics.drawable.RippleDrawable interactive(int fill, int radius, int stroke) {
        return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33FF98A5),
                surface(fill, radius, stroke), surface(UiStyles.SURFACE, radius, 0));
    }
    private Button button(String text, boolean primary) {
        return UiStyles.button(this, text, primary);
    }
    private TextView badge(String text, int foreground, int background) {
        TextView badge = label(text, 10, foreground); badge.setTypeface(null, Typeface.BOLD);
        badge.setPadding(dp(10), dp(7), dp(10), dp(7)); badge.setBackground(surface(background, 10, 0)); return badge;
    }
    private int categoryColor(String category) {
        switch (category) {
            case "Real Estate": case "Rentals": return 0xffEFB09D;
            case "Electronics": return 0xffB3C4E8;
            case "Vehicles": return 0xffACCCBC;
            case "Jobs": return 0xffDEC391;
            default: return UiStyles.ACCENT_TEXT;
        }
    }
    private int categoryTint(String category) {
        switch (category) {
            case "Real Estate": case "Rentals": return 0xff44302C;
            case "Electronics": return 0xff2D3446;
            case "Vehicles": return 0xff293C33;
            case "Jobs": return 0xff40382A;
            default: return UiStyles.SOFT;
        }
    }
    private String categorySymbol(String category) {
        switch (category) {
            case "Real Estate": case "Rentals": return "⌂";
            case "Electronics": return "▣";
            case "Vehicles": return "↗";
            case "Jobs": return "▤";
            default: return "↗";
        }
    }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(6), 0, dp(6)); return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
