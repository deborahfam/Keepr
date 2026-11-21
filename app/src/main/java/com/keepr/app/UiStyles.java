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
    static android.widget.LinearLayout header(android.app.Activity activity, String title, Runnable backAction) {
        android.widget.LinearLayout row=new android.widget.LinearLayout(activity);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        android.widget.ImageButton back=new android.widget.ImageButton(activity);
        back.setImageResource(R.drawable.ic_back); back.setContentDescription(activity.getString(R.string.back_to_collection));
        back.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),surface(activity,SURFACE,14,LINE),null));
        back.setPadding(dp(activity,12),dp(activity,12),dp(activity,12),dp(activity,12));
        back.setOnClickListener(v->backAction.run());
        row.addView(back,new android.widget.LinearLayout.LayoutParams(dp(activity,48),dp(activity,48)));
        TextView heading=text(activity,title,26,INK); heading.setPadding(0,0,0,0);
        android.widget.LinearLayout.LayoutParams label=new android.widget.LinearLayout.LayoutParams(0,-2,1); label.leftMargin=dp(activity,14);
        row.addView(heading,label); return row;
    }
    static android.widget.LinearLayout menuRow(Context context,String title,String description,int icon,boolean highlighted,Runnable action) {
        android.widget.LinearLayout row=new android.widget.LinearLayout(context);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL); row.setPadding(dp(context,14),dp(context,13),dp(context,14),dp(context,13));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF),surface(context,highlighted?SOFT:SURFACE,highlighted?14:12,highlighted?0xff61242E:0),null));
        android.widget.ImageView symbol=new android.widget.ImageView(context); symbol.setImageResource(icon);
        symbol.setPadding(dp(context,8),dp(context,8),dp(context,8),dp(context,8));
        symbol.setBackground(surface(context,highlighted?ACCENT:0xff1B1B1F,10,0));
        row.addView(symbol,new android.widget.LinearLayout.LayoutParams(dp(context,highlighted?38:34),dp(context,highlighted?38:34)));
        android.widget.LinearLayout copy=new android.widget.LinearLayout(context); copy.setOrientation(android.widget.LinearLayout.VERTICAL);
        TextView name=text(context,title,14,INK); name.setPadding(0,0,0,dp(context,2)); copy.addView(name);
        TextView hint=text(context,description,11,highlighted?0xffDFA5AC:MUTED); hint.setPadding(0,0,0,0); copy.addView(hint);
        android.widget.LinearLayout.LayoutParams details=new android.widget.LinearLayout.LayoutParams(0,-2,1); details.leftMargin=dp(context,12); details.rightMargin=dp(context,12); row.addView(copy,details);
        android.widget.ImageView chevron=new android.widget.ImageView(context); chevron.setImageResource(R.drawable.ic_back); chevron.setRotation(180); chevron.setImageTintList(ColorStateList.valueOf(0xff706A6D)); row.addView(chevron,new android.widget.LinearLayout.LayoutParams(dp(context,18),dp(context,18)));
        row.setMinimumHeight(dp(context,60)); row.setFocusable(true); row.setOnClickListener(v->action.run()); return row;
    }
    static void field(android.widget.EditText field) {
        Context context=field.getContext();
        field.setTextSize(14); field.setTextColor(INK); field.setHintTextColor(MUTED);
        field.setTypeface(context.getResources().getFont(R.font.inter));
        field.setBackground(surface(context,SURFACE,14,LINE));
        field.setPadding(dp(context,14),dp(context,12),dp(context,14),dp(context,12));
        field.setMinimumHeight(dp(context,48));
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
        if(save==null) return;
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
