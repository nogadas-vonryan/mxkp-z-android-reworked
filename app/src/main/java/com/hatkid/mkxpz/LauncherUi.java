package com.hatkid.mkxpz;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Shared spacing, surfaces and touch targets for the launcher only. */
final class LauncherUi {
    static final int BACKGROUND = Color.rgb(244, 247, 245);
    static final int SURFACE = Color.WHITE;
    static final int INK = Color.rgb(26, 43, 35);
    static final int MUTED = Color.rgb(99, 116, 107);
    static final int ACCENT = Color.rgb(23, 107, 85);
    static final int SOFT = Color.rgb(226, 240, 233);
    static final int BORDER = Color.rgb(221, 230, 224);
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

    RippleDrawable ripple(int color, int radius, boolean border) {
        return new RippleDrawable(ColorStateList.valueOf(0x22176B55), shape(color, radius, border), shape(Color.WHITE, radius, false));
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
        button.setTextSize(15);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(primary ? Color.WHITE : ACCENT);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(dp(56)); button.setMinimumHeight(dp(56));
        button.setPadding(dp(18), dp(12), dp(18), dp(12));
        button.setBackground(ripple(primary ? ACCENT : SURFACE, 16, !primary));
        button.setBackgroundTintList(null);
        button.setStateListAnimator(null);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12);
        button.setLayoutParams(params);
        return button;
    }

    ImageButton icon(int resource, String description) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(resource);
        button.setImageTintList(ColorStateList.valueOf(ACCENT));
        button.setContentDescription(description);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setMinimumWidth(dp(48)); button.setMinimumHeight(dp(48));
        button.setBackground(ripple(Color.TRANSPARENT, 14, false));
        button.setBackgroundTintList(null);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        return button;
    }

    void styleSwitch(Switch view) {
        view.setTextSize(16);
        view.setTextColor(INK);
        view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        view.setMinimumHeight(dp(56));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setSwitchPadding(dp(16));
        view.setPadding(dp(16), dp(12), dp(16), dp(12));
        view.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[0]},
                new int[]{ACCENT, Color.rgb(137, 151, 143)}));
        view.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[0]},
                new int[]{Color.rgb(163, 210, 190), BORDER}));
        view.setBackground(ripple(SURFACE, 16, true));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        view.setLayoutParams(params);
    }
}
