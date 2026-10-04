package com.hatkid.mkxpz;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

/** Shared spacing, surfaces and touch targets for the launcher only. */
final class LauncherUi {
    static final int BACKGROUND = Color.rgb(27, 27, 27);
    static final int SURFACE = Color.rgb(38, 38, 38);
    static final int INK = Color.rgb(210, 210, 210);
    static final int MUTED = Color.rgb(150, 150, 150);
    static final int ACCENT = Color.rgb(192, 222, 255);
    static final int BORDER = Color.rgb(60, 60, 60);
    static final int CORNER = 4;
    private final Context context;

    LauncherUi(Context context) { this.context = context; }
    int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    GradientDrawable shape(int color, int radius, boolean border) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        if (border) shape.setStroke(dp(1), BORDER);
        return shape;
    }

    StateListDrawable interactive(int color, int radius, boolean border) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled}, shape(BACKGROUND, radius, border));
        states.addState(new int[]{android.R.attr.state_pressed}, shape(Color.rgb(70, 70, 70), radius, true));
        GradientDrawable focus = shape(Color.rgb(60, 60, 60), radius, true);
        focus.setStroke(dp(1), ACCENT);
        states.addState(new int[]{android.R.attr.state_focused}, focus);
        states.addState(new int[]{android.R.attr.state_hovered}, shape(Color.rgb(60, 60, 60), radius, true));
        states.addState(new int[0], shape(color, radius, border));
        return states;
    }

    void input(EditText view, boolean path) {
        view.setTextColor(INK);
        view.setHintTextColor(MUTED);
        view.setTextSize(14);
        view.setTypeface(Typeface.create(path ? "monospace" : "sans-serif", Typeface.NORMAL));
        view.setMinimumHeight(dp(48));
        view.setPadding(dp(8), dp(8), dp(8), dp(8));
        StateListDrawable states = new StateListDrawable();
        GradientDrawable focus = shape(Color.rgb(12, 12, 12), CORNER, true);
        focus.setStroke(dp(1), ACCENT);
        states.addState(new int[]{android.R.attr.state_focused}, focus);
        states.addState(new int[0], shape(Color.rgb(12, 12, 12), CORNER, true));
        view.setBackground(states);
        view.setBackgroundTintList(null);
    }

    TextView label(String text, int size, int color, boolean medium) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        return view;
    }

    Button action(String title, boolean primary) {
        Button button = new Button(context);
        button.setText(title);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(INK);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(dp(48)); button.setMinimumHeight(dp(48));
        button.setPadding(dp(10), dp(8), dp(10), dp(8));
        button.setBackground(interactive(primary ? Color.rgb(50, 50, 50) : Color.rgb(40, 40, 40), CORNER, true));
        button.setBackgroundTintList(null);
        button.setStateListAnimator(null);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(6);
        button.setLayoutParams(params);
        return button;
    }

    ImageButton icon(int resource, String description) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(resource);
        button.setImageTintList(ColorStateList.valueOf(INK));
        button.setContentDescription(description);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setMinimumWidth(dp(48)); button.setMinimumHeight(dp(48));
        button.setBackground(interactive(Color.TRANSPARENT, CORNER, false));
        button.setBackgroundTintList(null);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        return button;
    }

    void styleToggle(CheckBox view) {
        view.setTextSize(14);
        view.setTextColor(INK);
        view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        view.setMinimumHeight(dp(48));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setButtonDrawable(R.drawable.launcher_checkbox);
        view.setButtonTintList(null);
        view.setCompoundDrawablePadding(dp(8));
        view.setBackground(interactive(SURFACE, CORNER, false));
        view.setPadding(dp(8), dp(6), dp(8), dp(6));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(3);
        view.setLayoutParams(params);
    }
}
