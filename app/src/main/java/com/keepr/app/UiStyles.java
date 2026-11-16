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
    static final int INK = 0xffF3EDE8, MUTED = 0xffB9AEB4, ACCENT = 0xffB83F4F;
    static final int ACCENT_TEXT = 0xffFF98A5, SURFACE = 0xff242126;
    static final int SOFT = 0xff38262D, PEACH = 0xff493139;
    static final int PAPER = 0xff171518, LINE = 0xff443A41;
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
        button.setTextSize(14); button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},
                new int[]{primary ? 0xffC2A8AE : 0xff958990, primary ? 0xffffffff : ACCENT_TEXT}));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FF98A5),
                surface(context, primary ? ACCENT : SOFT, 18, 0), surface(context, 0xffffffff, 18, 0)));
        button.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10));
        button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(dp(context, 48)); button.setMinimumHeight(dp(context, 48));
        button.setStateListAnimator(null); return button;
    }
    static TextView text(Context context, String value, int size, int color) {
        TextView text = new TextView(context); text.setText(value); text.setTextSize(size); text.setTextColor(color);
        text.setLineSpacing(dp(context, 3), 1); text.setPadding(0, dp(context, 6), 0, dp(context, 6));
        if (size >= 20) text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return text;
    }
}
