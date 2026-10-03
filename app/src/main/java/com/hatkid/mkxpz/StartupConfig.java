package com.hatkid.mkxpz;

import android.os.Environment;
import android.util.AtomicFile;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

/** Owns the same root file the JNI startup directory uses. */
final class StartupConfig {
    static File directory() {
        return new File(Environment.getExternalStorageDirectory(), "mkxp-z");
    }
    final File file = new File(directory(), "mkxp.json");
    final File previous = new File(directory(), "mkxp.json.previous");
    private byte[] original;
    private boolean existed;
    JSONObject options;

    void load() throws Exception {
        existed = file.exists();
        original = existed ? read(file) : new byte[0];
        options = existed ? parse(original) : new JSONObject();
        for (String key : new String[]{"fullscreen", "fixedAspectRatio", "integerScalingActive", "subImageFix"}) {
            if (options.has(key) && !(options.get(key) instanceof Boolean))
                throw new IOException(key + " must be true or false. The file has not been changed.");
        }
        if (options.has("gameFolder") && !(options.get("gameFolder") instanceof String))
            throw new IOException("gameFolder must be a folder path.");
    }

    static JSONObject parse(byte[] bytes) throws JSONException {
        String source = new String(bytes, StandardCharsets.UTF_8);
        if (source.startsWith("\uFEFF")) source = source.substring(1);
        JSONTokener tokener = new JSONTokener(source);
        Object value = tokener.nextValue();
        if (!(value instanceof JSONObject) || tokener.nextClean() != 0)
            throw new JSONException("Expected one JSON object. The file has not been changed.");
        return (JSONObject) value;
    }

    void save(Map<String, Boolean> changes) throws Exception {
        checkUnchanged();
        byte[] next = ConfigText.update(existed ? new String(original, StandardCharsets.UTF_8) : "{}\n", changes).getBytes(StandardCharsets.UTF_8);
        // Do not replace the undo copy when saving unchanged settings.
        if (changes.isEmpty() && existed) return;
        if (!directory().isDirectory() && !directory().mkdirs())
            throw new IOException("Cannot create " + directory());
        if (existed) write(previous, original);
        else write(previous, "{}\n".getBytes(StandardCharsets.UTF_8));
        write(file, next);
        load();
    }

    void restore() throws Exception {
        checkUnchanged();
        if (!previous.isFile()) throw new IOException("No previous settings have been saved yet.");
        byte[] backup = read(previous);
        parse(backup);
        write(file, backup);
        load();
    }

    private void checkUnchanged() throws IOException {
        if (file.exists() != existed || (existed && !Arrays.equals(original, read(file))))
            throw new IOException("The config changed outside this screen. Reload it before saving.");
    }

    static byte[] read(File source) throws IOException {
        try (InputStream stream = new AtomicFile(source).openRead();
             ByteArrayOutputStream result = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) result.write(buffer, 0, count);
            return result.toByteArray();
        }
    }

    private static void write(File target, byte[] bytes) throws IOException {
        AtomicFile atomic = new AtomicFile(target);
        FileOutputStream stream = null;
        try {
            stream = atomic.startWrite();
            stream.write(bytes);
            atomic.finishWrite(stream);
        } catch (IOException e) {
            if (stream != null) atomic.failWrite(stream);
            throw e;
        }
    }
}
