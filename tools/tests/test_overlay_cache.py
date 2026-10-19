from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


@unittest.skipUnless(shutil.which('cc'), 'needs a C compiler')
class OverlayCacheTest(unittest.TestCase):
    def test_cursor_moves_without_recopying_unchanged_content(self):
        source = (ROOT / 'app/src/main/cpp/waylandcomp/src/sc_layer.c').read_text()
        start = source.index('int sc_layer_present_overlay(')
        end = source.index('\nstatic void hide_layer(', start)
        with tempfile.TemporaryDirectory() as folder:
            work = Path(folder)
            (work / 'overlay_under_test.inc').write_text(source[start:end])
            binary = work / 'overlay-cache'
            result = subprocess.run(['cc', '-std=c11', '-Wall', '-Wextra', '-Werror',
                                     '-fsanitize=address,undefined', '-I' + str(work),
                                     str(ROOT / 'tools/tests/overlay_cache_harness.c'), '-o', str(binary)],
                                    capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            result = subprocess.run([str(binary)], capture_output=True, text=True, timeout=10)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == '__main__':
    unittest.main()
