package com.hatkid.mkxpz;

import java.util.LinkedHashMap;
import java.util.Map;

/** Run with javac/java; verifies preservation without Android or native dependencies. */
public final class ConfigTextTest {
    public static void main(String[] args) {
        Map<String, Boolean> change = new LinkedHashMap<>();
        change.put("fullscreen", true);
        check("{\"fullscreen\":false,\"unknown\":{\"fullscreen\":false}}",
              "{\"fullscreen\":true,\"unknown\":{\"fullscreen\":false}}", change);
        check("{/* keep */ fullscreen: false /* reason */, custom: 0xFF, scripts:['x,y}',],}",
              "{/* keep */ fullscreen: true /* reason */, custom: 0xFF, scripts:['x,y}',],}", change);
        check("// example {\n{/* empty */}", "// example {\n{/* empty */\n  \"fullscreen\": true\n}", change);
        check("{}", "{\n  \"fullscreen\": true\n}", change);
        check("{\"x\": 1 // retain comment\n}",
              "{\"x\": 1 // retain comment\n,\n  \"fullscreen\": true\n}", change);
        check("{\"x\":1, /* trailing */}",
              "{\"x\":1, /* trailing */\n  \"fullscreen\": true\n}", change);
        change.put("fixedAspectRatio", false);
        check("{\"fullscreen\": false, \"fixedAspectRatio\":true}",
              "{\"fullscreen\": true, \"fixedAspectRatio\":false}", change);
        check("{preloadScript:['scripts/load-zlib.rb','scripts/fix-essentials-clock.rb'],bindingNames:{c:'Use'},defScreenW:512}",
              "{preloadScript:['scripts/load-zlib.rb','scripts/fix-essentials-clock.rb'],bindingNames:{c:'Use'},defScreenW:512,\n  \"fullscreen\": true,\n  \"fixedAspectRatio\": false\n}", change);
        reject("{fullscreen:false,fullscreen:true}", change);
        reject("{fullscreen:123}", change);
        reject("{fullscreen:false /* unclosed}", change);
        System.out.println("Config preservation checks passed");
    }
    private static void check(String source, String expected, Map<String, Boolean> change) {
        String actual = ConfigText.update(source, change);
        if (!actual.equals(expected)) throw new AssertionError(actual + " != " + expected);
    }
    private static void reject(String source, Map<String, Boolean> change) {
        try { ConfigText.update(source, change); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected rejection: " + source);
    }
}
