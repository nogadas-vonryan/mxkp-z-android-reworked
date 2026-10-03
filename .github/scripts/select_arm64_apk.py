#!/usr/bin/env python3
"""Select a release APK by Gradle's ABI metadata, independent of its filename."""
import json
from pathlib import Path
import sys


def select(directory):
    directory = directory.resolve()
    metadata = json.loads((directory / 'output-metadata.json').read_text())
    matches = []
    for output in metadata.get('elements', []):
        abis = [f.get('value') for f in output.get('filters', [])
                if f.get('filterType') == 'ABI']
        if abis == ['arm64-v8a']:
            apk = (directory / output['outputFile']).resolve()
            if apk.parent != directory or apk.suffix != '.apk' or not apk.is_file():
                raise ValueError('Arm64 metadata points to an invalid or missing APK')
            matches.append(apk)
    if len(matches) != 1:
        raise ValueError(f'Expected one arm64 APK in Gradle metadata; found {len(matches)}')
    return matches[0]


if __name__ == '__main__':
    directory = Path(sys.argv[1])
    try:
        print(select(directory))
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(f'APK selection failed: {error}', file=sys.stderr)
        print('Available APKs: ' + ', '.join(p.name for p in directory.glob('*.apk')), file=sys.stderr)
        sys.exit(1)
