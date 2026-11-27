"""Read-only discovery of local inputs; copies only to the CI checkout."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "ci-output"
OUT.mkdir(exist_ok=True)
NAMES = re.findall(r'files\("run/mods/([^"]+\.jar)"\)', (ROOT / "build.gradle").read_text())
PREFIXES = ("assets/cmobs/", "data/cmobs/", "cmobs/default_configs/")
def sha(path):
    with path.open("rb") as f:
        return hashlib.file_digest(f, "sha256").hexdigest()

def metadata(path):
    try:
        with zipfile.ZipFile(path) as z:
            return json.loads(z.read("fabric.mod.json"))
    except (KeyError, ValueError, zipfile.BadZipFile):
        return {}

def discover():
    configured = os.environ.get("CMOBS_MOD_SOURCE", "").strip()
    override = os.environ.get("ADADV_CURSEFORGE_INSTANCE", "").strip()
    roots = [Path(configured)] if configured else []
    if not configured:
        if override:
            roots.append(Path(override))
        else:
            # DevHub background service may run under a different Windows account.
            drive = Path(os.environ.get("SystemDrive", "C:") + "/")
            profiles = {Path.home(), Path(os.environ.get("PUBLIC", drive / "Users/Public")).parent}
            try:
                profiles.update(p for p in (drive / "Users").iterdir() if p.is_dir())
            except OSError:
                pass
            parents = {drive / "CurseForge/Minecraft/Instances"}
            for profile in profiles:
                parents.update(profile / part for part in (
                    "curseforge/minecraft/Instances", "Documents/Curse/Minecraft/Instances",
                    "Documents/CurseForge/Minecraft/Instances", "CurseForge/Minecraft/Instances"))
            matches = set()
            for parent in parents:
                try:
                    matches.update(p.resolve() for p in parent.iterdir()
                                   if p.name.casefold() == "adadv-2.0" and (p / "mods").is_dir())
                except OSError:
                    continue
            if len(matches) > 1:
                raise RuntimeError("Multiple ADADV-2.0 instances; set CMOBS_MOD_SOURCE to the intended instance.")
            roots.extend(matches)
    roots.append(ROOT / "run/mods")
    jars = {}
    for root in roots:
        if not root.is_dir():
            continue
        dirs = [root] if root.name.lower() == "mods" else [root / "mods"]
        for folder in ("automodpack", ".automodpack"):
            base = root / folder
            dirs.append(base / "mods")
            try:
                dirs.extend(p / "mods" for p in (base / "modpacks").iterdir())
            except OSError:
                pass
        # A separately configured dependency directory may contain JARs directly.
        if configured and root == Path(configured):
            dirs.append(root)
        for directory in dirs:
            try:
                for p in directory.glob("*.jar"):
                    jars[str(p.resolve())] = p
            except OSError:
                continue
    print("CMOBS_DISCOVERY", len(roots) - 1, "source roots,", len(jars), "candidate JARs")
    return list(jars.values())

def main():
    jars = discover()
    dest = ROOT / "run/mods"
    dest.mkdir(parents=True, exist_ok=True)
    report = {"compile": {}, "missing": [], "ambiguous": [], "resource_candidates": []}
    for name in NAMES:
        candidates = [p for p in jars if p.name == name]
        if not candidates:
            report["missing"].append(name)
            continue
        if len({sha(p) for p in candidates}) != 1:
            report["ambiguous"].append(name)
            continue
        src = candidates[0]
        target = dest / name
        if src.resolve() != target.resolve():
            shutil.copy2(src, target)
        report["compile"][name] = sha(target)
    seen = set()
    for p in jars:
        m = metadata(p)
        if m.get("id") != "cmobs":
            continue
        h = sha(p)
        if h in seen:
            continue
        seen.add(h)
        with zipfile.ZipFile(p) as z:
            entries = [n for n in z.namelist() if n.startswith(PREFIXES) and not n.endswith("/") and ".." not in n.split("/")]
            report["resource_candidates"].append({"file": p.name, "version": m.get("version"), "sha256": h, "resources": len(entries)})
            if entries:
                with zipfile.ZipFile(OUT / ("resource-candidate-" + h[:16] + ".zip"), "w", zipfile.ZIP_DEFLATED) as recovered:
                    for n in entries:
                        recovered.writestr(n, z.read(n))
    (OUT / "dependency-manifest.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))
    if report["missing"] or report["ambiguous"]:
        raise SystemExit("Missing/ambiguous pinned JARs. Set CMOBS_MOD_SOURCE to the authoritative mod directory or instance.")
    print("CMOBS_COMPILE_INPUTS_READY")

if __name__ == "__main__":
    main()
