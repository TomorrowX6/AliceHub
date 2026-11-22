"""Exercise the app driver with native/Xcode fixtures, including existing archives."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class BuildAppChecks(unittest.TestCase):
    def test_native_is_rebuilt_with_or_without_staging(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            shutil.copyfile(ROOT / 'ios/build-app.sh', root / 'build-app.sh')
            calls = root / 'calls'
            native = root / 'build-native.sh'
            native.write_text('#!/bin/bash\nset -eu\necho "native:$1" >> "$CALLS"\n'
                              'mkdir -p "$(dirname "$0")/build/$1/stage/lib"\n'
                              'touch "$(dirname "$0")/build/$1/stage/lib/libxsystem4.a"\n'
                              'mkdir -p "$(dirname "$0")/build/$1/stage/share/system3/mda"\n'
                              'touch "$(dirname "$0")/build/$1/stage/share/system3/mda/AMUS.DAT"\n')
            native.chmod(0o755)
            for name in ('xcodegen', 'xcodebuild'):
                tool = root / name
                tool.write_text('#!/bin/bash\necho "' + name + '" >> "$CALLS"\n')
                tool.chmod(0o755)
            env = dict(os.environ, PATH=str(root) + os.pathsep + os.environ['PATH'], CALLS=str(calls))
            for _ in range(2):
                subprocess.run(['bash', str(root / 'build-app.sh'), 'sim'], env=env, check=True)
            self.assertEqual(calls.read_text().splitlines(),
                             ['native:iphonesimulator', 'xcodegen', 'xcodebuild'] * 2)
            self.assertTrue((root / 'build/system3/mda/AMUS.DAT').is_file())
            product = root / 'build/app-iphoneos/Build/Products/Release-iphoneos/AliceHub.app'
            product.mkdir(parents=True)
            (product / 'fixture').write_text('app')
            subprocess.run(['bash', str(root / 'build-app.sh'), 'ipa'], env=env, check=True,
                           capture_output=True, text=True)
            self.assertEqual(calls.read_text().splitlines()[-3:], ['native:iphoneos', 'xcodegen', 'xcodebuild'])
            self.assertTrue((root / 'build/AliceHub.ipa').is_file())
            before = calls.read_text()
            invalid = subprocess.run(['bash', str(root / 'build-app.sh'), 'invalid'], env=env,
                                     capture_output=True, text=True)
            self.assertEqual(invalid.returncode, 2)
            self.assertIn('Usage:', invalid.stderr)
            self.assertEqual(calls.read_text(), before)


if __name__ == '__main__':
    unittest.main()
