package com.hatkid.mkxpz;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Generates an isolated startup directory for the existing JNI launch contract. */
final class LaunchSession {
    final File directory;
    final JSONObject options;

    LaunchSession(Context context, File game, GameLibrary library) throws Exception {
        if (!GameLibrary.isGame(game)) throw new IOException("No supported RPG Maker game found in " + game);
        options = baseOptions(game);
        JSONObject profile = library.profile(game);
        JSONObject display = profile.optJSONObject("display");
        if (display != null) merge(options, display);
        // Profiles remember an explicit ordered selection, including an empty selection.
        JSONArray chosen = profile.optJSONArray("scripts");
        if (chosen == null) chosen = preloads(options);
        JSONArray enabled = new JSONArray();
        Set<String> seen = new LinkedHashSet<>();
        // Validate user selections before creating a session or changing the report pointer.
        for (int i = 0; i < chosen.length(); i++) {
            String path = resolveScript(game, chosen.getString(i));
            if (!new File(path).isFile()) throw new IOException("Preload script is missing: " + path + "\nOpen this game's settings to disable it or restore the file.");
            seen.add(path);
        }
        directory = new File(new File(context.getFilesDir(), "sessions"),
                System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8));
        if (!directory.mkdirs()) throw new IOException("Cannot create a launch session");
        // This optional observer logs full Ruby raise backtraces before native dialogs truncate them.
        if (profile.optBoolean("traceExceptions", true)) {
            File observer = new File(directory, "exception-trace.rb");
            String template;
            try (java.io.InputStream stream = context.getAssets().open("diagnostics/exception-trace.rb");
                 java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
                template = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            }
            String logPath = new File(directory, "ruby-exceptions.log").getAbsolutePath();
            String literal = "'" + logPath.replace("\\", "\\\\").replace("'", "\\'") + "'";
            StartupConfig.write(observer, template.replace("__LOG_PATH__", literal).getBytes(StandardCharsets.UTF_8));
            enabled.put(observer.getAbsolutePath());
        }
        for (String path : seen) enabled.put(path);
        options.put("gameFolder", game.getCanonicalPath());
        options.put("preloadScript", enabled);
        // Name game-script frames in the existing engine's exception backtraces.
        options.put("useScriptNames", true);
        StartupConfig.write(new File(directory, "mkxp.json"), engineConfigBytes(options));
        JSONObject summary = new JSONObject();
        summary.put("game", game.getCanonicalPath());
        summary.put("title", GameLibrary.title(game));
        summary.put("started", new java.util.Date().toString());
        summary.put("device", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
        summary.put("android", android.os.Build.VERSION.RELEASE);
        summary.put("abis", new JSONArray(Arrays.asList(android.os.Build.SUPPORTED_ABIS)));
        summary.put("appVersion", context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName);
        summary.put("engine", "Bundled mkxp-z (build reports 2.4)");
        summary.put("note", "Save-directory mkxp.json overrides can supersede this launch configuration. Ruby raise events may include exceptions handled normally by the game.");
        StartupConfig.write(new File(directory, "session.json"), summary.toString(2).getBytes(StandardCharsets.UTF_8));
        StartupConfig.write(new File(context.getFilesDir(), "latest-session.txt"), directory.getAbsolutePath().getBytes(StandardCharsets.UTF_8));
        prune(directory);
    }

    static byte[] engineConfigBytes(JSONObject options) throws Exception {
        String json = options.toString(2);
        StringBuilder ascii = new StringBuilder(json.length());
        final String hex = "0123456789abcdef";
        for (int i = 0; i < json.length(); i++) {
            char character = json.charAt(i);
            if (character <= 0x7f) {
                ascii.append(character);
            } else {
                ascii.append('\\').append('u');
                for (int shift = 12; shift >= 0; shift -= 4)
                    ascii.append(hex.charAt((character >> shift) & 0xf));
            }
        }
        return ascii.toString().getBytes(StandardCharsets.US_ASCII);
    }

    static JSONObject baseOptions(File game) throws Exception {
        JSONObject result = new JSONObject();
        String executable = GameLibrary.executableName(game);
        if (executable != null) result.put("execName", executable);
        File root = new File(StartupConfig.directory(), "mkxp.json");
        File own = new File(game, "mkxp.json");
        if (root.isFile()) merge(result, readConfig(root));
        if (own.isFile() && !own.getCanonicalFile().equals(root.getCanonicalFile())) merge(result, readConfig(own));
        if (!result.has("fullscreen")) result.put("fullscreen", true);
        return result;
    }

    private static JSONObject readConfig(File file) throws Exception {
        try { return StartupConfig.parse(StartupConfig.read(file)); }
        catch (Exception e) { throw new IOException("Cannot read " + file + ": " + e.getMessage(), e); }
    }

    private static void merge(JSONObject target, JSONObject source) throws Exception {
        Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            // Preserve individual controller bindings when a game specifies only some keys.
            if (key.equals("bindingNames") && target.optJSONObject(key) != null && source.optJSONObject(key) != null)
                merge(target.getJSONObject(key), source.getJSONObject(key));
            else target.put(key, source.get(key));
        }
    }

    static JSONArray preloads(JSONObject options) throws Exception {
        Object value = options.opt("preloadScript");
        if (value == null) return new JSONArray();
        if (value instanceof String) return new JSONArray().put(value);
        if (!(value instanceof JSONArray)) throw new IOException("preloadScript must be a script path or a list of paths.");
        JSONArray array = (JSONArray) value;
        for (int i = 0; i < array.length(); i++) {
            if (!(array.get(i) instanceof String)) throw new IOException("Every preloadScript entry must be a path.");
        }
        return array;
    }

    static String resolveScript(File game, String path) throws IOException {
        File script = new File(path);
        if (!script.isAbsolute()) {
            // Preserve legacy game-relative preloads; shared scripts are the fallback.
            File local = new File(game, path);
            File shared = new File(StartupConfig.directory(), path);
            script = local.isFile() || !shared.isFile() ? local : shared;
        }
        return script.getCanonicalPath();
    }

    static List<String> scriptChoices(File game, JSONObject options, JSONObject profile) throws Exception {
        Set<String> choices = new LinkedHashSet<>();
        JSONArray enabled = profile.optJSONArray("scripts");
        if (enabled == null) enabled = preloads(options);
        for (int i = 0; i < enabled.length(); i++) choices.add(resolveScript(game, enabled.getString(i)));
        // Keep disabled original entries available too.
        JSONArray originals = preloads(options);
        for (int i = 0; i < originals.length(); i++) choices.add(resolveScript(game, originals.getString(i)));
        discoverScripts(GameLibrary.child(game, "scripts"), choices);
        discoverScripts(new File(StartupConfig.directory(), "scripts"), choices);
        return new ArrayList<>(choices);
    }

    private static void discoverScripts(File folder, Set<String> choices) throws IOException {
        File[] files = folder.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".rb"));
        if (files == null) return;
        Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        for (File file : files) choices.add(file.getCanonicalPath());
    }

    private static void prune(File current) {
        File[] sessions = current.getParentFile().listFiles(File::isDirectory);
        if (sessions == null || sessions.length <= 5) return;
        Arrays.sort(sessions, (left, right) -> right.getName().compareTo(left.getName()));
        for (int i = 5; i < sessions.length; i++) {
            File[] files = sessions[i].listFiles();
            if (files != null) for (File file : files) file.delete();
            sessions[i].delete();
        }
    }
}
