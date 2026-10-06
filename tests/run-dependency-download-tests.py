#!/usr/bin/env python3
"""Check JNI dependency setup using local downloads; no network or NDK needed."""
import io
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile

script = Path(__file__).resolve().parents[1] / 'app/jni/get_deps.sh'
other_dependencies = ('libogg', 'libvorbis', 'libtheora', 'uchardet', 'pixman',
                      'physfs', 'openal', 'SDL2', 'SDL2_image', 'SDL2_ttf',
                      'SDL2_sound', 'openssl', 'ruby')

with tempfile.TemporaryDirectory(prefix='mkxp-dependency-tests-') as temp:
    root = Path(temp)
    fixture = root / 'libiconv-fixture.tar.gz'
    with tarfile.open(fixture, 'w:gz') as archive:
        contents = b'#!/bin/sh\nexit 0\n'
        entry = tarfile.TarInfo('libiconv-1.17/configure')
        entry.size = len(contents)
        archive.addfile(entry, io.BytesIO(contents))
    binaries = root / 'bin'
    binaries.mkdir()
    wget = binaries / 'wget'
    wget.write_text('''#!/bin/bash
set -eu
printf '%s\\n' "$*" >> "$DOWNLOAD_LOG"
case "$DOWNLOAD_MODE" in
  fail) exit 8 ;;
  fallback) [[ "${!#}" == https://ftpmirror.gnu.org/* ]] || exit 8 ;;
esac
if [[ "$DOWNLOAD_MODE" == corrupt ]]; then
  printf 'invalid archive' > libiconv-1.17.tar.gz
else
  cp "$DOWNLOAD_FIXTURE" libiconv-1.17.tar.gz
fi
''')
    wget.chmod(0o755)
    for mode in ('success', 'fallback', 'fail', 'corrupt', 'incomplete', 'existing'):
        work = root / mode
        work.mkdir()
        for dependency in other_dependencies:
            (work / dependency).mkdir()
        if mode in ('incomplete', 'existing'):
            (work / 'libiconv').mkdir()
        if mode == 'existing':
            (work / 'libiconv/configure').write_text('# existing source\n')
        log = work / 'downloads.log'
        env = dict(os.environ, PATH=str(binaries) + ':' + os.environ['PATH'],
                   DOWNLOAD_MODE=mode, DOWNLOAD_FIXTURE=str(fixture), DOWNLOAD_LOG=str(log))
        result = subprocess.run(['bash', str(script)], cwd=work, env=env,
                                text=True, capture_output=True)
        succeeded = mode in ('success', 'fallback', 'existing')
        assert (result.returncode == 0) == succeeded, (mode, result.stdout, result.stderr)
        assert ('Done!' in result.stdout) == succeeded, (mode, result.stdout)
        if succeeded:
            assert (work / 'libiconv/configure').is_file(), mode
        if mode == 'incomplete':
            assert 'libiconv/configure is missing' in result.stderr
        calls = log.read_text().splitlines() if log.exists() else []
        expected = {'success': 1, 'fallback': 2, 'fail': 2, 'corrupt': 1,
                    'incomplete': 0, 'existing': 0}[mode]
        assert len(calls) == expected, (mode, calls)
        if mode == 'fallback':
            assert 'https://ftpmirror.gnu.org/' in calls[-1]
        print(mode + ': passed')
print('Dependency download failure, fallback and source-validation checks passed.')
