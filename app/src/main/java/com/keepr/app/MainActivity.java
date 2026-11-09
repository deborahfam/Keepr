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
    private final int ink = Color.rgb(39, 34, 59);
    private final int muted = Color.rgb(107, 101, 122);
    private final int accent = Color.rgb(102, 72, 202);
    private final int canvas = Color.rgb(248, 246, 242);
    private final int line = Color.rgb(228, 223, 235);
    private final List<Button> categoryButtons = new ArrayList<>();

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
        root.setBackgroundColor(canvas);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });

        LinearLayout brand = new LinearLayout(this); brand.setGravity(android.view.Gravity.CENTER_VERTICAL);
        brand.setPadding(dp(24), dp(12), dp(24), dp(12));
        TextView mark = label("k", 25, Color.WHITE); mark.setGravity(android.view.Gravity.CENTER);
        mark.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        mark.setBackground(surface(accent, 14, 0));
        brand.addView(mark, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView name = label("keepr", 26, ink); name.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1); nameParams.leftMargin = dp(10);
        brand.addView(name, nameParams);
        TextView privacy = badge("ON YOUR DEVICE", accent, Color.rgb(235, 229, 252));
        brand.addView(privacy); root.addView(brand);

        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        LinearLayout content = column(); content.setPadding(dp(24), dp(8), dp(24), dp(20));
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout hero = column(); hero.setPadding(dp(24), dp(20), dp(24), dp(22));
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(47, 38, 73), Color.rgb(83, 59, 119)});
        gradient.setCornerRadius(dp(24)); hero.setBackground(gradient);
        TextView eyebrow = label("GOOD FINDS, SAFE KEEPING", 11, Color.rgb(221, 203, 255));
        eyebrow.setLetterSpacing(0.12f); hero.addView(eyebrow);
        TextView heading = label("Keep a little\npossibility.", 32, Color.WHITE);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setLineSpacing(0, 1.02f); hero.addView(heading);
        TextView subtitle = label("The places, deals, and ideas you want to come back to.", 15, Color.rgb(231, 222, 244));
        subtitle.setLineSpacing(dp(3), 1); hero.addView(subtitle);
        content.addView(hero);

        LinearLayout collectionHeading = new LinearLayout(this);
        collectionHeading.setGravity(android.view.Gravity.CENTER_VERTICAL);
        collectionHeading.setPadding(0, dp(22), 0, dp(8));
        TextView collection = label("Your collection", 23, ink); collection.setTypeface(null, Typeface.BOLD);
        collectionHeading.addView(collection, new LinearLayout.LayoutParams(0, -2, 1));
        count = badge("", accent, Color.rgb(235, 229, 252)); collectionHeading.addView(count);
        content.addView(collectionHeading);

        EditText search = new EditText(this); search.setSingleLine(true);
        search.setHint(R.string.search_finds); search.setTextSize(15); search.setTextColor(ink); search.setHintTextColor(muted);
        search.setPadding(dp(16), dp(12), dp(16), dp(12)); search.setBackground(surface(Color.WHITE, 16, line));
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        search.setCompoundDrawablePadding(dp(10));
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        content.addView(search, new LinearLayout.LayoutParams(-1, dp(54)));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { query = s.toString(); renderCards(); }
            public void afterTextChanged(Editable e) {}
        });
        HorizontalScrollView filters = new HorizontalScrollView(this); filters.setHorizontalScrollBarEnabled(false);
        filters.setPadding(0, dp(12), 0, dp(14));
        LinearLayout chips = new LinearLayout(this);
        categoryButtons.clear();
        List<String> options = new ArrayList<>(); options.add("All"); java.util.Collections.addAll(options, CATEGORIES);
        for (String option : options) {
            Button chip = button(option.equals("All") ? getString(R.string.all_finds) : option, false);
            chip.setTag(option); chip.setTextSize(13); chip.setMinHeight(dp(48));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, dp(48)); cp.rightMargin = dp(8);
            chips.addView(chip, cp); categoryButtons.add(chip);
            chip.setOnClickListener(v -> { category = option; updateFilters(); renderCards(); });
        }
        filters.addView(chips); content.addView(filters); updateFilters();
        cards = column(); content.addView(cards);

        LinearLayout dock = column(); dock.setPadding(dp(24), dp(10), dp(24), dp(12));
        dock.setBackground(surface(canvas, 0, 0));
        Button add = button(getString(R.string.save_link), true);
        add.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_add, 0, 0, 0);
        add.setCompoundDrawablePadding(dp(10));
        add.setOnClickListener(v -> showEditor("")); dock.addView(add, new LinearLayout.LayoutParams(-1, dp(56)));
        root.addView(dock); setContentView(root); renderCards();
    }

    private void updateFilters() {
        for (Button chip : categoryButtons) {
            boolean selected = category.equals(chip.getTag());
            chip.setSelected(selected); chip.setTextColor(selected ? Color.WHITE : muted);
            chip.setBackground(interactive(selected ? accent : Color.WHITE, 24, selected ? 0 : line));
            chip.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void renderCards() {
        if (cards == null) return;
        cards.removeAllViews(); int shown = 0;
        for (SavedItem link : links) {
            String searchable = (link.title() + " " + link.url() + " " + link.notes() + " " + link.description()).toLowerCase(Locale.ROOT);
            if ((!category.equals("All") && !category.equals(link.categoryId())) || !searchable.contains(query.toLowerCase(Locale.ROOT))) continue;
            shown++;
            String itemCategory = link.categoryId() == null ? "Other" : link.categoryId();
            int categoryColor = categoryColor(itemCategory);
            int tint = categoryTint(itemCategory);
            LinearLayout card = column(); card.setPadding(dp(18), dp(18), dp(18), dp(16));
            card.setBackground(surface(Color.WHITE, 22, line));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(14);
            cards.addView(card, params);

            LinearLayout meta = new LinearLayout(this); meta.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView symbol = label(categorySymbol(itemCategory), 18, categoryColor);
            symbol.setGravity(android.view.Gravity.CENTER); symbol.setTypeface(null, Typeface.BOLD);
            symbol.setBackground(surface(tint, 12, 0));
            meta.addView(symbol, new LinearLayout.LayoutParams(dp(42), dp(42)));
            LinearLayout source = column();
            TextView categoryLabel = label(itemCategory, 12, categoryColor); categoryLabel.setTypeface(null, Typeface.BOLD);
            categoryLabel.setPadding(0, 0, 0, dp(3)); source.addView(categoryLabel);
            String host = Uri.parse(link.url()).getHost();
            TextView domain = label(host == null ? link.url() : host.replaceFirst("^www\\.", ""), 12, muted);
            domain.setPadding(0, 0, 0, 0); domain.setSingleLine(true);
            domain.setEllipsize(android.text.TextUtils.TruncateAt.END); source.addView(domain);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, -2, 1); sp.leftMargin = dp(10);
            meta.addView(source, sp);
            Button more = button("⋯", false); more.setTextSize(22); more.setContentDescription(getString(R.string.item_options, link.title()));
            more.setBackground(interactive(Color.WHITE, 12, 0));
            meta.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
            more.setOnClickListener(v -> {
                PopupMenu menu = new PopupMenu(this, more);
                menu.getMenu().add(R.string.delete_link).setOnMenuItemClickListener(item -> { confirmDelete(link); return true; });
                menu.show();
            });
            card.addView(meta);
            TextView title = label(link.title(), 21, ink); title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            title.setPadding(0, dp(14), 0, dp(6)); card.addView(title);
            if (!link.description().isEmpty()) {
                TextView description = label(link.description(), 14, muted); description.setLineSpacing(dp(3), 1);
                card.addView(description);
            }
            if (!link.notes().isEmpty()) {
                TextView notes = label(link.notes(), 14, ink); notes.setPadding(dp(12), dp(10), dp(12), dp(10));
                notes.setBackground(surface(canvas, 10, 0)); card.addView(notes);
            }
            LinearLayout footer = new LinearLayout(this); footer.setGravity(android.view.Gravity.CENTER_VERTICAL);
            footer.setPadding(0, dp(14), 0, 0);
            if (!link.price().isEmpty()) {
                TextView price = badge((link.currency() + " " + link.price()).trim(), categoryColor, tint);
                footer.addView(price, new LinearLayout.LayoutParams(0, -2, 1));
            } else {
                String status = link.status().name().replace('_', ' ').toLowerCase(Locale.ROOT);
                TextView state = label("•  " + status.substring(0, 1).toUpperCase(Locale.ROOT) + status.substring(1), 12, muted);
                footer.addView(state, new LinearLayout.LayoutParams(0, -2, 1));
            }
            Button open = button(getString(R.string.open_link), false);
            open.setOnClickListener(v -> openLink(link.url()));
            LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-2, dp(48)); op.leftMargin = dp(10);
            footer.addView(open, op); card.addView(footer);
        }
        count.setText(String.valueOf(shown));
        count.setContentDescription(getResources().getQuantityString(R.plurals.saved_count, shown, shown));
        if (shown == 0) {
            LinearLayout empty = column(); empty.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            empty.setPadding(dp(24), dp(28), dp(24), dp(30));
            empty.setBackground(surface(Color.WHITE, 22, line));
            TextView icon = label("↗", 28, accent); icon.setGravity(android.view.Gravity.CENTER);
            icon.setBackground(surface(Color.rgb(239, 233, 253), 20, 0));
            empty.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));
            TextView title = label(links.isEmpty() ? "Make room for a good find." : "Nothing here just yet.", 20, ink);
            title.setGravity(android.view.Gravity.CENTER); title.setTypeface(null, Typeface.BOLD); empty.addView(title);
            TextView explanation = label(links.isEmpty() ? "Found something you love? Save its link here, or share it to Keepr from another app." : "Try a different search or explore another category.", 14, muted);
            explanation.setGravity(android.view.Gravity.CENTER); explanation.setLineSpacing(dp(3), 1); empty.addView(explanation);
            cards.addView(empty);
        }
    }

    private void confirmDelete(SavedItem link) {
        new AlertDialog.Builder(this).setTitle("Delete saved link?").setMessage(link.title())
            .setNegativeButton("Cancel", null).setPositiveButton("Delete", (d, which) -> {
                if (!storageHealthy) return;
                databaseWorker.execute(() -> {
                    try { store.delete(link.id()); }
                    catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, "Could not delete link", Toast.LENGTH_LONG).show()); }
                });
            }).show();
    }

    private void showEditor(String sharedUrl) {
        if (editor != null) editor.dismiss();
        LinearLayout form = column(); form.setPadding(dp(24), dp(8), dp(24), dp(16));
        form.addView(label("A good find deserves a place to stay.", 14, muted));
        EditText url = field(form, "Link (https://…)"); url.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); url.setText(sharedUrl);
        EditText title = field(form, "Title (optional)");
        PreviewMetadata[] preview = {null};
        String[] previewUrl = {""}, previewError = {""};
        TextView previewStatus = label("Preview is optional. You can always save the link offline.", 13, muted);
        form.addView(previewStatus);
        Button fetch = button(getString(R.string.fetch_preview), false); form.addView(fetch, new LinearLayout.LayoutParams(-1, dp(48)));
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
        form.addView(label("CATEGORY", 11, muted));
        categories.setContentDescription("Category"); categories.setBackground(surface(canvas, 12, line));
        form.addView(categories, new LinearLayout.LayoutParams(-1, dp(52)));
        EditText notes = field(form, "Notes (optional)"); notes.setSingleLine(false);
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Save to your vault")
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
        form.addView(label(hint, 12, muted));
        EditText input = new EditText(this); input.setHint(hint); input.setSingleLine(true);
        input.setTextSize(15); input.setTextColor(ink); input.setHintTextColor(muted);
        input.setPadding(dp(14), dp(12), dp(14), dp(12)); input.setBackground(surface(canvas, 12, line));
        form.addView(input, new LinearLayout.LayoutParams(-1, -2)); input.setMinHeight(dp(52)); return input;
    }
    private GradientDrawable surface(int fill, int radius, int stroke) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(fill); shape.setCornerRadius(dp(radius));
        if (stroke != 0) shape.setStroke(dp(1), stroke); return shape;
    }
    private android.graphics.drawable.RippleDrawable interactive(int fill, int radius, int stroke) {
        return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x226648CA),
                surface(fill, radius, stroke), surface(Color.WHITE, radius, 0));
    }
    private Button button(String text, boolean primary) {
        Button button = new Button(this); button.setText(text); button.setAllCaps(false);
        button.setTextSize(15); button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(primary ? Color.WHITE : accent);
        button.setBackground(interactive(primary ? accent : Color.rgb(242, 237, 254), 14, 0));
        button.setPadding(dp(16), 0, dp(16), 0); button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(dp(48)); button.setMinimumHeight(dp(48)); button.setStateListAnimator(null);
        return button;
    }
    private TextView badge(String text, int foreground, int background) {
        TextView badge = label(text, 10, foreground); badge.setTypeface(null, Typeface.BOLD);
        badge.setPadding(dp(10), dp(7), dp(10), dp(7)); badge.setBackground(surface(background, 10, 0)); return badge;
    }
    private int categoryColor(String category) {
        switch (category) {
            case "Real Estate": case "Rentals": return Color.rgb(145, 72, 47);
            case "Electronics": return Color.rgb(48, 88, 151);
            case "Vehicles": return Color.rgb(41, 110, 96);
            case "Jobs": return Color.rgb(141, 93, 23);
            default: return accent;
        }
    }
    private int categoryTint(String category) {
        switch (category) {
            case "Real Estate": case "Rentals": return Color.rgb(251, 232, 221);
            case "Electronics": return Color.rgb(229, 237, 253);
            case "Vehicles": return Color.rgb(223, 242, 235);
            case "Jobs": return Color.rgb(251, 240, 211);
            default: return Color.rgb(239, 233, 253);
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
