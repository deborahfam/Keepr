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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String[] CATEGORIES = {"Real Estate", "Electronics", "Vehicles", "Jobs", "Marketplace", "Rentals", "Social", "Other"};
    private final List<SavedItem> links = new ArrayList<>();
    private SavedItemRepository store;
    private final ExecutorService databaseWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService previewWorker = Executors.newSingleThreadExecutor();
    private LinearLayout cards;
    private TextView count;
    private AlertDialog editor;
    private EditText editorUrl, editorTitle, editorNotes;
    private Spinner editorCategory;
    private String category = "All", query = "";
    private boolean storageHealthy = false;
    private final int green = Color.rgb(37, 102, 81);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new SQLiteSavedItemRepository(this);
        buildScreen();
        databaseWorker.execute(() -> {
            try {
                store.observe(snapshot -> runOnUiThread(() -> {
                    if (!isDestroyed()) { links.clear(); links.addAll(snapshot); renderCards(); }
                }));
                List<SavedItem> loaded = store.load();
                runOnUiThread(() -> { if (!isDestroyed()) { storageHealthy = true; links.clear(); links.addAll(loaded); renderCards(); } });
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
            showEditor(state.getString("draft_url", ""));
            editorTitle.setText(state.getString("draft_title", ""));
            editorNotes.setText(state.getString("draft_notes", ""));
            editorCategory.setSelection(state.getInt("draft_category", 0));
        } else if (state == null) receiveShare(getIntent());
    }

    private void buildScreen() {
        LinearLayout root = column();
        root.setPadding(dp(24), dp(16), dp(24), 0);
        root.setBackgroundColor(Color.rgb(246, 247, 242));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(24) + insets.getSystemWindowInsetLeft(),
                    dp(16) + insets.getSystemWindowInsetTop(),
                    dp(24) + insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.addView(label("KEEPR  /  YOUR PERSONAL COLLECTION", 12, green));
        TextView heading = label("Good finds.\nKept together.", 34, Color.rgb(27, 42, 35));
        heading.setTypeface(null, Typeface.BOLD);
        root.addView(heading);
        root.addView(label("A little space for things worth coming back to.", 15, Color.DKGRAY));
        Button add = new Button(this); add.setText(R.string.save_link);
        add.setOnClickListener(v -> showEditor("")); root.addView(add);
        EditText search = new EditText(this);
        search.setSingleLine(true); search.setHint("Search your saved finds");
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        root.addView(search);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { query = s.toString(); renderCards(); }
            public void afterTextChanged(Editable e) {}
        });
        Spinner filter = new Spinner(this);
        filter.setContentDescription("Filter saved links by category");
        filter.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"All", "Real Estate", "Electronics", "Vehicles", "Jobs", "Marketplace", "Rentals", "Social", "Other"}));
        filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int position, long id) { category = p.getItemAtPosition(position).toString(); renderCards(); }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        root.addView(filter, new LinearLayout.LayoutParams(-1, dp(56)));
        count = label("", 13, green); root.addView(count);
        ScrollView scroll = new ScrollView(this); cards = column();
        scroll.addView(cards); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root); renderCards();
    }

    private void renderCards() {
        if (cards == null) return;
        cards.removeAllViews(); int shown = 0;
        for (SavedItem link : links) {
            String searchable = (link.title() + " " + link.url() + " " + link.notes() + " " + link.description()).toLowerCase(Locale.ROOT);
            if ((!category.equals("All") && !category.equals(link.categoryId())) || !searchable.contains(query.toLowerCase(Locale.ROOT))) continue;
            shown++;
            LinearLayout card = column(); card.setPadding(dp(16), dp(12), dp(16), dp(12));
            GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(16));
            card.setBackground(bg);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(12);
            cards.addView(card, params);
            card.addView(label((link.categoryId() == null ? "Other" : link.categoryId()).toUpperCase(Locale.ROOT), 11, green));
            TextView title = label(link.title(), 21, Color.BLACK); title.setTypeface(null, Typeface.BOLD); card.addView(title);
            card.addView(label(link.url(), 13, Color.DKGRAY));
            if (!link.price().isEmpty()) card.addView(label(link.currency() + " " + link.price(), 18, green));
            if (!link.description().isEmpty()) card.addView(label(link.description(), 15, Color.DKGRAY));
            if (!link.notes().isEmpty()) card.addView(label(link.notes(), 15, Color.DKGRAY));
            LinearLayout actions = new LinearLayout(this);
            Button open = new Button(this); open.setText(R.string.open_link); open.setOnClickListener(v -> openLink(link.url()));
            Button delete = new Button(this); delete.setText(R.string.delete_link); delete.setOnClickListener(v ->
                new AlertDialog.Builder(this).setTitle("Delete saved link?").setMessage(link.title())
                    .setNegativeButton("Cancel", null).setPositiveButton("Delete", (d, which) -> {
                        if (!storageHealthy) return;
                        databaseWorker.execute(() -> {
                            try { store.delete(link.id()); }
                            catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, "Could not delete link", Toast.LENGTH_LONG).show()); }
                        });
                    }).show());
            actions.addView(open); actions.addView(delete); card.addView(actions);
        }
        count.setText(getResources().getQuantityString(R.plurals.saved_count, shown, shown));
        if (shown == 0) {
            cards.addView(label(links.isEmpty() ? "Your next great find starts here." : "No matching finds.", 22, green));
            cards.addView(label(links.isEmpty() ? "Save a link above, or share a post to Keepr from another app." : "Try another search or category.", 16, Color.DKGRAY));
        }
    }

    private void showEditor(String sharedUrl) {
        if (editor != null) editor.dismiss();
        LinearLayout form = column(); form.setPadding(dp(24), dp(8), dp(24), 0);
        EditText url = field(form, "Link (https://…)"); url.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); url.setText(sharedUrl);
        EditText title = field(form, "Title (optional)");
        PreviewMetadata[] preview = {null};
        String[] previewUrl = {""}, previewError = {""};
        TextView previewStatus = label("Preview is optional. You can always save the link offline.", 13, Color.DKGRAY);
        form.addView(previewStatus);
        Button fetch = new Button(this); fetch.setText(R.string.fetch_preview); form.addView(fetch);
        fetch.setOnClickListener(v -> {
            String requested;
            try { requested = UrlExtractor.normalize(url.getText().toString()); }
            catch (IllegalArgumentException e) { url.setError(e.getMessage()); return; }
            preview[0] = null; previewError[0] = ""; previewUrl[0] = requested;
            fetch.setEnabled(false); previewStatus.setText(R.string.fetching_preview);
            previewWorker.execute(() -> {
                try {
                    PreviewMetadata result = new MetadataService().fetch(requested);
                    runOnUiThread(() -> {
                        if (isDestroyed()) return;
                        preview[0] = result; fetch.setEnabled(true);
                        if (title.getText().toString().trim().isEmpty()) title.setText(result.title());
                        previewStatus.setText(result.description().isEmpty() ? "Preview fetched. Some fields may be unavailable." : result.description());
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        if (isDestroyed()) return;
                        previewError[0] = e.getMessage() == null ? "Preview unavailable" : e.getMessage();
                        fetch.setEnabled(true); previewStatus.setText(getString(R.string.preview_error, previewError[0]));
                    });
                }
            });
        });
        Spinner categories = new Spinner(this);
        categories.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, CATEGORIES));
        categories.setContentDescription("Category"); form.addView(categories);
        EditText notes = field(form, "Notes (optional)"); notes.setSingleLine(false);
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Keep something good")
            .setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!storageHealthy) { Toast.makeText(this, "Storage is unavailable", Toast.LENGTH_LONG).show(); return; }
            try {
                String normalized = UrlExtractor.normalize(url.getText().toString());
                PreviewMetadata snapshot = normalized.equals(previewUrl[0]) ? preview[0] : null;
                String error = normalized.equals(previewUrl[0]) ? previewError[0] : "";
                String itemTitle = title.getText().toString().trim();
                if (itemTitle.isEmpty()) itemTitle = java.net.URI.create(normalized).getHost();
                long now = System.currentTimeMillis();
                SavedItem link = new SavedItem(java.util.UUID.randomUUID().toString(), normalized, itemTitle,
                        snapshot == null ? "" : snapshot.description(), snapshot == null ? "" : snapshot.siteName(),
                        snapshot == null ? "" : snapshot.imageUrl(), snapshot == null ? "" : snapshot.price(),
                        snapshot == null ? "" : snapshot.currency(), snapshot == null ? "" : snapshot.contact(),
                        notes.getText().toString().trim(), categories.getSelectedItem().toString(), SavedItem.Status.ACTIVE,
                        snapshot != null ? SavedItem.PreviewState.READY : error.isEmpty() ? SavedItem.PreviewState.PENDING : SavedItem.PreviewState.FAILED,
                        error, now, now);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                databaseWorker.execute(() -> {
                    try {
                        store.add(link);
                        runOnUiThread(() -> { if (!isDestroyed()) { dialog.dismiss(); } });
                    } catch (Exception e) { runOnUiThread(() -> {
                        if (!isDestroyed()) { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); url.setError("Could not save. Please try again."); }
                    }); }
                });
            } catch (IllegalArgumentException e) { url.setError(e.getMessage()); }
        }));
        editor = dialog; editorUrl = url; editorTitle = title; editorNotes = notes; editorCategory = categories;
        dialog.show();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        if (editor != null && editor.isShowing()) {
            out.putBoolean("editor_open", true);
            out.putString("draft_url", editorUrl.getText().toString());
            out.putString("draft_title", editorTitle.getText().toString());
            out.putString("draft_notes", editorNotes.getText().toString());
            out.putInt("draft_category", editorCategory.getSelectedItemPosition());
        }
        super.onSaveInstanceState(out);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); receiveShare(intent);
    }

    @Override protected void onDestroy() {
        previewWorker.shutdownNow();
        databaseWorker.execute(store::close);
        databaseWorker.shutdown();
        super.onDestroy();
    }

    private void receiveShare(Intent intent) {
        if (!Intent.ACTION_SEND.equals(intent.getAction()) || !"text/plain".equals(intent.getType())) return;
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text == null && intent.getClipData() != null && intent.getClipData().getItemCount() > 0) {
            CharSequence clip = intent.getClipData().getItemAt(0).getText();
            if (clip != null) text = clip.toString();
        }
        List<String> urls = UrlExtractor.extract(text);
        if (urls.isEmpty()) {
            Toast.makeText(this, "No valid web link found. Paste a link to save it.", Toast.LENGTH_LONG).show();
            showEditor("");
        } else if (urls.size() == 1) showEditor(urls.get(0));
        else new AlertDialog.Builder(this).setTitle("Choose a link to keep")
            .setItems(urls.toArray(new String[0]), (dialog, index) -> showEditor(urls.get(index)))
            .setNegativeButton("Cancel", null).show();
    }

    private void openLink(String url) {
        try { UrlExtractor.normalize(url); } catch (IllegalArgumentException e) { return; }
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, "No browser is installed", Toast.LENGTH_LONG).show(); }
    }
    private EditText field(LinearLayout form, String hint) {
        EditText input = new EditText(this); input.setHint(hint); input.setSingleLine(true); form.addView(input); return input;
    }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(8), 0, dp(8)); return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
