"""Check the Files document associations in the iOS bundle."""
from pathlib import Path
import plistlib
import unittest


class DocumentTypeChecks(unittest.TestCase):
    def test_rar_is_recognized_as_an_openable_archive(self):
        with (Path(__file__).resolve().parents[1] / "App" / "Info.plist").open("rb") as source:
            plist = plistlib.load(source)
        rar = "com.rarlab.rar-archive"
        declaration = next(item for item in plist["UTImportedTypeDeclarations"]
                           if item["UTTypeIdentifier"] == rar)
        self.assertIn("public.data", declaration["UTTypeConformsTo"])
        self.assertIn("public.archive", declaration["UTTypeConformsTo"])
        self.assertEqual(declaration["UTTypeTagSpecification"]["public.filename-extension"], "rar")
        self.assertTrue(any(rar in item["LSItemContentTypes"] for item in plist["CFBundleDocumentTypes"]))
        self.assertTrue(plist["LSSupportsOpeningDocumentsInPlace"])
