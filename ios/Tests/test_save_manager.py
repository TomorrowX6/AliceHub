"""Standalone Foundation checks and ZIP interoperability (no Xcode project needed)."""
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]


class SaveManagerChecks(unittest.TestCase):
    def test_foundation_and_interoperability(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for name, path, data, method in (
                ('android.zip', 'SaveData/android.asd', b'android', zipfile.ZIP_DEFLATED),
                ('wrapped.zip', 'outer/inner/wrapped.sav', b'wrapped', zipfile.ZIP_DEFLATED),
                ('stored.zip', 'stored.asd', b'stored', zipfile.ZIP_STORED),
            ):
                with zipfile.ZipFile(root / name, 'w', compression=method) as archive:
                    archive.writestr(path, data)
            with zipfile.ZipFile(root / 'discovery.zip', 'w', compression=zipfile.ZIP_DEFLATED) as archive:
                archive.writestr('outer/', b'')
                archive.writestr('outer/package/SaveData/', b'')
                archive.writestr('outer/package/SaveData/depth.asd', b'depth')
                archive.writestr('outer/readme.txt', b'ignored')
            binary = root / 'checks'
            subprocess.run([
                'swiftc', '-import-objc-header', str(ROOT / 'ios/Tests/SaveChecks-Bridging-Header.h'),
                str(ROOT / 'ios/App/SaveZip.swift'), str(ROOT / 'ios/App/SaveManager.swift'),
                str(ROOT / 'ios/App/GlobalSaveEditor.swift'),
                str(ROOT / 'ios/Tests/SaveManagerChecks.swift'), '-lz', '-o', str(binary),
            ], check=True)
            template = ROOT / 'project/app/src/main/res/raw/full_unlock_saves.zip'
            subprocess.run([str(binary), str(root), str(template)], check=True)
            subprocess.run(['unzip', '-t', str(root / 'interop.zip')], check=True)
            with zipfile.ZipFile(root / 'interop.zip') as archive:
                self.assertIsNone(archive.testzip())
                self.assertEqual(archive.namelist(), ['SaveData/slot.asd'])
                self.assertTrue(all(entry.flag_bits & 0x800 for entry in archive.infolist()))
            with zipfile.ZipFile(root / 'roundtrip.zip') as archive:
                self.assertIsNone(archive.testzip())
                self.assertIn('SaveData/日本語/测试.asd', archive.namelist())
            print('PASS: unzip -t and Python zipfile.testzip; Python deflate ZIP restored by Swift')


if __name__ == '__main__':
    unittest.main()
