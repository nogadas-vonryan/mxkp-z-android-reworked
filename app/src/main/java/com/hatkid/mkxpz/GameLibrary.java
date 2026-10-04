package com.hatkid.mkxpz;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Library entries and profiles live in app storage; game files are never edited. */
final class GameLibrary {
    final Context context;
    final File stateFile;
    JSONObject state;

    GameLibrary(Context context) throws Exception {
        this.context = context;
        stateFile = new File(context.getFilesDir(), "library.json");
        state = StartupConfig.existsAtomic(stateFile) ? StartupConfig.parse(StartupConfig.readAtomic(stateFile)) : new JSONObject();
    }

    static void initialize(Context context) throws IOException {
        File scripts = new File(StartupConfig.directory(), "scripts");
        if (!scripts.isDirectory() && !scripts.mkdirs()) throw new IOException("Cannot create " + scripts);
        File games = new File(StartupConfig.directory(), "games");
        if (!games.isDirectory() && !games.mkdirs()) throw new IOException("Cannot create " + games);
        File rootConfig = new File(StartupConfig.directory(), "mkxp.json");
        if (!rootConfig.exists()) StartupConfig.write(rootConfig, "{}\n".getBytes(StandardCharsets.UTF_8));
        for (String name : new String[]{"load-zlib.rb", "fix-essentials-clock.rb", "disable-steam.rb", "disable-audio.rb"}) {
            File target = new File(scripts, name);
            if (target.exists()) continue;
            try (java.io.InputStream input = context.getAssets().open("scripts/" + name);
                 java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                StartupConfig.write(target, output.toByteArray());
            }
        }
    }

    File gamesDirectory() {
        return new File(state.optString("gamesDirectory", new File(StartupConfig.directory(), "games").getAbsolutePath()));
    }

    void setGamesDirectory(File directory) throws Exception {
        state.put("gamesDirectory", directory.getCanonicalPath());
        save();
    }

    void add(File game) throws Exception {
        if (!isGame(game)) throw new IOException("Choose a folder containing Game.exe.");
        Set<String> paths = paths("added");
        paths.add(game.getCanonicalPath());
        state.put("added", new JSONArray(paths));
        Set<String> hidden = paths("hidden");
        hidden.remove(game.getCanonicalPath());
        state.put("hidden", new JSONArray(hidden));
        save();
    }

    void remove(File game) throws Exception {
        String path = game.getCanonicalPath();
        Set<String> added = paths("added");
        added.remove(path);
        state.put("added", new JSONArray(added));
        Set<String> hidden = paths("hidden");
        hidden.add(path);
        state.put("hidden", new JSONArray(hidden));
        save();
    }

    String displayName(File game) {
        JSONObject names = state.optJSONObject("names");
        if (names != null) {
            try {
                String name = names.optString(game.getCanonicalPath(), "").trim();
                if (!name.isEmpty()) return name;
            } catch (IOException ignored) {}
        }
        return title(game);
    }

    void rename(File game, String name) throws Exception {
        JSONObject names = state.optJSONObject("names");
        if (names == null) names = new JSONObject();
        String path = game.getCanonicalPath();
        name = name.trim();
        if (name.isEmpty()) names.remove(path);
        else names.put(path, name);
        state.put("names", names);
        save();
    }

    List<File> games() throws Exception {
        Set<String> candidates = paths("added");
        File folder = gamesDirectory();
        if (isGame(folder)) candidates.add(folder.getCanonicalPath());
        File[] children = folder.listFiles(File::isDirectory);
        if (children != null) for (File child : children) {
            if (isGame(child)) candidates.add(child.getCanonicalPath());
        }
        // Keep the pre-library installation visible, including a game in the root.
        if (isGame(StartupConfig.directory())) candidates.add(StartupConfig.directory().getCanonicalPath());
        File rootConfig = new File(StartupConfig.directory(), "mkxp.json");
        if (rootConfig.isFile()) {
            try {
                JSONObject root = StartupConfig.parse(StartupConfig.read(rootConfig));
                File legacy = new File(root.optString("gameFolder", "."));
                if (!legacy.isAbsolute()) legacy = new File(StartupConfig.directory(), legacy.getPath());
                if (isGame(legacy)) candidates.add(legacy.getCanonicalPath());
            } catch (Exception ignored) { /* Launch reports config errors; listing still works. */ }
        }
        candidates.removeAll(paths("hidden"));
        List<File> games = new ArrayList<>();
        for (String path : candidates) games.add(new File(path));
        Collections.sort(games, (left, right) -> {
            int order = displayName(left).compareToIgnoreCase(displayName(right));
            return order != 0 ? order : left.getPath().compareTo(right.getPath());
        });
        return games;
    }

    private Set<String> paths(String key) {
        Set<String> result = new LinkedHashSet<>();
        JSONArray array = state.optJSONArray(key);
        if (array != null) for (int i = 0; i < array.length(); i++) result.add(array.optString(i));
        return result;
    }

    private void save() throws Exception {
        StartupConfig.write(stateFile, state.toString(2).getBytes(StandardCharsets.UTF_8));
    }

    File profileFile(File game) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(game.getCanonicalPath().getBytes(StandardCharsets.UTF_8));
        StringBuilder name = new StringBuilder();
        for (byte b : hash) name.append(String.format(Locale.ROOT, "%02x", b & 255));
        File directory = new File(context.getFilesDir(), "profiles");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create game profiles");
        return new File(directory, name + ".json");
    }

    JSONObject profile(File game) throws Exception {
        File file = profileFile(game);
        return StartupConfig.existsAtomic(file) ? StartupConfig.parse(StartupConfig.readAtomic(file)) : new JSONObject();
    }

    void saveProfile(File game, JSONObject profile) throws Exception {
        StartupConfig.write(profileFile(game), profile.toString(2).getBytes(StandardCharsets.UTF_8));
    }

    static boolean isGame(File directory) {
        return directory.isDirectory() && child(directory, "Game.exe").isFile();
    }

    static File child(File directory, String name) {
        File direct = new File(directory, name);
        if (direct.exists()) return direct;
        String[] names = directory.list();
        if (names != null) for (String candidate : names) {
            if (candidate.equalsIgnoreCase(name)) return new File(directory, candidate);
        }
        return direct;
    }

    static String title(File directory) {
        File ini = child(directory, "Game.ini");
        if (ini.isFile() && ini.length() < 65536) {
            try {
                boolean gameSection = false;
                for (String line : new String(StartupConfig.read(ini), StandardCharsets.UTF_8).split("\\r?\\n")) {
                    line = line.replace("\uFEFF", "").trim();
                    if (line.startsWith("[")) gameSection = line.equalsIgnoreCase("[Game]");
                    if (gameSection && line.matches("(?i)Title\\s*=.*")) {
                        String title = line.substring(line.indexOf('=') + 1).trim();
                        if (!title.isEmpty()) return title;
                    }
                }
            } catch (IOException ignored) {}
        }
        return directory.getName();
    }
}
