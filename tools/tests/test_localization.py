"""Guard Chinese resource coverage and Android format arguments during upstream merges."""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

RES = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "res"
FORMAT = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z%])")


def resources(folder):
    result = {}
    for path in sorted((RES / folder).glob("*.xml")):
        for node in ET.parse(path).getroot():
            if node.tag not in {"string", "string-array", "plurals"}:
                continue
            key = (node.tag, node.attrib["name"])
            if key in result:
                raise AssertionError(f"Duplicate resource {key} in {path}")
            result[key] = node
    return result


def formats(text):
    return sorted((position or "1", kind) for position, kind in FORMAT.findall(text)
                  if kind not in {"%", "n"})


class LocalizationTest(unittest.TestCase):
    def test_chinese_resources_and_arguments(self):
        english, chinese = resources("values"), resources("values-zh-rCN")
        expected = {key for key, node in english.items()
                    if node.attrib.get("translatable") != "false"}
        self.assertFalse(set(chinese) - set(english), "Chinese resources removed upstream")
        self.assertFalse(expected - set(chinese), "Missing Chinese translations")
        for key in sorted(expected):
            source, target = english[key], chinese[key]
            with self.subTest(resource=key):
                if source.tag == "string":
                    pairs = [(source, target)]
                elif source.tag == "plurals":
                    source_items = {n.attrib["quantity"]: n for n in source}
                    self.assertIn("other", {n.attrib["quantity"] for n in target})
                    pairs = [(source_items.get(n.attrib["quantity"], source_items["other"]), n)
                             for n in target]
                else:
                    self.assertEqual(len(source), len(target))
                    pairs = zip(source, target)
                for a, b in pairs:
                    translated = "".join(b.itertext())
                    self.assertTrue(translated.strip())
                    if a.attrib.get("formatted", source.attrib.get("formatted")) != "false":
                        self.assertEqual(formats("".join(a.itertext())), formats(translated))

    def test_language_is_declared(self):
        languages = ET.parse(RES / "xml" / "locales_config.xml").getroot()
        self.assertIn("zh-CN", {n.attrib["{http://schemas.android.com/apk/res/android}name"]
                             for n in languages})


if __name__ == "__main__":
    unittest.main()
