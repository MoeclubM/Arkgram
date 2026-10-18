"""CI host crash diagnostics: print code locations, never dump memory or registers."""
import json
import subprocess
import sys
from pathlib import Path

reports = sorted(Path('/tmp/android-runner').rglob('*.dmp'))
print('Emulator crash reports:', len(reports), flush=True)
for path in reports:
    result = subprocess.run([sys.argv[1], '--json', str(path)], check=True,
                            stdout=subprocess.PIPE, text=True)
    report = json.loads(result.stdout)
    print('Emulator crash type:', report['crash_info']['type'], flush=True)
    for frame in report['crashing_thread']['frames']:
        # Symbol names are optional in the vendor format; null exposes missing symbols.
        print(json.dumps({key: frame.get(key) for key in
                          ['frame', 'module', 'module_offset', 'function']}), flush=True)
