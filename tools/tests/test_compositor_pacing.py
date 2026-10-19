from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


@unittest.skipUnless(shutil.which('cc'), 'needs a C compiler')
class CompositorPacingTest(unittest.TestCase):
    def test_vsync_is_independent_of_event_loop_delays(self):
        source = (ROOT / 'app/src/main/cpp/waylandcomp/src/compositor.c').read_text()
        start = source.index('static void on_vsync(')
        end = source.index('\nstatic uint32_t now_ms(', start)
        with tempfile.TemporaryDirectory() as folder:
            work = Path(folder)
            (work / 'on_vsync_under_test.inc').write_text(source[start:end])
            binary = work / 'compositor-pacing'
            subprocess.run(['cc', '-std=c11', '-Wall', '-Wextra', '-Werror',
                            '-fsanitize=address,undefined', '-I' + str(work),
                            str(ROOT / 'tools/tests/compositor_pacing_harness.c'),
                            '-o', str(binary)], check=True, capture_output=True, text=True)
            result = subprocess.run([str(binary)], capture_output=True, text=True, timeout=10)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == '__main__':
    unittest.main()
