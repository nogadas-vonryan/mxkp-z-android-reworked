package com.hatkid.mkxpz;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.*;
import org.json.JSONArray;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small startup settings screen; no native engine or Ruby VM is loaded here. */
public class SettingsActivity extends Activity {
    private StartupConfig config;
    private LinearLayout content;
    private final Map<String, Switch> switches = new LinkedHashMap<>();
    private boolean loaded;
    private boolean obb;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        showScreen();
        if (state != null) {
            for (Map.Entry<String, Switch> entry : switches.entrySet()) {
                if (state.containsKey(entry.getKey())) entry.getValue().setChecked(state.getBoolean(entry.getKey()));
            }
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        for (Map.Entry<String, Switch> entry : switches.entrySet()) state.putBoolean(entry.getKey(), entry.getValue().isChecked());
    }

    @Override protected void onResume() {
        super.onResume();
        if (!loaded) showScreen();
    }

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (android.content.ActivityNotFoundException e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE}, 110);
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        showScreen();
    }

    private void showScreen() {
        loaded = false;
        switches.clear();
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);
        setContentView(scroll);
        heading("Game settings");
        text("Adjust startup settings, then start a fresh game session.");
        if (!hasStorageAccess()) {
            text("Storage access lets mkxp-z read your game and save its configuration in internal storage.");
            button("Allow storage access", this::requestStorageAccess);
            return;
        }
        config = new StartupConfig();
        heading("Active configuration");
        text(config.file.getAbsolutePath());
        obb = new File(getObbDir(), "main.1." + getPackageName() + ".obb").exists();
        if (obb) {
            text("An OBB game package is installed. Gameplay reads its mounted configuration instead of this root file. Root settings editing is unavailable for this package.");
            button("Start packaged game", this::launch);
            loaded = true;
            return;
        }
        try {
            config.load();
            if (!config.file.exists()) text("No config yet. Saving creates this file; existing game files stay in place.");
            String folder = config.options.optString("gameFolder", "");
            File game = folder.isEmpty() ? StartupConfig.directory() : new File(folder);
            if (!game.isAbsolute()) game = new File(StartupConfig.directory(), folder);
            text("Game folder: " + game.getCanonicalPath());
            text("The selected game's own mkxp.json is not automatically imported. Save-directory overrides, if present, can override these startup settings.");
            heading("Display");
            toggle("fullscreen", "Fullscreen", "Use the available screen area.", false);
            toggle("fixedAspectRatio", "Preserve aspect ratio", "Keep the game's proportions; unused space may appear at the edges.", true);
            toggle("integerScalingActive", "Integer scaling", "Use whole-number scale steps for pixels; the game may appear smaller.", false);
            heading("Compatibility");
            toggle("subImageFix", "Texture workaround", "Use the engine's alternative texture-upload path for graphics-driver issues.", false);
            heading("Startup scripts");
            text("Enabled preload scripts, in execution order. This screen preserves the list. Relative paths start from the game folder.");
            Object scripts = config.options.opt("preloadScript");
            if (scripts == null) text("No preload scripts enabled.");
            else if (scripts instanceof String) text("1. " + scripts);
            else if (scripts instanceof JSONArray) {
                JSONArray list = (JSONArray) scripts;
                if (list.length() == 0) text("No preload scripts enabled.");
                int order = 0;
                for (int i = 0; i < list.length(); i++) {
                    if (list.get(i) instanceof String) text((++order) + ". " + list.getString(i));
                }
            } else text("The preloadScript value is unusual; it will be preserved unchanged.");
            text("Saving preserves other options, scripts, and comments. The previous file is kept for restore.");
            button("Save settings", () -> save(false));
            button("Save and start game", () -> save(true));
            button("Restore previous settings", () -> new AlertDialog.Builder(this)
                    .setTitle("Restore previous settings?")
                    .setMessage("Replace the startup config with the copy from before your last settings change. Unsaved edits on this screen will be discarded.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Restore", (dialog, which) -> {
                        try { config.restore(); showScreen(); toast("Previous settings restored"); }
                        catch (Exception e) { error(e); }
                    }).show()).setEnabled(config.previous.isFile());
            button("Reload from file", () -> new AlertDialog.Builder(this)
                    .setTitle("Reload configuration?")
                    .setMessage("Discard unsaved edits and read the file again.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Reload", (dialog, which) -> showScreen()).show());
            loaded = true;
        } catch (Exception e) {
            text("Cannot edit this configuration: " + e.getMessage());
            text("Your file has not been changed. Fix it in a text editor, then reload. You can also let the engine load it directly.");
            button("Reload from file", this::showScreen);
            button("Start with existing config", this::launch);
        }
    }

    private void save(boolean start) {
        try {
            Map<String, Boolean> changes = new LinkedHashMap<>();
            for (Map.Entry<String, Switch> entry : switches.entrySet()) {
                String key = entry.getKey();
                boolean value = entry.getValue().isChecked();
                boolean fallback = key.equals("fixedAspectRatio");
                // Leave absent defaults absent, and retain every unexposed option.
                if (value != config.options.optBoolean(key, fallback)) changes.put(key, value);
            }
            config.save(changes);
            showScreen();
            if (start) launch(); else toast("Settings saved");
        } catch (Exception e) { error(e); }
    }

    private void launch() {
        if (!obb && !StartupConfig.directory().isDirectory()) {
            error(new Exception("Create " + StartupConfig.directory() + " and place your game there before starting."));
            return;
        }
        launchWhenStopped(android.os.SystemClock.elapsedRealtime() + 10000);
    }

    private void launchWhenStopped(long deadline) {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        java.util.List<android.app.ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
        if (processes != null) {
            for (android.app.ActivityManager.RunningAppProcessInfo process : processes) {
                if (process.processName.equals(getPackageName() + ":engine")) {
                    if (android.os.SystemClock.elapsedRealtime() >= deadline) {
                        error(new Exception("The previous game is still closing. Wait a moment, then try starting again."));
                    } else {
                        new android.os.Handler(getMainLooper()).postDelayed(() -> {
                            if (!isFinishing() && !isDestroyed() && hasWindowFocus()) launchWhenStopped(deadline);
                        }, 250);
                    }
                    return;
                }
            }
        }
        if (!isFinishing() && !isDestroyed()) startActivity(new Intent(this, MainActivity.class));
    }

    private void toggle(String key, String title, String description, boolean fallback) {
        Switch view = new Switch(this);
        view.setText(title);
        view.setChecked(config.options.optBoolean(key, fallback));
        view.setPadding(0, 16, 0, 8);
        content.addView(view);
        text(description);
        switches.put(key, view);
    }

    private void heading(String title) {
        TextView view = text(title);
        view.setTextSize(22);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setPadding(0, 24, 0, 12);
    }

    private TextView text(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(16);
        view.setTextIsSelectable(true);
        view.setPadding(0, 4, 0, 8);
        content.addView(view);
        return view;
    }

    private Button button(String title, Runnable action) {
        Button view = new Button(this);
        view.setText(title);
        content.addView(view);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    private void error(Exception e) {
        new AlertDialog.Builder(this).setTitle("Settings unavailable")
                .setMessage(e.getMessage()).setPositiveButton("OK", null).show();
    }
}
