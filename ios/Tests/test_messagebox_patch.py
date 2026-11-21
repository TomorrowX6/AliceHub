"""Check the SDL patch against downloaded sources; UIKit interaction needs a device."""
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
PATCH = ROOT / 'ios/patch-sdl-messagebox.cmake'
RELATIVE = Path('src/video/uikit/SDL_uikitmessagebox.m')


class MessageBoxPatchChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        sources = list((ROOT / 'ios/build').glob('*/SDL2-prefix/src/SDL2/' + str(RELATIVE)))
        if not sources:
            raise unittest.SkipTest('Build iOS native dependencies to download SDL first')
        cls.original = sources[0].read_text()
        recipe = PATCH.read_text()
        previous = re.search(r'set\(previous_inactive \[=\[(.*?)\]=\]\)', recipe, re.S)[1]
        current = re.search(r'set\(new_inactive \[=\[(.*?)\]=\]\)', recipe, re.S)[1]
        cls.original = cls.original.replace(previous, current)
        for name in ('wait', 'present'):
            old = re.search(r'set\(old_' + name + r' \[=\[(.*?)\]=\]\)', recipe, re.S)[1]
            new = re.search(r'set\(new_' + name + r' \[=\[(.*?)\]=\]\)', recipe, re.S)[1]
            cls.original = cls.original.replace(new, old)

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.source = self.directory / RELATIVE
        self.source.parent.mkdir(parents=True)

    def patch(self):
        return subprocess.run(['cmake', '-DSOURCE_DIR=' + str(self.directory), '-P', str(PATCH)],
                              capture_output=True, text=True)

    def test_scene_attachment_choice_mapping_and_idempotence(self):
        self.source.write_text(self.original)
        result = self.patch()
        self.assertEqual(result.returncode, 0, result.stderr)
        patched = self.source.read_text()
        self.assertIn('initWithWindowScene:scene', patched)
        self.assertNotIn('initWithFrame:[UIScreen mainScreen].bounds', patched)
        self.assertNotIn('beforeDate:[NSDate distantFuture]', patched)
        self.assertIn('[previousKeyWindow makeKeyWindow]', patched)
        self.assertIn('*buttonid = messageboxdata->buttons[clickedindex].buttonid;', patched)
        self.assertIn('SDL_MESSAGEBOX_BUTTON_ESCAPEKEY_DEFAULT', patched)
        self.assertIn('SDL_MESSAGEBOX_BUTTON_RETURNKEY_DEFAULT', patched)
        self.assertIn('SDL_MESSAGEBOX_BUTTONS_RIGHT_TO_LEFT', patched)
        self.assertIn('deadline = [NSDate timeIntervalSinceReferenceDate] + 5.0;\n                continue;', patched)
        self.assertEqual(self.patch().returncode, 0)
        self.assertEqual(self.source.read_text(), patched)

    def test_upgrade_previous_patch(self):
        self.source.write_text(self.original)
        self.assertEqual(self.patch().returncode, 0)
        current = self.source.read_text()
        recipe = PATCH.read_text()
        previous = re.search(r'set\(previous_inactive \[=\[(.*?)\]=\]\)', recipe, re.S)[1]
        updated = re.search(r'set\(new_inactive \[=\[(.*?)\]=\]\)', recipe, re.S)[1]
        self.source.write_text(current.replace(updated, previous))
        self.assertEqual(self.patch().returncode, 0)
        self.assertEqual(self.source.read_text(), current)

    def test_empty_or_unknown_upstream_source_fails_without_writing(self):
        for contents in ('', 'unexpected SDL version'):
            with self.subTest(contents=contents):
                self.source.write_text(contents)
                result = self.patch()
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('Unexpected SDL UIKit message box source', result.stderr)
                self.assertEqual(self.source.read_text(), contents)


if __name__ == '__main__':
    unittest.main()
