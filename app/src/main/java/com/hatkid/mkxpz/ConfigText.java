package com.hatkid.mkxpz;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Edits top-level booleans without rewriting unknown JSON5 values or comments. */
final class ConfigText {
    static String update(String source, Map<String, Boolean> changes) {
        List<Edit> edits = new ArrayList<>();
        int i = skip(source, 0);
        if (i < source.length() && source.charAt(i) == '\uFEFF') i = skip(source, i + 1);
        if (i >= source.length() || source.charAt(i++) != '{') throw new IllegalArgumentException("Expected a config object");
        int objectStart = i - 1;
        int close = -1;
        List<String> found = new ArrayList<>();
        while ((i = skip(source, i)) < source.length()) {
            if (source.charAt(i) == '}') { close = i; break; }
            int start = i;
            if (source.charAt(i) == '"' || source.charAt(i) == '\'') i = stringEnd(source, i);
            else while (i < source.length() && source.charAt(i) != ':' && !Character.isWhitespace(source.charAt(i))) i++;
            String key = source.substring(start, i);
            // Exposed keys are ASCII. Escaped spellings are deliberately rejected when editing.
            if (key.startsWith("\"") || key.startsWith("'")) key = key.substring(1, key.length() - 1);
            if (key.indexOf('\\') >= 0) throw new IllegalArgumentException("Escaped config keys cannot be edited safely by this screen");
            i = skip(source, i);
            if (i >= source.length() || source.charAt(i++) != ':') throw new IllegalArgumentException("Invalid config property");
            i = skip(source, i);
            int valueStart = i;
            int depth = 0;
            while (i < source.length()) {
                char c = source.charAt(i);
                if (c == '"' || c == '\'') { i = stringEnd(source, i); continue; }
                int after = skip(source, i);
                if (after != i) { i = after; continue; }
                if (c == '[' || c == '{') depth++;
                if (c == ']' || c == '}') {
                    if (depth == 0) break;
                    depth--;
                }
                if (c == ',' && depth == 0) break;
                i++;
            }
            if (changes.containsKey(key)) {
                if (found.contains(key)) throw new IllegalArgumentException("Duplicate setting: " + key);
                found.add(key);
                // Replace just the boolean token, retaining surrounding comments.
                int end = valueStart;
                while (end < source.length() && Character.isLetter(source.charAt(end))) end++;
                String token = source.substring(valueStart, end);
                if (!token.equals("true") && !token.equals("false")) throw new IllegalArgumentException("Invalid boolean: " + key);
                edits.add(new Edit(valueStart, end, changes.get(key).toString()));
            }
            i = skip(source, i);
            if (i < source.length() && source.charAt(i) == ',') i++;
            else if (i >= source.length() || source.charAt(i) != '}') throw new IllegalArgumentException("Invalid config separator");
        }
        if (close < 0) throw new IllegalArgumentException("Unclosed config object");
        StringBuilder additions = new StringBuilder();
        for (Map.Entry<String, Boolean> change : changes.entrySet()) {
            if (!found.contains(change.getKey())) {
                if (additions.length() > 0) additions.append(',');
                additions.append('\n').append("  \"").append(change.getKey()).append("\": ").append(change.getValue());
            }
        }
        if (additions.length() > 0) {
            // Find the last significant character, excluding comments.
            int last = -1;
            for (int p = skip(source, objectStart + 1); p < close;) {
                if (source.charAt(p) == '"' || source.charAt(p) == '\'') { p = stringEnd(source, p); last = p - 1; }
                else { last = p++; }
                p = skip(source, p);
            }
            if (last >= 0 && source.charAt(last) != ',') additions.insert(0, ',');
            additions.append('\n');
            edits.add(new Edit(close, close, additions.toString()));
        }
        StringBuilder result = new StringBuilder(source);
        for (int e = edits.size() - 1; e >= 0; e--) {
            Edit edit = edits.get(e);
            result.replace(edit.start, edit.end, edit.text);
        }
        return result.toString();
    }

    private static int stringEnd(String source, int i) {
        char quote = source.charAt(i++);
        while (i < source.length()) {
            char c = source.charAt(i++);
            if (c == '\\') i++;
            else if (c == quote) return i;
        }
        throw new IllegalArgumentException("Unclosed config string");
    }

    private static int skip(String source, int i) {
        while (i < source.length()) {
            if (Character.isWhitespace(source.charAt(i))) { i++; continue; }
            if (source.startsWith("//", i)) {
                i += 2;
                while (i < source.length() && source.charAt(i) != '\n' && source.charAt(i) != '\r') i++;
            } else if (source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2);
                if (end < 0) throw new IllegalArgumentException("Unclosed config comment");
                i = end + 2;
            } else break;
        }
        return i;
    }

    private static final class Edit {
        final int start, end;
        final String text;
        Edit(int start, int end, String text) { this.start = start; this.end = end; this.text = text; }
    }
}
