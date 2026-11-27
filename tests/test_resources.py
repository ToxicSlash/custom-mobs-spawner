import json
import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"

class ResourceTests(unittest.TestCase):
    def test_json_resources_parse(self):
        files = sorted(RES.rglob("*.json"))
        self.assertTrue(files)
        errors = []
        for path in files:
            try:
                json.loads(path.read_text(encoding="utf-8-sig"))
            except (ValueError, UnicodeError) as exc:
                errors.append(f"{path.relative_to(ROOT)}: {exc}")
        self.assertEqual([], errors)

    def test_namespace_is_not_ignored(self):
        for path in ("src/main/resources/assets/cmobs/lang/en_us.json",
                     "src/main/resources/data/cmobs/cmobs/player_skills/example.json",
                     "src/main/resources/cmobs/default_configs/example.json"):
            result = subprocess.run(["git", "check-ignore", "--no-index", path], cwd=ROOT,
                                    capture_output=True, text=True)
            self.assertEqual(1, result.returncode, result.stdout + result.stderr)

    def test_required_bundled_resources(self):
        missing = []
        for relative in ("assets/cmobs", "data/cmobs/cmobs/player_skills",
                         "data/cmobs/tags/items", "cmobs/default_configs"):
            path = RES / relative
            if not path.is_dir() or not any(p.is_file() for p in path.rglob("*")):
                missing.append(relative)
        self.assertEqual([], missing, "Recover authoritative resources; do not regenerate from tooltips")

if __name__ == "__main__":
    unittest.main()
