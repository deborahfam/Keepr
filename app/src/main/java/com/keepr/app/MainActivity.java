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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
    private static final String[] CATEGORIES = {"Marketplace", "Rentals", "Social", "Other"};
    private final List<SavedLink> links = new ArrayList<>();
    private LinkStore store;
    private LinearLayout cards;
    private TextView count;
    private String category = "All", query = "";
    private boolean storageHealthy = true;
    private final int green = Color.rgb(37, 102, 81);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new LinkStore(this);
        try { links.addAll(store.load()); }
        catch (org.json.JSONException e) {
            storageHealthy = false;
            new AlertDialog.Builder(this).setTitle("Saved links could not be read")
                .setMessage("Your stored data has been preserved. Saving is disabled until the storage issue is resolved.")
                .setPositiveButton("OK", null).show();
        }
        buildScreen();
        if (state == null) receiveShare(getIntent());
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
                new String[]{"All", "Marketplace", "Rentals", "Social", "Other"}));
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
        for (SavedLink link : links) {
            String searchable = (link.title + " " + link.url + " " + link.notes).toLowerCase(Locale.ROOT);
            if ((!category.equals("All") && !category.equals(link.category)) || !searchable.contains(query.toLowerCase(Locale.ROOT))) continue;
            shown++;
            LinearLayout card = column(); card.setPadding(dp(16), dp(12), dp(16), dp(12));
            GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(16));
            card.setBackground(bg);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(12);
            cards.addView(card, params);
            card.addView(label(link.category.toUpperCase(Locale.ROOT), 11, green));
            TextView title = label(link.title, 21, Color.BLACK); title.setTypeface(null, Typeface.BOLD); card.addView(title);
            card.addView(label(link.url, 13, Color.DKGRAY));
            if (!link.notes.isEmpty()) card.addView(label(link.notes, 15, Color.DKGRAY));
            LinearLayout actions = new LinearLayout(this);
            Button open = new Button(this); open.setText(R.string.open_link); open.setOnClickListener(v -> openLink(link.url));
            Button delete = new Button(this); delete.setText(R.string.delete_link); delete.setOnClickListener(v ->
                new AlertDialog.Builder(this).setTitle("Delete saved link?").setMessage(link.title)
                    .setNegativeButton("Cancel", null).setPositiveButton("Delete", (d, which) -> {
                        if (!storageHealthy) return;
                        links.remove(link); store.save(links); renderCards();
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
        LinearLayout form = column(); form.setPadding(dp(24), dp(8), dp(24), 0);
        EditText url = field(form, "Link (https://…)"); url.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); url.setText(sharedUrl);
        EditText title = field(form, "Title (optional)");
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
                SavedLink link = SavedLink.create(title.getText().toString(), url.getText().toString(), categories.getSelectedItem().toString(), notes.getText().toString());
                links.add(0, link); store.save(links); renderCards(); dialog.dismiss();
            } catch (IllegalArgumentException e) { url.setError(e.getMessage()); }
        }));
        dialog.show();
    }

    private void receiveShare(Intent intent) {
        if (!Intent.ACTION_SEND.equals(intent.getAction()) || !"text/plain".equals(intent.getType())) return;
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text == null) return;
        Matcher matcher = Pattern.compile("https?://[^\\s<>]+", Pattern.CASE_INSENSITIVE).matcher(text);
        showEditor(matcher.find() ? matcher.group() : "");
    }

    private void openLink(String url) {
        if (!SavedLink.isWebUrl(url)) return;
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
