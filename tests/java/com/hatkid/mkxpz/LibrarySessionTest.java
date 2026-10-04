package com.hatkid.mkxpz;

import android.content.Context;
import android.os.Environment;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Host integration checks; tests/run-library-tests.py supplies minimal Android I/O shims. */
public final class LibrarySessionTest {
    public static void main(String[] args) throws Exception {
        File work = new File(args[0]);
        Environment.root = new File(work, "storage");
        Context context = new Context(new File(work, "private"), new File(args[1]));
        GameLibrary.initialize(context);
        File root = StartupConfig.directory();
        File shared = new File(root, "scripts/load-zlib.rb");
        byte[] originalScript = Files.readAllBytes(shared.toPath());
        write(shared, "# user customized this script\n");
        GameLibrary.initialize(context);
        check(read(shared).contains("customized"), "Initialization overwrote a user script");
        Files.write(shared.toPath(), originalScript);
        File a = game(new File(root, "games/A"), "Alpha", "Game.exe");
        File b = game(new File(root, "games/B"), "Beta", "game.EXE");
        File external = game(new File(work, "elsewhere/C"), "External", "Game.exe");
        game(new File(a, "nested"), "Nested", "Game.exe");
        GameLibrary library = new GameLibrary(context);
        check(library.games().size() == 2, "Default folder scanning is incorrect");
        check(GameLibrary.title(b).equals("Beta"), "Case-insensitive discovery failed");
        library.add(external);
        library.add(new File(external, "."));
        check(library.games().size() == 3, "Manual entries were duplicated");
        library.remove(a);
        check(a.exists() && library.games().size() == 2, "Removing an entry deleted files or failed to hide it");
        library = new GameLibrary(context);
        check(library.games().size() == 2, "Library state did not persist");
        library.add(a);
        library.setGamesDirectory(new File(work, "elsewhere"));
        check(library.games().size() == 2, "Custom library folder scanning failed");
        library.setGamesDirectory(new File(root, "games"));

        File rootConfig = new File(root, "mkxp.json");
        File gameConfig = new File(a, "mkxp.json");
        write(rootConfig, "{\"fullscreen\":false,\"unknown\":{\"keep\":17},\"bindingNames\":{\"c\":\"Use\",\"x\":\"Jump\"},\"preloadScript\":[\"scripts/load-zlib.rb\"]}");
        write(gameConfig, "{\"windowTitle\":\"Alpha\",\"defScreenW\":512,\"bindingNames\":{\"c\":\"Confirm\"},\"preloadScript\":[\"scripts/load-zlib.rb\",\"scripts/local.rb\"]}");
        write(new File(a, "scripts/local.rb"), "# local script\n");
        String rootBefore = read(rootConfig), gameBefore = read(gameConfig);
        write(new File(rootConfig.getPath() + ".bak"), "{\"wrongBackup\":true}");
        write(new File(gameConfig.getPath() + ".bak"), "{\"wrongBackup\":true}");
        write(new File(a, "Game.ini.bak"), "[Game]\nTitle=Wrong backup\n");
        check(GameLibrary.title(a).equals("Alpha"), "Reading a title restored a user's backup file");
        LaunchSession initial = new LaunchSession(context, a, library);
        check(initial.options.getInt("defScreenW") == 512, "Game-specific options were lost");
        check(initial.options.getJSONObject("unknown").getInt("keep") == 17, "Unknown defaults were lost");
        JSONObject bindings = initial.options.getJSONObject("bindingNames");
        check(bindings.getString("c").equals("Confirm") && bindings.getString("x").equals("Jump"), "Bindings were not merged");
        JSONArray preloads = initial.options.getJSONArray("preloadScript");
        check(preloads.length() == 3, "Initial preload selection was not preserved");
        check(preloads.getString(1).equals(shared.getCanonicalPath()), "Shared scripts were not resolved absolutely");
        check(preloads.getString(2).equals(new File(a, "scripts/local.rb").getCanonicalPath()), "Game-relative scripts changed");
        check(!read(new File(initial.directory, "exception-trace.rb")).contains("__LOG_PATH__"), "Trace path was not inserted");
        check(initial.options.getString("gameFolder").equals(a.getCanonicalPath()), "Selected game path is wrong");

        JSONObject profile = new JSONObject();
        profile.put("display", new JSONObject().put("fullscreen", true));
        profile.put("scripts", new JSONArray());
        profile.put("traceExceptions", false);
        library.saveProfile(a, profile);
        LaunchSession disabled = new LaunchSession(context, a, library);
        check(disabled.options.getJSONArray("preloadScript").length() == 0, "Explicitly disabled preloads came back");
        check(disabled.options.getBoolean("fullscreen"), "Per-game display setting failed");
        LaunchSession other = new LaunchSession(context, b, new GameLibrary(context));
        check(!other.options.getBoolean("fullscreen"), "Display settings leaked to another game");
        check(other.options.getJSONArray("preloadScript").length() == 2, "Script selection leaked to another game");
        check(rootBefore.equals(read(rootConfig)) && gameBefore.equals(read(gameConfig)), "Original configs were modified");

        profile.put("scripts", new JSONArray().put("scripts/missing.rb"));
        library.saveProfile(a, profile);
        String latestBefore = read(new File(context.getFilesDir(), "latest-session.txt"));
        try { new LaunchSession(context, a, library); throw new AssertionError("Missing preload was accepted"); }
        catch (java.io.IOException expected) { check(expected.getMessage().contains("missing"), "Missing preload was not identified"); }
        check(latestBefore.equals(read(new File(context.getFilesDir(), "latest-session.txt"))), "Failed launch replaced the latest successful report");
        library.saveProfile(a, new JSONObject());
        check(new LaunchSession(context, a, library).options.getJSONArray("preloadScript").length() == 3, "Reset did not restore original selections");
        check(new File(context.getFilesDir(), "sessions").listFiles(File::isDirectory).length <= 5, "Session retention is unbounded");
        File latest = new File(read(new File(context.getFilesDir(), "latest-session.txt")));
        Diagnostics diagnostics = new Diagnostics(latest);
        char[] large = new char[1024 * 1024 + 50];
        java.util.Arrays.fill(large, 'x');
        diagnostics.append("messages.log", new String(large));
        diagnostics.append("messages.log", "should not exceed the cap");
        check(new File(latest, "messages.log").length() == 1024 * 1024, "Diagnostic log cap failed");
        write(new File(latest, "ruby-exceptions.log"), "NoMethodError: undefined method index\n059:game-script:59\n");
        String report = Diagnostics.report(context);
        check(report.contains("NoMethodError") && report.contains("059:game-script:59") && report.contains("appVersion"), "Diagnostic report lost exception details or metadata");
        write(gameConfig, "{invalid config");
        try { new LaunchSession(context, a, library); throw new AssertionError("Broken config was accepted"); }
        catch (Exception expected) { check(expected.getMessage().contains("mkxp.json"), "Broken config path was not reported"); }
        System.out.println("Library discovery, script selection, config isolation, retention and diagnostic report checks passed");
    }

    private static File game(File folder, String title, String executable) throws Exception {
        write(new File(folder, executable), "");
        write(new File(folder, "Game.ini"), "[Game]\nTitle=" + title + "\n");
        return folder;
    }
    private static void write(File file, String text) throws Exception {
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }
    private static String read(File file) throws Exception { return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
