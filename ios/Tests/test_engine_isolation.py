"""Audit staged Mach-O engines after building both iOS SDKs."""
from pathlib import Path
from fnmatch import fnmatchcase
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]
EXPORTS = {
    'xsystem4': {'_xsystem4_main', '_alicehub_ios_prepare_window',
                 '_alicehub_ios_bind_drawable', '_alicehub_ios_set_background_hook'},
    'xsystem35': {'_xsystem35_main'},
    'system3': {'_system3_main'},
}


class EngineIsolationChecks(unittest.TestCase):
    def test_exports_and_shared_dependencies(self):
        for sdk in ('iphonesimulator', 'iphoneos'):
            lib = ROOT / 'ios/build' / sdk / 'stage/lib'
            for engine, expected in EXPORTS.items():
                with self.subTest(sdk=sdk, engine=engine):
                    obj = lib / (engine + '_engine.o')
                    self.assertTrue(obj.is_file(), 'Build native libraries first')
                    output = subprocess.check_output(['nm', '-gU', str(obj)], text=True)
                    actual = {line.split()[-1] for line in output.splitlines()}
                    allowed = expected.copy()
                    if engine == 'xsystem4':
                        allowed.update({'_alicehub_ios_set_gamepad_callbacks',
                                        '_alicehub_ios_gamepad_command', '_alicehub_ios_gamepad_frame'})
                        allowed.update((ROOT / 'ios/engine-exports.txt').read_text().splitlines())
                    self.assertTrue(expected <= actual)
                    self.assertTrue(all(any(fnmatchcase(symbol, pattern) for pattern in allowed)
                                        for symbol in actual), actual - allowed)
                    undefined = subprocess.check_output(['nm', '-u', str(obj)], text=True)
                    self.assertIn('_SDL_Init', undefined)
                    self.assertIn('_FT_Init_FreeType', undefined)
                    self.assertNotIn('_sys_exit\n', undefined)
                    if engine == 'xsystem35':
                        self.assertIn('_Mix_OpenAudioDevice', undefined)
                    archive = lib / ('lib' + engine + '_engine.a')
                    members = subprocess.check_output(['ar', '-t', str(archive)], text=True)
                    objects = [name for name in members.splitlines()
                               if not name.startswith('__.SYMDEF')]
                    self.assertEqual(objects, [obj.name])


if __name__ == '__main__':
    unittest.main()
