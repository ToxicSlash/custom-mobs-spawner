"""Read-only discovery of local inputs; copies only to the CI checkout."""
import hashlib
import sys
import io
import urllib.request
import urllib.parse
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
    override = (os.environ.get("ADADV_INSTANCE_PATH") or os.environ.get("ADADV_CURSEFORGE_INSTANCE") or "").strip()
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
    roots.extend((ROOT / "run/mods", ROOT / "ci-desktop/jars"))
    jars = {}
    for root in roots:
        if not root.is_dir():
            continue
        dirs = [root] if root.name.lower() in {"mods", "jars"} else [root / "mods"]
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
    print("CMOBS_DISCOVERY", len(roots) - 2, "source roots,", len(jars), "candidate JARs")
    return list(jars.values())

# Only exact release artifacts are accepted. Verify upstream SHA-512 and filename.
PROJECTS = {
    "bettercombat-fabric-1.9.0+1.20.1.jar": "better-combat",
    "rpgmana-1.1.0.jar": "rpg-mana",
    "amethyst_core-1.5.0+1.20.1.jar": "amethyst-core",
    "amethyst_imbuement-1.20.1-19.8.jar": "amethyst-imbuement",
    "fzzy_core-0.5.1+1.20.1.jar": "fzzy-core",
    "fzzy_config-0.7.6+1.20.1.jar": "fzzy-config",
    "Botania-1.20.1-453-FABRIC.jar": "botania",
    "trinkets-3.7.2.jar": "trinkets",
    "spell_engine-0.15.12+1.20.1.jar": "spell-engine",
    "spell_power-0.12.0+1.20.1.jar": "spell-power"
}

def public_exact(name, dest):
    slug = PROJECTS.get(name)
    if not slug:
        return None
    params = urllib.parse.urlencode({"loaders": '["fabric"]', "game_versions": '["1.20.1"]'})
    url = f"https://api.modrinth.com/v2/project/{slug}/version?{params}"
    try:
        request = urllib.request.Request(url, headers={"User-Agent": "ToxicSlash/custom-mobs-spawner CI"})
        with urllib.request.urlopen(request, timeout=25) as response:
            versions = json.load(response)
        matches = {f["hashes"]["sha512"]: f for v in versions for f in v["files"]
                   if f["filename"] == name and f.get("hashes", {}).get("sha512")}
        if len(matches) != 1:
            print("CMOBS_PUBLIC_EXACT_UNAVAILABLE", name, len(matches))
            return None
        expected, info = next(iter(matches.items()))
        with urllib.request.urlopen(info["url"], timeout=90) as response:
            data = response.read()
        if hashlib.sha512(data).hexdigest() != expected:
            raise RuntimeError("Upstream hash mismatch: " + name)
        target = dest / name
        target.write_bytes(data)
        print("CMOBS_PUBLIC_EXACT", name)
        return target
    except (OSError, ValueError) as exc:
        print("CMOBS_PUBLIC_LOOKUP_FAILED", name, type(exc).__name__)
        return None

def ids_in(jar):
    def walk(z):
        try:
            meta = json.loads(z.read("fabric.mod.json"))
        except KeyError:
            return set()
        ids = {meta["id"], *meta.get("provides", [])}
        for entry in meta.get("jars", []):
            with zipfile.ZipFile(io.BytesIO(z.read(entry["file"]))) as nested:
                ids.update(walk(nested))
        return ids
    with zipfile.ZipFile(jar) as z:
        return walk(z)

def export_runtime(jars, dest, report):
    # Transfer only compile inputs and the dependency closure of this mod, not the pack.
    target = OUT / "jars"
    target.mkdir(exist_ok=True)
    for name in report["compile"]:
        shutil.copy2(dest / name, target / name)
    records = [(p, metadata(p)) for p in jars]
    records = [(p, m) for p, m in records if m and m.get("id") not in {"cmobs", "automodpack", "smitherz"}]
    ids = {"minecraft", "java", "fabricloader"}
    required = set(json.loads((ROOT / "src/main/resources/fabric.mod.json").read_text())["depends"]) | {"botania", "trinkets", "rpgmana"}
    # Include installed optional integrations in the test-only export, with their closure.
    optional = {"iceandfire", "bettercombat", "amethyst_imbuement"}
    present = {m["id"] for p, m in records}
    required.update(optional & present)
    report["optional_integrations_present"] = sorted(optional & present)
    visited = set()
    while required - ids - visited:
        mid = sorted(required - ids - visited)[0]
        visited.add(mid)
        matches = [p for p, m in records if mid == m["id"] or mid in m.get("provides", [])]
        if not matches:
            matches = [p for p, m in records if m.get("jars") and mid in ids_in(p)]
        if not matches or len({sha(p) for p in matches}) != 1:
            continue
        jar = matches[0]
        if metadata(jar).get("environment") == "client":
            continue
        output = target / jar.name
        if output.exists() and sha(output) != sha(jar):
            raise RuntimeError("Conflicting export filename: " + jar.name)
        shutil.copy2(jar, output)
        ids.update(ids_in(jar))
        required.update(metadata(jar).get("depends", {}))
    report["runtime_export_missing"] = sorted(required - ids)
    report["exported"] = {p.name: sha(p) for p in target.glob("*.jar")}

def main():
    exporting = "--export" in sys.argv
    jars = discover()
    dest = ROOT / "run/mods"
    dest.mkdir(parents=True, exist_ok=True)
    report = {"compile": {}, "missing": [], "ambiguous": [], "resource_candidates": []}
    for name in NAMES:
        candidates = [p for p in jars if p.name == name]
        if not candidates and not exporting:
            downloaded = public_exact(name, dest)
            if downloaded:
                candidates = [downloaded]
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
    if exporting:
        export_runtime(jars, dest, report)
    (OUT / "dependency-manifest.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))
    if exporting:
        print("CMOBS_DESKTOP_DISCOVERY_COMPLETE")
        return
    if report["missing"] or report["ambiguous"]:
        raise SystemExit("Missing/ambiguous pinned JARs. Set CMOBS_MOD_SOURCE to the authoritative mod directory or instance.")
    print("CMOBS_COMPILE_INPUTS_READY")

if __name__ == "__main__":
    main()
