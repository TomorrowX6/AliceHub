import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]


class ModifierSaveKeysTest(unittest.TestCase):
    def test_swift_editable_keys_match_native_writer(self):
        source = (ROOT / "xsystem4/src/modifier.c").read_text()
        writer = re.search(r"^static bool write_local_save_mod_str\(.*?^}$", source, re.S | re.M).group(0)
        native = set(re.findall(r'\(mod, "([A-Za-z0-9]+)"', writer))
        bridge = (ROOT / "ios/App/ModifierBridge.swift").read_text()
        block = re.search(r"writableSaveKeys: Set<String> = \[(.*?)\]", bridge, re.S).group(1)
        self.assertEqual(set(re.findall(r'"([A-Za-z0-9]+)"', block)), native)


if __name__ == "__main__":
    unittest.main()
