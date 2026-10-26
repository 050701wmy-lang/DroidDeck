import json
from pathlib import Path
import runpy
import subprocess
import tempfile
import unittest

BIN = Path(__file__).resolve().parents[1] / "linuxfs/overlay/usr/local/bin"
LIVE = runpy.run_path(str(BIN / "droiddeck-steam-compat"))


class DeviceControlsTest(unittest.TestCase):
    def test_native_brightness_notifications_do_not_echo_android_readback(self):
        harness = r"""
const assert = require('assert');
globalThis.window = globalThis;
const requests = [], callbacks = [];
let current = 1;
window.droiddeckBrightnessChanged = value => requests.push(Number(value));
globalThis.SteamClient = { System: { Display: {
  RegisterForBrightnessChanges(cb) { callbacks.push(cb); cb({ flBrightness: 1 }); return {}; },
  SetBrightness(value) {
    if (current === value) return;
    current = value;
    callbacks.forEach(cb => cb({ flBrightness: value }));
  }
} } };
const script = require('fs').readFileSync(process.argv[2], 'utf8');
assert.strictEqual(eval(script), 'installed');
assert.strictEqual(eval(script), 'present');
const bridge = window.__droiddeckBrightness;
bridge.set(0.5);
bridge.set(0.5);
assert.deepStrictEqual(requests, []);
callbacks.forEach(cb => cb({ flBrightness: 0.8 }));
assert.deepStrictEqual(requests, [0.8]);
bridge.set(0.7992126);
assert.deepStrictEqual(requests, [0.8]);
for (const value of [NaN, Infinity, -1, 2, '0.5']) callbacks.forEach(cb => cb({ flBrightness: value }));
assert.deepStrictEqual(requests, [0.8]);
assert.strictEqual(callbacks.length, 1);
"""
        with tempfile.TemporaryDirectory() as directory:
            script = Path(directory) / "bridge.js"
            script.write_text(LIVE["BRIGHTNESS_JS"])
            test = Path(directory) / "test.js"
            test.write_text(harness)
            result = subprocess.run(["node", str(test), str(script)], capture_output=True, text=True, timeout=20)
            self.assertEqual(result.returncode, 0, result.stderr)

    def test_requests_reject_nonfinite_and_out_of_range_values(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "brightness.request"
            for value in ["NaN", "Infinity", "-0.1", "1.1", "invalid", None]:
                LIVE["brightness_request"](value, directory)
                self.assertFalse(path.exists())
            LIVE["brightness_request"]("0.4", directory)
            self.assertEqual(float(path.read_text()), 0.4)

    def test_failed_request_revision_republishes_the_actual_brightness(self):
        with tempfile.TemporaryDirectory() as directory:
            globals_ = LIVE["Helper"].sync_controls.__globals__
            previous = globals_["CONTROLS_DIR"]
            try:
                globals_["CONTROLS_DIR"] = directory
                helper = LIVE["Helper"]("/steam", 0)
                calls = []
                helper.page = type("Page", (), {"evaluate": lambda self, expression: calls.append(expression)})()
                path = Path(directory) / "state.json"
                path.write_text(json.dumps({"brightness": 0.5, "revision": 1}))
                helper.sync_controls()
                helper.sync_controls()
                self.assertEqual(len(calls), 1)
                path.write_text(json.dumps({"brightness": 0.5, "revision": 2}))
                helper.sync_controls()
                self.assertEqual(len(calls), 2)
            finally:
                globals_["CONTROLS_DIR"] = previous


if __name__ == "__main__":
    unittest.main()
