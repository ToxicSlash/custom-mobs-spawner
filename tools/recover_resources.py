"""Recover only absent CMobs resources from the installed ADADV 1.0.0 JAR.

The exact installed archive hash was observed in run 45972250562.
This is a source recovery operation, not a regeneration of gameplay data.
"""
import json
import os
from pathlib import Path, PurePosixPath
import subprocess
import zipfile

from prepare_ci import ROOT, OUT, discover, sha, metadata, PREFIXES

EXPECTED = "23f4f90f7392a3ba1a282b447229cf172c0c5a4011f1aa6e1a3bca25e6f47152"
RESOURCE_ROOT = ROOT / "src/main/resources"

def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()

def main():
    if os.environ.get("GITHUB_REF") != "refs/heads/dev":
        raise RuntimeError("Recovery is restricted to dev")
    if any((RESOURCE_ROOT / prefix).is_dir() for prefix in PREFIXES):
        print("CMOBS_RECOVERY_SKIPPED namespace already exists; never overwrite")
        return
    matches = [p for p in discover() if metadata(p).get("id") == "cmobs" and sha(p) == EXPECTED]
    if not matches:
        raise RuntimeError("Pinned installed CMobs JAR not available")
    jar = matches[0]
    entries = {}
    with zipfile.ZipFile(jar) as z:
        for info in z.infolist():
            name = info.filename
            if info.is_dir() or not name.startswith(PREFIXES):
                continue
            parts = PurePosixPath(name).parts
            if ".." in parts or "\\" in name or ":" in name or name in entries:
                raise RuntimeError("Unsafe or duplicate archive resource")
            data = z.read(info)
            if name.endswith(".json"):
                json.loads(data)
            entries[name] = data
    if len(entries) != 272:
        raise RuntimeError(f"Unexpected source inventory: {len(entries)}")
    for prefix in ("assets/cmobs/", "data/cmobs/cmobs/player_skills/gem_skills/",
                   "data/cmobs/tags/items/", "cmobs/default_configs/"):
        if not any(n.startswith(prefix) for n in entries):
            raise RuntimeError("Missing required recovery family: " + prefix)
    for name, data in entries.items():
        path = RESOURCE_ROOT / name
        if path.exists():
            raise RuntimeError("Refusing to overwrite " + name)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    manifest = {"source": jar.name, "sha256": EXPECTED, "count": len(entries), "paths": sorted(entries)}
    (OUT / "resource-recovery.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    # Only these explicit source paths may enter the commit.
    subprocess.run(["git", "add", "--", *["src/main/resources/" + p.rstrip("/") for p in PREFIXES]], cwd=ROOT, check=True)
    staged = git("diff", "--cached", "--name-only").splitlines()
    if set(staged) != {"src/main/resources/" + n for n in entries}:
        raise RuntimeError("Staged files do not exactly match recovery inventory")
    subprocess.run(["git", "-c", "user.name=github-actions[bot]", "-c",
                    "user.email=41898282+github-actions[bot]@users.noreply.github.com",
                    "commit", "-m", "Recover original CMobs namespace resources from installed ADADV JAR"], cwd=ROOT, check=True)
    # Non-force push rejects if any concurrent work advanced dev.
    subprocess.run(["git", "push", "origin", "HEAD:refs/heads/dev"], cwd=ROOT, check=True)
    print("CMOBS_RESOURCES_RECOVERED", len(entries), git("rev-parse", "HEAD"))

if __name__ == "__main__":
    main()
