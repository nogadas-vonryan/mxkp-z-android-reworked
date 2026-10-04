package com.hatkid.mkxpz;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** App-private, bounded reports that users can explicitly export. */
final class Diagnostics {
    private static final int LIMIT = 1024 * 1024;
    private final File directory;
    private volatile Process reader;
    private volatile boolean stopped;

    Diagnostics(File directory) { this.directory = directory; }

    void start() {
        append("messages.log", "Engine activity created (pid " + android.os.Process.myPid() + ")");
        Thread thread = new Thread(() -> {
            try {
                reader = new ProcessBuilder("logcat", "-v", "threadtime", "-T", "1").redirectErrorStream(true).start();
                if (stopped) { reader.destroy(); return; }
                try (BufferedReader lines = new BufferedReader(new InputStreamReader(reader.getInputStream(), StandardCharsets.UTF_8));
                     FileOutputStream output = new FileOutputStream(new File(directory, "engine.log"), true)) {
                    int written = 0;
                    String pid = Integer.toString(android.os.Process.myPid());
                    String line;
                    while ((line = lines.readLine()) != null && written < LIMIT) {
                        String[] columns = line.trim().split("\\s+", 6);
                        if (columns.length < 6 || !columns[2].equals(pid)) continue;
                        byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
                        output.write(bytes, 0, Math.min(bytes.length, LIMIT - written));
                        output.flush();
                        written += bytes.length;
                    }
                }
            } catch (IOException e) { append("messages.log", "Logcat capture unavailable: " + e); }
            finally { if (reader != null) reader.destroy(); }
        }, "mkxp-diagnostics");
        thread.setDaemon(true);
        thread.start();
    }

    synchronized void append(String name, String text) {
        File file = new File(directory, name);
        try (FileOutputStream stream = new FileOutputStream(file, true)) {
            byte[] bytes = ("[" + new java.util.Date() + "] " + text + "\n").getBytes(StandardCharsets.UTF_8);
            int available = (int) Math.max(0, LIMIT - file.length());
            stream.write(bytes, 0, Math.min(available, bytes.length));
        } catch (IOException e) { Log.w("mkxp-wrapper", "Cannot save diagnostics", e); }
    }

    void stop() {
        stopped = true;
        append("messages.log", "Engine activity closing");
        if (reader != null) reader.destroy();
    }

    static void recordLauncherError(Context context, Exception error) {
        new Diagnostics(context.getFilesDir()).append("launcher-errors.log", Log.getStackTraceString(error));
    }

    static String report(Context context) throws IOException {
        StringBuilder report = new StringBuilder("mkxp-z Android diagnostic report\n");
        File pointer = new File(context.getFilesDir(), "latest-session.txt");
        if (StartupConfig.existsAtomic(pointer)) {
            File directory = new File(new String(StartupConfig.readAtomic(pointer), StandardCharsets.UTF_8));
            File sessions = new File(context.getFilesDir(), "sessions").getCanonicalFile();
            if (!sessions.equals(directory.getCanonicalFile().getParentFile())) throw new IOException("Invalid session path");
            for (String name : new String[]{"session.json", "mkxp.json", "messages.log", "ruby-exceptions.log", "engine.log"}) {
                report.append("\n--- ").append(name).append(" ---\n");
                File file = new File(directory, name);
                if (file.isFile() && file.length() > 0) report.append(new String(StartupConfig.read(file), StandardCharsets.UTF_8));
                else report.append("No entries captured.\n");
            }
        } else report.append("No game session recorded yet.\n");
        File launcher = new File(context.getFilesDir(), "launcher-errors.log");
        if (launcher.isFile()) report.append("\n--- Launcher errors ---\n").append(new String(StartupConfig.read(launcher), StandardCharsets.UTF_8));
        return report.toString();
    }
}
