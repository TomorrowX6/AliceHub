"""Guard resource coverage for CJK source literals and common verbatim UI sinks."""

from pathlib import Path
import plistlib
import re
import unittest

APP = Path(__file__).resolve().parents[1] / "App"
LANGUAGES = ("zh-Hans", "ja", "en")
LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')
KEY = re.compile(LITERAL.pattern + r"\s*=")
CJK = re.compile(r"[\u3040-\u30ff\u3400-\u9fff\uac00-\ud7af\uf900-\ufaff]")
VERBATIM = re.compile(
    r"(?:Text\(verbatim:\s*|ModError\.message\(\s*|"
    r"\b(?:message|notice|error|status|progressText|importProgress|accessibilityLabel)\s*=\s*)$"
)

# Game titles, filenames, cheat commands and canonical metadata are not UI copy.
NON_UI = {
    "GameLibrary.swift": (
        r'^\s*\("[a-z0-9]+", \[|\b(?:displayName =|normalized\.contains\(|lower\.contains\("中文"\)|'
        r'regularNamesLower\.contains\(|lastPathComponent\.lowercased\(\) ==|let ainNames =|'
        r'replacingOccurrences\(of: "・")'
    ),
    "ModCatalog.swift": (
        r"\.contains\(|\.hasPrefix\(|\.hasSuffix\(|\.modReplacing\(|\.modMatch\(|"
        r"replacingOccurrences\(|\b(?:evenicle2Aliases|prefix|word|suffix|patch|full|modGroupRibuluo|modGroupBfyy)\b\s*(?:=|in)\s*\[?|"
        r"for \(words, label\) in|return \"v\" \+ version|static let supported"
    ),
    "ModOverlay.swift": r"\.contains\(|\.hasPrefix\(|\.replacingOccurrences\(|return \[|\.hasSuffix\(",
    "ModifierBridge.swift": r"\b(?:disableStreamerCodes|enableStreamerCode)\b|^\s*\(|ModifierGame\(",
    "OnlineGameCatalog.swift": r"\bSelf\(id:",
    "SaveEditorView.swift": r"name\.contains\(",
    "UncensoredPatch.swift": r"\bSelf\(id:|names\.contains\(",
}


def localized_keys(language):
    directory = APP / f"{language}.lproj"
    keys = set(KEY.findall((directory / "Localizable.strings").read_text(encoding="utf-8")))
    plurals = directory / "Localizable.stringsdict"
    if plurals.exists():
        keys.update(plistlib.loads(plurals.read_bytes()))
    return keys


def unlocalized_literals(path, source, keys):
    data_pattern = NON_UI.get(path.name)
    for number, line in enumerate(source.splitlines(), 1):
        if line.lstrip().startswith(("//", "/*", "*")):
            continue
        for literal in LITERAL.finditer(line):
            value = literal[1]
            if not CJK.search(value):
                continue
            if path.suffix == ".swift" and VERBATIM.search(line[:literal.start()]):
                yield f"{path.name}:{number}: look up {value!r} instead of displaying it verbatim"
            elif value not in keys and not (data_pattern and re.search(data_pattern, line)):
                yield f"{path.name}:{number}: no zh-Hans/ja/en localization for {value!r}"


class UILocalizationChecks(unittest.TestCase):
    def test_cjk_literals_have_translations(self):
        keys = set.intersection(*(localized_keys(language) for language in LANGUAGES))
        files = sorted(p for p in APP.rglob("*") if p.suffix in (".swift", ".m", ".c", ".h"))
        failures = [
            failure for path in files
            for failure in unlocalized_literals(path, path.read_text(encoding="utf-8"), keys)
        ]
        self.assertFalse(failures, "\n".join(failures))

    def test_guard_detects_new_copy_and_verbatim_use_of_existing_key(self):
        path = Path("ExampleView.swift")
        self.assertTrue(list(unlocalized_literals(path, 'Text("新文案")', set())))
        self.assertTrue(list(unlocalized_literals(path, 'message = "已安装"', {"已安装"})))
        self.assertTrue(list(unlocalized_literals(path, 'Text(verbatim: "已安装")', {"已安装"})))
        self.assertFalse(list(unlocalized_literals(path, 'message = hubText("已安装")', {"已安装"})))


if __name__ == "__main__":
    unittest.main()
