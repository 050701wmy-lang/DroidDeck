from pathlib import Path
import runpy
import struct
import tempfile
import unittest
from unittest.mock import patch

HELPER = runpy.run_path(str(Path(__file__).resolve().parents[1] / "linuxfs/overlay/usr/local/bin/droiddeck-icu"))


class IcuRepairTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.game = self.root / "Albion Online/launcher"
        self.game.mkdir(parents=True)
        self.pack = self.root / "selected-esync-pack"
        self.wine = self.pack / "files/lib/wine/aarch64-windows"
        self.wine.mkdir(parents=True)
        (self.wine / "icu.dll").touch()
        self.command = [str(self.pack / "proton"), "waitforexitandrun", str(self.game / "AlbionLauncher.exe")]
        self.target = self.game / "icuuc.dll"
        self.required = ["ucnv_open", "ucnv_close"]
        self.available = set(self.required)

    def pe(self, path):
        test = self
        class Module:
            machine = 0x8664
            def imports(self, library):
                return test.required
            def exports(self):
                return test.available
        self.assertIn(Path(path), [self.game / "Qt6Core.dll", self.wine / "icu.dll"])
        return Module()

    def prepare(self):
        with patch.dict(HELPER["prepare"].__globals__, {"PE": self.pe}):
            return HELPER["prepare"](self.command)

    def test_selected_proton_exports_generate_a_loadable_alias(self):
        self.assertTrue(self.prepare())
        pe = HELPER["PE"](self.target)
        self.assertEqual(pe.machine, 0x8664)
        self.assertEqual(pe.exports(), set(self.required))
        directory = pe.offset(pe.u32(pe.directory))
        funcs = pe.offset(pe.u32(directory + 28))
        self.assertEqual({pe.string(pe.u32(funcs + i * 4)) for i in range(2)},
                         {"icu.ucnv_open", "icu.ucnv_close"})
        self.assertEqual(pe.u32(pe.directory + 8), 0)  # no imports or executable entry point
        self.assertEqual(pe.u32(0x98 + 16), 0)
        before = self.target.read_bytes()
        self.assertFalse(self.prepare())
        self.assertEqual(self.target.read_bytes(), before)

    def test_existing_game_library_is_preserved(self):
        self.target.write_bytes(b"game's native DLL")
        self.assertFalse(self.prepare())
        self.assertEqual(self.target.read_bytes(), b"game's native DLL")

    def test_proton_with_its_own_alias_needs_no_repair(self):
        (self.wine / "icuuc.dll").touch()
        self.assertFalse(self.prepare())
        self.assertFalse(self.target.exists())

    def test_missing_export_does_not_install_partial_fix(self):
        self.available.remove("ucnv_open")
        self.assertFalse(self.prepare())
        self.assertFalse(self.target.exists())

    def test_unrelated_game_and_non_launch_verbs_are_untouched(self):
        self.command[2] = str(self.game / "Other.exe")
        self.assertFalse(self.prepare())
        self.command[2] = str(self.game / "AlbionLauncher.exe")
        self.command[1] = "getcompatpath"
        self.assertFalse(self.prepare())
        self.assertFalse(self.target.exists())

    def test_qt_without_this_dependency_is_untouched(self):
        self.required = []
        self.assertFalse(self.prepare())
        self.assertFalse(self.target.exists())


if __name__ == "__main__":
    unittest.main()
