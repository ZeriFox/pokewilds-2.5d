#!/usr/bin/env python3
"""Compatibility entry point: the one-time migration is now tracked Java.

This command verifies evidence; it never rewrites sources or renews approvals.
Use prepare_stardew_presentation.py to import art and build.py to compile.
The original migration is preserved in git at eec20e70.
"""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ('BwAssets.java', 'JohtoRenderer.java', 'JohtoBattleRenderer.java',
           'ModernPartyUi.java', 'ModernInventoryUi.java', 'PmdBattleSprites.java',
           'util/SpriteProxy.java')

def main():
    manifest = json.loads((ROOT/'baseline/modernization-scope.json').read_text(encoding='utf-8'))
    reviewed = {**manifest['modified_original_sources'], **manifest['added_sources']}
    for name in SOURCES:
        path = 'src/com/pkmngen/game/' + name
        actual = hashlib.sha256((ROOT/path).read_bytes()).hexdigest()
        if actual != reviewed[path]['reviewed_sha256']:
            raise RuntimeError('Tracked source needs review: ' + path)
    print('Migration retired: tracked Java matches the reviewed manifest; no files changed.')
    print('Build: python tools/prepare_stardew_presentation.py, then python build.py')

if __name__ == '__main__':
    main()
