from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


@unittest.skipUnless(shutil.which('cc'), 'needs a C compiler')
class PresentHistoryTest(unittest.TestCase):
    def test_displayed_frames_and_frame_times(self):
        with tempfile.TemporaryDirectory() as folder:
            binary = Path(folder) / 'present-history'
            subprocess.run(['cc', '-std=c11', '-Wall', '-Wextra', '-Werror',
                            '-fsanitize=address,undefined',
                            str(ROOT / 'tools/tests/present_history_harness.c'),
                            '-lm', '-o', str(binary)], check=True, capture_output=True, text=True)
            result = subprocess.run([str(binary)], capture_output=True, text=True, timeout=10)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == '__main__':
    unittest.main()
