import os
import pathlib
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = ROOT / '.github/scripts/android-emulator-smoke.sh'

FAKE_ADB = '''#!/usr/bin/env python3
import os, pathlib, sys
args = sys.argv[1:]
state = pathlib.Path(os.environ['ADB_STATE'])
log = pathlib.Path(os.environ['ADB_LOG'])
with log.open('a') as f: f.write(' '.join(args)+'\\n')
if args == ['wait-for-device']: sys.exit(0)
if args == ['shell', 'getprop', 'sys.boot_completed']:
  print('1')
  sys.exit(0)
if args[:2] == ['install', '-r']:
  sys.exit(0 if len(args)==3 and pathlib.Path(args[2]).exists() else 2)
if args[:2] == ['logcat', '-c']: sys.exit(0)
if args[:2] == ['logcat', '-d']:
  if os.getenv('ADB_CRASH') == '1' and state.exists():
    print('E AndroidRuntime: FATAL EXCEPTION: main')
    print('E AndroidRuntime: Process: app.hiddify.com, PID: 1234')
  sys.exit(0)
if args[:4] == ['shell', 'am', 'start', '-W']:
  print('Status: ok')
  sys.exit(0)
if args[:3] == ['shell', 'pidof', '-s']:
  print('1234')
  sys.exit(0)
if args[:3] == ['shell', 'svc', 'wifi']:
  state.write_text(args[3])
  sys.exit(0)
if args[:4] == ['shell', 'cmd', 'wifi', 'status']:
  print('Wi-Fi is '+ ('disabled' if state.exists() and state.read_text()=='disable' else 'enabled'))
  sys.exit(0)
if args[:3] == ['shell', 'dumpsys', 'activity']:
  print('mResumedActivity: ActivityRecord{com.hiddify.hiddify.MainActivity app.hiddify.com}')
  sys.exit(0)
print('Unexpected adb args: '+repr(args), file=sys.stderr)
sys.exit(2)
'''

class SmokeTests(unittest.TestCase):
    def run_smoke(self, with_apk=True, crash=False):
        with tempfile.TemporaryDirectory() as d:
            root=pathlib.Path(d)
            binary=root/'adb'
            binary.write_text(FAKE_ADB)
            binary.chmod(0o755)
            (root/'integration-apk').mkdir()
            if with_apk:
                (root/'integration-apk'/'app-debug.apk').write_bytes(b'fake')
            env=os.environ.copy()
            env.update(PATH=str(root)+os.pathsep+env['PATH'], ADB_STATE=str(root/'state'), ADB_LOG=str(root/'adb.log'), ADB_CRASH=('1' if crash else '0'))
            p=subprocess.run(['bash', str(SCRIPT)], cwd=root, env=env, text=True, capture_output=True)
            return p, (root/'adb.log').read_text() if (root/'adb.log').exists() else ''
    def test_starts_downloaded_apk_and_validates_wifi_toggles(self):
        p, log = self.run_smoke()
        self.assertEqual(0, p.returncode, p.stderr+p.stdout)
        self.assertIn('install -r integration-apk/app-debug.apk', log)
        self.assertIn('shell svc wifi disable', log)
        self.assertIn('shell svc wifi enable', log)
    def test_fails_if_artifact_absent(self):
        p, _ = self.run_smoke(with_apk=False)
        self.assertNotEqual(0, p.returncode)
        self.assertIn('APK', p.stderr)
    def test_reports_app_crash_after_toggle(self):
        p, _ = self.run_smoke(crash=True)
        self.assertNotEqual(0, p.returncode)
        self.assertIn('FATAL EXCEPTION', p.stderr)

if __name__=='__main__': unittest.main()
