"""Standalone Foundation checks for the save sync folder (no Xcode project needed)."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class SaveSyncChecks(unittest.TestCase):
    def test_two_way_sync(self):
        with tempfile.TemporaryDirectory() as temporary:
            binary = Path(temporary) / 'checks'
            subprocess.run([
                'swiftc', '-import-objc-header', str(ROOT / 'ios/Tests/SaveChecks-Bridging-Header.h'),
                str(ROOT / 'ios/App/SaveZip.swift'), str(ROOT / 'ios/App/SaveManager.swift'),
                str(ROOT / 'ios/App/GlobalSaveEditor.swift'),
                str(ROOT / 'ios/App/SaveSync.swift'), str(ROOT / 'ios/Tests/SaveSyncChecks.swift'),
                '-lz', '-o', str(binary),
            ], check=True)
            subprocess.run([str(binary), temporary], check=True)


if __name__ == '__main__':
    unittest.main()
