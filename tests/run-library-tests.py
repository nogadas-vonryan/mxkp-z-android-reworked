#!/usr/bin/env python3
"""Run host integration checks with --json-jar /path/to/org.json.jar.

Minimal shims provide Android file locations and I/O; these checks do not verify
Android permissions, AtomicFile crash recovery, activity lifecycle or native Ruby.
The JSON jar is a host test dependency only, never packaged into the app.
"""
import argparse
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument('--json-jar', type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
shims = {
    'android/os/Environment.java': '''package android.os;
        public class Environment { public static java.io.File root;
        public static java.io.File getExternalStorageDirectory() { return root; } }''',
    'android/os/Build.java': '''package android.os;
        public class Build { public static String MANUFACTURER="Test", MODEL="Host";
        public static String[] SUPPORTED_ABIS={"arm64-v8a"};
        public static class VERSION { public static String RELEASE="Test"; } }''',
    'android/content/Context.java': '''package android.content;
        public class Context {
        private final java.io.File files; private final android.content.res.AssetManager assets;
        public Context(java.io.File files, java.io.File assets) { this.files=files; files.mkdirs();
        this.assets=new android.content.res.AssetManager(assets); }
        public java.io.File getFilesDir() { return files; }
        public android.content.res.AssetManager getAssets() { return assets; }
        public String getPackageName() { return "com.hatkid.mkxpz"; }
        public android.content.pm.PackageManager getPackageManager() { return new android.content.pm.PackageManager(); }
        }''',
    'android/content/res/AssetManager.java': '''package android.content.res;
        public class AssetManager { private final java.io.File root;
        public AssetManager(java.io.File root) { this.root=root; }
        public java.io.InputStream open(String name) throws java.io.IOException {
        return new java.io.FileInputStream(new java.io.File(root,name)); } }''',
    'android/content/pm/PackageManager.java': '''package android.content.pm;
        public class PackageManager {
        public PackageInfo getPackageInfo(String name,int flags) { return new PackageInfo(); } }''',
    'android/content/pm/PackageInfo.java': '''package android.content.pm;
        public class PackageInfo { public String versionName="test"; }''',
    'android/util/AtomicFile.java': '''package android.util;
        public class AtomicFile { private final java.io.File file;
        public AtomicFile(java.io.File file) { this.file=file; }
        public java.io.FileInputStream openRead() throws java.io.IOException { return new java.io.FileInputStream(file); }
        public java.io.FileOutputStream startWrite() throws java.io.IOException { return new java.io.FileOutputStream(file); }
        public void finishWrite(java.io.FileOutputStream stream) throws java.io.IOException { stream.close(); }
        public void failWrite(java.io.FileOutputStream stream) { try { stream.close(); } catch (Exception ignored) {} } }''',

}
with tempfile.TemporaryDirectory(prefix='mkxp-library-tests-') as temp:
    work = Path(temp)
    sources = []
    for name, source in shims.items():
        file = work / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(source)
        sources.append(str(file))
    package = root / 'app/src/main/java/com/hatkid/mkxpz'
    sources += [str(package / (name + '.java')) for name in ('StartupConfig', 'ConfigText', 'GameLibrary', 'LaunchSession')]
    test = root / 'tests/java/com/hatkid/mkxpz'
    sources += [str(test / 'LibrarySessionTest.java'), str(test / 'ConfigTextTest.java')]
    classes = work / 'classes'
    classes.mkdir()
    subprocess.run(['javac', '-cp', str(args.json_jar.resolve()), '-d', str(classes), *sources], check=True)
    classpath = str(classes) + ':' + str(args.json_jar.resolve())
    for name in ('ConfigTextTest', 'LibrarySessionTest'):
        subprocess.run(['java', '-cp', classpath, 'com.hatkid.mkxpz.' + name,
                        str(work / 'fixture'), str(root / 'app/src/main/assets')], check=True)
