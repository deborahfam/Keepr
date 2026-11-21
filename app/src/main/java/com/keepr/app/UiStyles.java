package com.keepr.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.Button;
import android.widget.TextView;

/** Shared, accessible native controls for Keepr's screens. */
final class UiStyles {
    static final int INK = 0xffF7F3F4, MUTED = 0xffA7A0A3, ACCENT = 0xffF04444;
    static final int ACCENT_TEXT = 0xffFF98A5, SURFACE = 0xff131316;
    static final int SOFT = 0xff3A171D, PEACH = 0xff493139;
    static final int PAPER = 0xff09090B, LINE = 0xff302C2E;
    private UiStyles() { }
    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
    static GradientDrawable surface(Context context, int fill, int radius, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill); shape.setCornerRadius(dp(context, radius));
        if (stroke != 0) shape.setStroke(dp(context, 1), stroke);
        return shape;
    }
    static Button button(Context context, String label, boolean primary) {
        Button button = new Button(context); button.setText(label); button.setAllCaps(false);
        button.setTextSize(14); button.setTypeface(context.getResources().getFont(com.keepr.app.R.font.inter));
        button.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},
                new int[]{primary ? 0xffC2A8AE : 0xff958990, primary ? 0xffffffff : ACCENT_TEXT}));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FF98A5),
                surface(context, primary ? ACCENT : SURFACE, 14, primary ? 0 : LINE), surface(context, 0xffffffff, 18, 0)));
        button.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10));
        button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(dp(context, 48)); button.setMinimumHeight(dp(context, 48));
        button.setStateListAnimator(null); return button;
    }
    static void styleEditor(android.app.AlertDialog dialog) {
        android.view.Window window=dialog.getWindow();
        if(window!=null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setBackgroundDrawable(surface(dialog.getContext(),PAPER,24,LINE));
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        Button save=dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
        save.setTextColor(0xffffffff); save.setBackground(surface(dialog.getContext(),ACCENT,14,0));
        save.setPadding(dp(dialog.getContext(),24),dp(dialog.getContext(),10),dp(dialog.getContext(),24),dp(dialog.getContext(),10));
    }
    static TextView text(Context context, String value, int size, int color) {
        TextView text = new TextView(context); text.setText(value); text.setTextSize(size); text.setTextColor(color);
        text.setTypeface(context.getResources().getFont(com.keepr.app.R.font.inter)); text.setIncludeFontPadding(false); text.setLineSpacing(dp(context, 3), 1); text.setPadding(0, dp(context, 6), 0, dp(context, 6));
        if (size >= 20) text.setTypeface(context.getResources().getFont(com.keepr.app.R.font.funnel_sans));
        return text;
    }
}
