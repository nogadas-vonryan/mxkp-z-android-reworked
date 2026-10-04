package com.hatkid.mkxpz;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The app opens to a game library. Settings are an optional per-game destination. */
public class SettingsActivity extends Activity {
    private LinearLayout content;
    private GameLibrary library;
    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private final Map<String, CheckBox> display = new LinkedHashMap<>();
    private final List<ScriptRow> scripts = new ArrayList<>();
    private CheckBox trace;
    private File editing;
    private String screen = "library";
    private int generation;
    private boolean permissionPending;
    private boolean launching;
    private JSONObject draft;
    private LauncherUi ui;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new LauncherUi(this);
        if (hasStorageAccess() && state != null && state.containsKey("editing")) {
            try {
                draft = new JSONObject(state.getString("draft", "{}"));
                showSettings(new File(state.getString("editing")));
                return;
            } catch (Exception e) { error(e); }
        }
        showLibrary();
    }

    @Override protected void onResume() {
        super.onResume();
        if (permissionPending) {
            permissionPending = false;
            showLibrary();
        }
    }

    @Override protected void onDestroy() {
        generation++;
        work.shutdownNow();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (!screen.equals("library")) {
            if (screen.equals("settings")) {
                new AlertDialog.Builder(this).setTitle("Leave game settings?")
                        .setMessage("Unsaved changes will be discarded.")
                        .setNegativeButton("Keep editing", null)
                        .setPositiveButton("Leave", (d, w) -> showLibrary()).show();
            } else showLibrary();
        } else super.onBackPressed();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (screen.equals("settings") && editing != null && trace != null) {
            try {
                state.putString("editing", editing.getAbsolutePath());
                state.putString("draft", currentProfile().toString());
            } catch (Exception e) { Diagnostics.recordLauncherError(this, e); }
        }
    }

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStorageAccess() {
        permissionPending = true;
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
        permissionPending = false;
        showLibrary();
    }

    private void page(String title, String destination) {
        generation++;
        screen = destination;
        display.clear();
        scripts.clear();
        trace = null;
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(LauncherUi.BACKGROUND);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(12);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);
        setContentView(scroll);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        if (!destination.equals("library")) {
            ImageButton back = ui.icon(R.drawable.launcher_back, "Back to games");
            back.setOnClickListener(v -> onBackPressed());
            bar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
        TextView heading = ui.label(title, 20, LauncherUi.INK, true);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setSingleLine(true);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.setPadding(dp(8), 0, dp(8), 0);
        bar.addView(heading, new LinearLayout.LayoutParams(0, dp(56), 1));
        if (destination.equals("library")) {
            ImageButton menu = ui.icon(R.drawable.launcher_more, "Library options");
            menu.setOnClickListener(v -> libraryMenu(menu));
            bar.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
        content.addView(bar);
        LinearLayout.LayoutParams barParams = (LinearLayout.LayoutParams) bar.getLayoutParams();
        barParams.bottomMargin = dp(8);
        getWindow().setNavigationBarColor(LauncherUi.BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(0);
    }

    private void showLibrary() {
        editing = null;
        draft = null;
        launching = false;
        page("Games", "library");
        if (!hasStorageAccess()) {
            text("Allow storage access to find and play games on your device.");
            button("Allow storage access", this::requestStorageAccess);
            return;
        }
        TextView status = text("Loading games…");
        int token = generation;
        work.execute(() -> {
            try {
                GameLibrary.initialize(this);
                GameLibrary next = new GameLibrary(this);
                List<File> games = next.games();
                runOnUiThread(() -> {
                    if (!active(token)) return;
                    library = next;
                    content.removeView(status);
                    if (engineRunning()) button("Continue playing", () -> {
                        if (engineRunning()) startActivity(new Intent(this, MainActivity.class));
                        else showLibrary();
                    });
                    if (hasObb()) button("Play packaged game", () -> {
                        if (!launching) {
                            launching = true;
                            launchWhenStopped(null, android.os.SystemClock.elapsedRealtime() + 10000, generation);
                        }
                    });
                    if (games.isEmpty()) {
                        text("No games yet. Put game folders in " + library.gamesDirectory() + ", or add a folder from elsewhere.");
                    }
                    for (File game : games) gameRow(game);
                    button(games.isEmpty() ? "Add your first game" : "Add game", () -> browseFolder(false), true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!active(token)) return;
                    status.setText("Could not load the game library.");
                    button("Try again", this::showLibrary);
                    error(e);
                });
            }
        });
    }

    private boolean active(int token) { return token == generation && !isFinishing() && !isDestroyed(); }
    private boolean hasObb() { return new File(getObbDir(), "main.1." + getPackageName() + ".obb").isFile(); }

    private void libraryMenu(android.view.View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Add game").setEnabled(library != null && hasStorageAccess());
        menu.getMenu().add("Games folder").setEnabled(library != null && hasStorageAccess());
        menu.getMenu().add("Refresh games");
        menu.getMenu().add("Diagnostics");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getTitle().toString()) {
                case "Add game": browseFolder(false); break;
                case "Games folder": browseFolder(true); break;
                case "Refresh games": showLibrary(); break;
                case "Diagnostics": showDiagnostics(); break;
            }
            return true;
        });
        menu.show();
    }

    private void gameRow(File game) {
        String name = library.displayName(game);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(ui.shape(LauncherUi.SURFACE, LauncherUi.CORNER, true));
        row.setClipToOutline(true);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(6);
        row.setLayoutParams(cardParams);
        LinearLayout play = new LinearLayout(this);
        play.setGravity(Gravity.CENTER_VERTICAL);
        play.setPadding(dp(10), dp(8), dp(8), dp(8));
        play.setMinimumHeight(dp(64));
        play.setBackground(ui.interactive(android.graphics.Color.TRANSPARENT, LauncherUi.CORNER, false));
        play.setFocusable(true);
        play.setContentDescription("Play " + name);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = ui.label(name, 15, LauncherUi.INK, true);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        copy.addView(title);
        TextView path = ui.label(game.getAbsolutePath(), 12, LauncherUi.MUTED, false);
        path.setTypeface(android.graphics.Typeface.MONOSPACE);
        path.setSingleLine(true);
        path.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        path.setPadding(0, dp(4), 0, 0);
        path.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        copy.addView(path);
        play.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        play.setOnClickListener(v -> launch(game));
        row.addView(play, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton more = ui.icon(R.drawable.launcher_more, "Options for " + name);
        more.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, more);
            menu.getMenu().add("Game settings");
            menu.getMenu().add("Rename");
            menu.getMenu().add("Show folder");
            menu.getMenu().add("Remove from library");
            menu.setOnMenuItemClickListener(item -> {
                switch (item.getTitle().toString()) {
                    case "Game settings": showSettings(game); break;
                    case "Rename": renameGame(game); break;
                    case "Show folder": new AlertDialog.Builder(this).setTitle(name)
                            .setMessage(game.getAbsolutePath()).setPositiveButton("OK", null).show(); break;
                    case "Remove from library": new AlertDialog.Builder(this).setTitle("Remove from library?")
                            .setMessage("The game files and saves will stay on your device.")
                            .setNegativeButton("Cancel", null).setPositiveButton("Remove", (d, w) -> {
                                try { library.remove(game); showLibrary(); } catch (Exception e) { error(e); }
                            }).show(); break;
                }
                return true;
            });
            menu.show();
        });
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        moreParams.rightMargin = dp(8);
        row.addView(more, moreParams);
        content.addView(row);
    }

    private void renameGame(File game) {
        EditText input = new EditText(this);
        ui.input(input, false);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(library.displayName(game));
        input.selectAll();
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(input, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Rename game").setView(box)
                .setNegativeButton("Cancel", null).setNeutralButton("Use original", null)
                .setPositiveButton("Save name", null).create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try { library.rename(game, input.getText().toString()); dialog.dismiss(); showLibrary(); }
                catch (Exception e) { error(e); }
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                try { library.rename(game, ""); dialog.dismiss(); showLibrary(); }
                catch (Exception e) { error(e); }
            });
        });
        dialog.show();
    }

    private void browseFolder(boolean collection) {
        File initial = collection ? library.gamesDirectory() : Environment.getExternalStorageDirectory();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), 0, dp(16), 0);
        EditText path = new EditText(this);
        ui.input(path, true);
        path.setSingleLine(true);
        path.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        path.setText(initial.getAbsolutePath());
        box.addView(path);
        Button go = ui.action("Go to path", false);
        LinearLayout.LayoutParams goParams = new LinearLayout.LayoutParams(-1, -2);
        goParams.topMargin = dp(12);
        box.addView(go, goParams);
        ListView list = new ListView(this);
        int listHeight = Math.max(dp(96), Math.min(dp(280), getResources().getDisplayMetrics().heightPixels - dp(300)));
        box.addView(list, new LinearLayout.LayoutParams(-1, listHeight));
        List<File> children = new ArrayList<>();
        Runnable refresh = () -> {
            try {
                File folder = new File(path.getText().toString().trim()).getCanonicalFile();
                if (!folder.isDirectory()) throw new java.io.IOException("This folder does not exist.");
                File[] directories = folder.listFiles(File::isDirectory);
                if (directories == null) throw new java.io.IOException("Cannot read this folder. Check storage access.");
                Arrays.sort(directories, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
                path.setText(folder.getAbsolutePath());
                children.clear();
                children.addAll(Arrays.asList(directories));
                List<String> names = new ArrayList<>();
                for (File child : children) names.add(child.getName());
                list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
            } catch (Exception e) { error(e); }
        };
        go.setOnClickListener(v -> refresh.run());
        list.setOnItemClickListener((parent, view, position, id) -> {
            path.setText(children.get(position).getAbsolutePath()); refresh.run();
        });
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(collection ? "Choose games folder" : "Choose game folder")
                .setView(box).setNegativeButton("Cancel", null)
                .setPositiveButton("Use this folder", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                File folder = new File(path.getText().toString().trim()).getCanonicalFile();
                if (!folder.isDirectory() || !folder.canRead()) throw new java.io.IOException("Choose a readable folder.");
                if (collection) library.setGamesDirectory(folder); else library.add(folder);
                dialog.dismiss(); showLibrary();
            } catch (Exception e) { error(e); }
        }));
        refresh.run();
        dialog.show();
    }

    private void showSettings(File game) {
        editing = game;
        try { if (library == null) library = new GameLibrary(this); }
        catch (Exception e) { page(GameLibrary.title(game), "settings"); error(e); return; }
        page(library.displayName(game), "settings");
        try {
            JSONObject base = LaunchSession.baseOptions(game);
            JSONObject profile = draft != null ? draft : library.profile(game);
            draft = null;
            JSONObject overrides = profile.optJSONObject("display");
            heading("Display");
            toggle("fullscreen", "Fullscreen", true, base, overrides);
            toggle("fixedAspectRatio", "Preserve aspect ratio", true, base, overrides);
            toggle("integerScalingActive", "Integer scaling", false, base, overrides);
            toggle("subImageFix", "Texture workaround", false, base, overrides);
            heading("Preload scripts");
            JSONArray enabled = profile.optJSONArray("scripts");
            if (enabled == null) enabled = LaunchSession.preloads(base);
            Set<String> checked = new LinkedHashSet<>();
            for (int i = 0; i < enabled.length(); i++) checked.add(LaunchSession.resolveScript(game, enabled.getString(i)));
            LinearLayout rows = new LinearLayout(this);
            rows.setOrientation(LinearLayout.VERTICAL);
            content.addView(rows);
            for (String path : LaunchSession.scriptChoices(game, base, profile)) scripts.add(new ScriptRow(path, checked.contains(path)));
            renderScripts(rows);
            Button refreshScripts = button("Refresh scripts", () -> {
                try {
                    Set<String> known = new LinkedHashSet<>();
                    for (ScriptRow row : scripts) known.add(row.path);
                    int added = 0;
                    for (String path : LaunchSession.scriptChoices(game, base, currentProfile())) {
                        if (known.add(path)) { scripts.add(new ScriptRow(path, false)); added++; }
                    }
                    renderScripts(rows);
                    toast(added == 0 ? "Scripts are up to date" : added + " new script" + (added == 1 ? "" : "s") + " found");
                } catch (Exception e) { error(e); }
            });
            Button addScript = button("Add script path", () -> {
                EditText path = new EditText(this);
                ui.input(path, true);
                path.setHint("/storage/emulated/0/mkxp-z/scripts/custom.rb");
                new AlertDialog.Builder(this).setTitle("Add preload script").setView(path)
                        .setNegativeButton("Cancel", null).setPositiveButton("Add", (d, w) -> {
                            try {
                                String resolved = LaunchSession.resolveScript(game, path.getText().toString().trim());
                                if (!new File(resolved).isFile() || !resolved.toLowerCase(Locale.ROOT).endsWith(".rb")) throw new java.io.IOException("Choose an existing .rb file.");
                                for (ScriptRow row : scripts) if (row.path.equals(resolved)) { row.enabled = true; renderScripts(rows); return; }
                                scripts.add(new ScriptRow(resolved, true)); renderScripts(rows);
                            } catch (Exception e) { error(e); }
                        }).show();
            });
            pairButtons(refreshScripts, addScript);
            heading("Diagnostics");
            trace = new CheckBox(this);
            ui.styleToggle(trace);
            trace.setText("Record Ruby exception backtraces");
            trace.setChecked(profile.optBoolean("traceExceptions", true));
            content.addView(trace);
            Button saveSettings = button("Save settings", () -> {
                try { library.saveProfile(game, currentProfile()); showLibrary(); toast("Game settings saved"); }
                catch (Exception e) { error(e); }
            }, true);
            Button resetSettings = button("Reset game settings", () -> new AlertDialog.Builder(this).setTitle("Reset this game?")
                    .setMessage("Return to the original config's settings and preload selections.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Reset", (d, w) -> {
                        try { library.saveProfile(game, new JSONObject()); showSettings(game); }
                        catch (Exception e) { error(e); }
                    }).show());
            pairButtons(saveSettings, resetSettings);
        } catch (Exception e) { text("Could not read game settings."); error(e); }
    }

    private JSONObject currentProfile() throws Exception {
        JSONObject profile = new JSONObject();
        JSONObject values = new JSONObject();
        for (Map.Entry<String, CheckBox> entry : display.entrySet()) values.put(entry.getKey(), entry.getValue().isChecked());
        profile.put("display", values);
        JSONArray selected = new JSONArray();
        for (ScriptRow row : scripts) if (row.enabled) selected.put(row.path);
        profile.put("scripts", selected);
        profile.put("traceExceptions", trace.isChecked());
        return profile;
    }

    private void toggle(String key, String title, boolean fallback, JSONObject base, JSONObject overrides) {
        CheckBox view = new CheckBox(this);
        ui.styleToggle(view);
        view.setText(title);
        view.setChecked(overrides == null ? base.optBoolean(key, fallback) : overrides.optBoolean(key, base.optBoolean(key, fallback)));
        display.put(key, view);
        content.addView(view);
    }

    private void renderScripts(LinearLayout parent) {
        parent.removeAllViews();
        for (int i = 0; i < scripts.size(); i++) {
            ScriptRow script = scripts.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(ui.shape(LauncherUi.SURFACE, LauncherUi.CORNER, true));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
            cardParams.bottomMargin = dp(6);
            card.setLayoutParams(cardParams);
            CheckBox toggle = new CheckBox(this);
            ui.styleToggle(toggle);
            toggle.setBackground(ui.interactive(android.graphics.Color.TRANSPARENT, LauncherUi.CORNER, false));
            toggle.setPadding(dp(8), dp(6), dp(8), dp(6));
            toggle.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            File file = new File(script.path);
            toggle.setText(file.getName() + (file.isFile() ? "" : " (missing)"));
            toggle.setMaxLines(2);
            toggle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            toggle.setChecked(script.enabled);
            toggle.setOnCheckedChangeListener((v, checked) -> script.enabled = checked);
            card.addView(toggle);
            TextView description = ui.label(scriptDescription(file.getName()), 14, LauncherUi.MUTED, false);
            description.setPadding(dp(8), 0, dp(8), dp(3));
            card.addView(description);
            LinearLayout footer = new LinearLayout(this);
            footer.setGravity(Gravity.CENTER_VERTICAL);
            footer.setPadding(dp(4), 0, dp(4), dp(3));
            String source = "Custom path";
            try {
                File folder = file.getParentFile().getCanonicalFile();
                if (folder.equals(GameLibrary.child(editing, "scripts").getCanonicalFile())) source = "Game scripts";
                else if (folder.equals(new File(StartupConfig.directory(), "scripts").getCanonicalFile())) source = "Shared scripts";
            } catch (java.io.IOException ignored) {}
            TextView location = ui.label(source + " · view path", 12, LauncherUi.ACCENT, true);
            location.setPadding(dp(8), 0, 0, 0);
            location.setGravity(Gravity.CENTER_VERTICAL);
            location.setMinimumHeight(dp(48));
            location.setBackground(ui.interactive(android.graphics.Color.TRANSPARENT, LauncherUi.CORNER, false));
            location.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(file.getName())
                    .setMessage(script.path).setPositiveButton("OK", null).show());
            footer.addView(location, new LinearLayout.LayoutParams(0, -2, 1));
            final int position = i;
            for (int direction : new int[]{-1, 1}) {
                ImageButton move = ui.icon(direction < 0 ? R.drawable.launcher_up : R.drawable.launcher_down,
                        (direction < 0 ? "Move earlier: " : "Move later: ") + file.getName());
                move.setEnabled(position + direction >= 0 && position + direction < scripts.size());
                move.setAlpha(move.isEnabled() ? 1f : 0.3f);
                move.setOnClickListener(v -> { Collections.swap(scripts, position, position + direction); renderScripts(parent); });
                footer.addView(move, new LinearLayout.LayoutParams(dp(48), dp(48)));
            }
            card.addView(footer);
            parent.addView(card);
        }
    }

    private String scriptDescription(String name) {
        switch (name) {
            case "load-zlib.rb": return "Load Ruby compression support for Pokémon Essentials plugins.";
            case "fix-essentials-clock.rb": return "Correct microsecond uptime in this port for Essentials v21.";
            case "disable-steam.rb": return "Skip Steam integration; achievements are unavailable.";
            case "disable-audio.rb": return "Run silently and skip loading audio files.";
            default: return "Custom Ruby preload script.";
        }
    }

    private boolean engineRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
        if (processes != null) for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (process.processName.equals(getPackageName() + ":engine")) return true;
        }
        return false;
    }

    private void launch(File game) {
        if (launching) return;
        if (hasObb()) {
            error(new Exception("An OBB package is installed and overrides folder-based startup. Remove that package to launch a folder game; use Play packaged game to launch the OBB."));
            return;
        }
        launching = true;
        launchWhenStopped(game, android.os.SystemClock.elapsedRealtime() + 10000, generation);
    }

    private void launchWhenStopped(File game, long deadline, int token) {
        if (!active(token)) { launching = false; return; }
        if (engineRunning()) {
            if (android.os.SystemClock.elapsedRealtime() >= deadline) {
                launching = false;
                error(new Exception("A game is already running. Return to it and close it before starting another game."));
            } else new android.os.Handler(getMainLooper()).postDelayed(() -> {
                if (active(token)) launchWhenStopped(game, deadline, token);
            }, 250);
            return;
        }
        work.execute(() -> {
            try {
                LaunchSession session = game == null ? null : new LaunchSession(this, game, library);
                runOnUiThread(() -> {
                    launching = false;
                    if (!active(token)) return;
                    Intent intent = new Intent(this, MainActivity.class);
                    if (session != null) intent.putExtra("sessionDirectory", session.directory.getAbsolutePath());
                    startActivity(intent);
                });
            } catch (Exception e) {
                runOnUiThread(() -> { launching = false; if (active(token)) error(e); });
            }
        });
    }

    private void showDiagnostics() {
        page("Diagnostics", "diagnostics");
        button("Export report", () -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, "mkxp-z-diagnostics.txt");
            startActivityForResult(intent, 120);
        });
        button("Refresh report", this::showDiagnostics);
        TextView preview = text("Reading report…");
        preview.setTextSize(12);
        preview.setTypeface(android.graphics.Typeface.MONOSPACE);
        int token = generation;
        work.execute(() -> {
            try {
                String report = Diagnostics.report(this);
                String shown = report.length() > 32000 ? report.substring(0, 32000) + "\n[Preview shortened. Export to read the complete report.]" : report;
                runOnUiThread(() -> { if (active(token)) preview.setText(shown); });
            } catch (Exception e) { runOnUiThread(() -> { if (active(token)) error(e); }); }
        });
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 120 || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri destination = data.getData();
        work.execute(() -> {
            try (java.io.OutputStream stream = getContentResolver().openOutputStream(destination, "wt")) {
                if (stream == null) throw new java.io.IOException("Cannot write the report.");
                stream.write(Diagnostics.report(this).getBytes(StandardCharsets.UTF_8));
                runOnUiThread(() -> { if (!isDestroyed()) toast("Report exported"); });
            } catch (Exception e) { runOnUiThread(() -> { if (!isDestroyed()) error(e); }); }
        });
    }

    private static final class ScriptRow {
        final String path;
        boolean enabled;
        ScriptRow(String path, boolean enabled) { this.path = path; this.enabled = enabled; }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void heading(String title) {
        TextView view = ui.label(title, 15, LauncherUi.INK, true);
        view.setPadding(0, dp(16), 0, dp(6));
        content.addView(view);
    }
    private TextView text(String value) {
        TextView view = ui.label(value, 14, LauncherUi.MUTED, false);
        view.setLineSpacing(dp(1), 1f);
        view.setTextIsSelectable(true); view.setPadding(0, dp(3), 0, dp(6)); content.addView(view); return view;
    }
    private Button button(String title, Runnable action) {
        return button(title, action, false);
    }
    private Button button(String title, Runnable action, boolean primary) {
        Button view = ui.action(title, primary); content.addView(view);
        view.setOnClickListener(v -> action.run()); return view;
    }
    private void pairButtons(Button left, Button right) {
        content.removeView(left);
        content.removeView(right);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, -1, 1);
        leftParams.rightMargin = dp(6);
        row.addView(left, leftParams);
        row.addView(right, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = dp(6);
        content.addView(row, rowParams);
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    private void error(Exception e) {
        Diagnostics.recordLauncherError(this, e);
        new AlertDialog.Builder(this).setTitle("Could not complete this action")
                .setMessage(e.getMessage()).setPositiveButton("OK", null).show();
    }
}
