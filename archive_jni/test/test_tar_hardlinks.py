#!/usr/bin/env python3
"""Host JNI/libarchive regression tests. Requires a JDK, cc, and libarchive-dev.

Run: python3 archive_jni/test/test_tar_hardlinks.py
"""
import io
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = r'''
package io.github.kichikuou.xsystem4;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

public class ArchiveExtractor {
    private static native int nativeExtractFd(int fd, Object callback);
    public static class Callback {
        private final File root;
        private final File sourceRoot;
        Callback(File root, File sourceRoot) throws IOException {
            this.root = root.getCanonicalFile();
            this.sourceRoot = sourceRoot.getCanonicalFile();
        }
        public String onEntry(byte[] rawPath, long size) throws IOException {
            String name = new String(rawPath, StandardCharsets.UTF_8);
            File base = name.equals("original") ? sourceRoot : root;
            File out = new File(base, name).getCanonicalFile();
            if (!out.getPath().startsWith(base.getPath() + File.separator))
                throw new IOException("Entry escapes extraction root");
            out.getParentFile().mkdirs();
            return out.getPath();
        }
        public void onProgress(long bytes) {}
    }
    public static void main(String[] args) throws Exception {
        System.load(args[0]);
        try (FileInputStream in = new FileInputStream(args[1])) {
            Field fd = FileDescriptor.class.getDeclaredField("fd");
            fd.setAccessible(true);
            try {
                int count = nativeExtractFd(fd.getInt(in.getFD()), new Callback(
                    new File(args[2]), new File(args.length > 3 ? args[3] : args[2])));
                System.out.println("EXTRACTED " + count);
            } catch (IOException error) {
                System.out.println("ERROR " + error.getMessage());
                System.exit(2);
            }
        }
    }
}
'''


class TarHardlinksTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.build = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.build.cleanup)
        cls.build_dir = Path(cls.build.name)
        java_home = Path(shutil.which("javac")).resolve().parents[1]
        cls.library = cls.build_dir / "libarchive_jni.so"
        subprocess.run([
            "cc", "-shared", "-fPIC", "-Wall", "-Wextra", "-Werror",
            "-I" + str(java_home / "include"), "-I" + str(java_home / "include/linux"),
            str(ROOT / "archive_jni.c"), "-larchive", "-lpthread", "-o", str(cls.library),
        ], check=True)
        source = cls.build_dir / "ArchiveExtractor.java"
        source.write_text(JAVA)
        subprocess.run(["javac", "-d", str(cls.build_dir), str(source)], check=True)

    def extract(self, entries, root, source_root=None):
        archive = root / "fixture.tar"
        out = root / "out"
        out.mkdir(exist_ok=True)
        with tarfile.open(archive, "w", format=tarfile.USTAR_FORMAT) as tar:
            for name, content, target in entries:
                entry = tarfile.TarInfo(name)
                if target is not None:
                    entry.type = tarfile.LNKTYPE
                    entry.linkname = target
                    tar.addfile(entry)
                else:
                    entry.size = len(content)
                    tar.addfile(entry, io.BytesIO(content))
        result = subprocess.run([
            "java", "--add-opens=java.base/java.io=ALL-UNNAMED", "-cp", str(self.build_dir),
            "io.github.kichikuou.xsystem4.ArchiveExtractor", str(self.library), str(archive), str(out),
            str(source_root or out),
        ], capture_output=True, text=True, timeout=10)
        return result, out

    def test_cross_device_copy_fallback(self):
        if not Path("/dev/shm").is_dir():
            self.skipTest("No second filesystem available")
        with tempfile.TemporaryDirectory() as tmp, tempfile.TemporaryDirectory(dir="/dev/shm") as source:
            root, source_root = Path(tmp), Path(source)
            if root.stat().st_dev == source_root.stat().st_dev:
                self.skipTest("Temporary directories are on the same filesystem")
            content = b"cross-device payload\n" * 10000
            out = root / "out"
            out.mkdir()
            (out / "linked").write_bytes(b"stale content")
            for run in range(2):
                with self.subTest(run=run):
                    result, out = self.extract([
                        ("original", content, None), ("linked", None, "original"),
                    ], root, source_root)
                    self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                    self.assertIn("EXTRACTED 2", result.stdout)
                    self.assertEqual((out / "linked").read_bytes(), content)
                    self.assertNotEqual((out / "linked").stat().st_dev, (source_root / "original").stat().st_dev)
                    self.assertEqual(sorted(p.name for p in out.iterdir()), ["linked"])

    def test_repeated_extraction_overwrites_hard_link_destination(self):
        for stale in (False, True):
            with self.subTest(preexisting=stale), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                out = root / "out"
                out.mkdir()
                if stale:
                    (out / "linked").write_bytes(b"different content")
                content = b"target content\n"
                for run in range(2):
                    with self.subTest(run=run):
                        result, out = self.extract([
                            ("original", content, None), ("linked", None, "original"),
                        ], root)
                        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                        self.assertIn("EXTRACTED 2", result.stdout)
                        self.assertEqual((out / "linked").read_bytes(), content)
                        self.assertEqual((out / "original").read_bytes(), content)
                        self.assertEqual(sorted(p.name for p in out.iterdir()), ["linked", "original"])

    def test_directory_destination_is_not_replaced(self):
        for nonempty in (False, True):
            with self.subTest(nonempty=nonempty), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                destination = root / "out" / "linked"
                destination.mkdir(parents=True)
                if nonempty:
                    (destination / "keep").write_bytes(b"keep content")
                result, out = self.extract([
                    ("original", b"target", None), ("linked", None, "original"),
                ], root)
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertIn("Is a directory", result.stdout)
                self.assertTrue(destination.is_dir())
                if nonempty:
                    self.assertEqual((destination / "keep").read_bytes(), b"keep content")
                self.assertEqual(sorted(p.name for p in out.iterdir()), ["linked", "original"])

    def test_content_empty_and_chained_links(self):
        for content in (b"", b"hard link payload\n" * 100):
            with self.subTest(size=len(content)), tempfile.TemporaryDirectory() as tmp:
                result, out = self.extract([
                    ("dir/original", content, None),
                    ("linked", None, "./dir/original"),
                    ("chained", None, "linked"),
                ], Path(tmp))
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                self.assertIn("EXTRACTED 3", result.stdout)
                self.assertEqual((out / "linked").read_bytes(), content)
                self.assertEqual((out / "chained").read_bytes(), content)

    def test_escaping_targets_rejected(self):
        for target in ("../outside", "/etc/passwd", "dir/../../outside", "C:/outside", "..\\outside"):
            with self.subTest(target=target), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                (root / "outside").write_bytes(b"outside must not be linked")
                result, out = self.extract([("linked", None, target)], root)
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertIn("Invalid or escaping archive hard link target", result.stdout)
                self.assertFalse((out / "linked").exists())

    def test_missing_and_forward_targets_rejected(self):
        for later in ([], [("missing", b"later", None)]):
            with self.subTest(forward=bool(later)), tempfile.TemporaryDirectory() as tmp:
                result, out = self.extract([("linked", None, "missing")] + later, Path(tmp))
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertIn("target was not previously extracted", result.stdout)
                self.assertFalse((out / "linked").exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
