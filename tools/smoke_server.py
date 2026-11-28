"""Production Fabric server smoke test; no original world or config is touched."""
import io
import json
import os
from pathlib import Path
import queue
import re
import shutil
import subprocess
import tempfile
import sys
import threading
import time
import urllib.request
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
from prepare_ci import ROOT, OUT, discover, metadata, sha

def choose(paths, label):
    if not paths:
        raise RuntimeError("Missing server dependency: " + label)
    if len({sha(p) for p in paths}) != 1:
        raise RuntimeError("Ambiguous server dependency " + label + ": " + ", ".join(p.name for p in paths))
    return paths[0]

def provided(path):
    def walk(z):
        try:
            m = json.loads(z.read("fabric.mod.json"))
        except KeyError:
            return set()
        ids = {m["id"], *m.get("provides", [])}
        for entry in m.get("jars", []):
            with zipfile.ZipFile(io.BytesIO(z.read(entry["file"]))) as nested:
                ids.update(walk(nested))
        return ids
    with zipfile.ZipFile(path) as z:
        return walk(z)

def resolve(extra=(), client=False):
    built = choose([p for p in (ROOT / "build/libs").glob("*.jar")
                    if not p.name.endswith(("-sources.jar", "-dev.jar")) and metadata(p).get("id") == "cmobs"], "built cmobs")
    candidates = [(p, metadata(p)) for p in discover()]
    candidates = [(p, m) for p, m in candidates if m and (client or m.get("environment") != "client")
                  and m.get("id") not in {"cmobs", "automodpack", "smitherz"}]
    chosen = [built]
    ids = provided(built) | {"minecraft", "java", "fabricloader"}
    required = set(metadata(built).get("depends", {})) | {"botania", "trinkets", "rpgmana"} | set(extra)
    while required - ids:
        mid = sorted(required - ids)[0]
        choices = [p for p, m in candidates if mid == m.get("id") or mid in m.get("provides", [])]
        if not choices:
            choices = [p for p, m in candidates if m.get("jars") and mid in provided(p)]
        # The installed pack runtime is authoritative for compatibility; compile-only
        # Botania 453 may differ from the desktop runtime 455.
        if mid == "botania":
            desktop = [p for p in choices if p.parent == ROOT / "ci-desktop/jars"]
            if desktop:
                choices = desktop
        jar = choose(choices, mid)
        chosen.append(jar)
        ids.update(provided(jar))
        required.update(metadata(jar).get("depends", {}))
    return chosen

def main():
    ice = "--ice-and-fire" in sys.argv
    suffix = "-ice-and-fire" if ice else ""
    chosen = resolve(extra=("iceandfire",) if ice else ())
    (OUT / ("server-dependencies" + suffix + ".json")).write_text(json.dumps({p.name: sha(p) for p in chosen}, indent=2))
    with tempfile.TemporaryDirectory(prefix="cmobs-server-", dir=os.environ.get("RUNNER_TEMP")) as tmp:
        run = Path(tmp)
        (run / "mods").mkdir()
        for p in chosen:
            shutil.copy2(p, run / "mods" / p.name)
        (run / "eula.txt").write_text("eula=true\n")
        (run / "server.properties").write_text("server-ip=127.0.0.1\nserver-port=0\nonline-mode=true\nlevel-type=minecraft:flat\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\n")
        pack = run / "world/datapacks/cmobs_ci"
        fixture = pack / "data/cmobs_ci/cmobs/custom_mobs/probe.json"
        fixture.parent.mkdir(parents=True)
        (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "CMobs CI fixture"}}))
        fixture.write_text(json.dumps({"entity": "minecraft:zombie", "tags": ["cmobs_ci_probe"],
                                       "attributes": [{"id": "minecraft:generic.max_health", "base": 60}],
                                       "health": 60, "nbt": "{NoAI:1b,PersistenceRequired:1b}"}))
        def mob_fixture(name, entity, health, **extra):
            (fixture.parent / (name + ".json")).write_text(json.dumps({
                "entity": entity, "tags": ["cmobs_ci_" + name],
                "attributes": [{"id": "minecraft:generic.max_health", "base": health}],
                "health": health, "nbt": "{NoAI:1b,NoGravity:1b,PersistenceRequired:1b,Silent:1b}", **extra}))
        mob_fixture("equipment", "minecraft:zombie", 80,
                    equipment={"head": "minecraft:iron_helmet",
                               "mainhand": {"id": "minecraft:diamond_sword", "nbt": "{Unbreakable:1b}"}},
                    loot_table="minecraft:entities/cow", passengers=["cmobs_ci:passenger"])
        mob_fixture("passenger", "minecraft:chicken", 40)
        def data_fixture(folder, name, value):
            path = fixture.parent.parent / folder / (name + ".json")
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(value))
        mob_fixture("replacement", "minecraft:sheep", 42)
        mob_fixture("group_a", "minecraft:chicken", 33)
        mob_fixture("group_b", "minecraft:chicken", 34)
        data_fixture("replacements", "ci_rabbit", {"source": "minecraft:rabbit", "chance": 1,
                     "custom_mob": "cmobs_ci:replacement", "dimensions": ["minecraft:overworld"]})
        data_fixture("replacements", "ci_pig", {"source": "minecraft:pig", "chance": 1,
                     "group": "cmobs_ci:pair"})
        data_fixture("replacements", "ci_cow", {"source": "minecraft:cow", "chance": 1,
                     "custom_mob": "cmobs_ci:replacement", "dimensions": ["minecraft:the_nether"]})
        data_fixture("groups", "pair", {"members": [{"custom_mob": "cmobs_ci:group_a"},
                     {"custom_mob": "cmobs_ci:group_b", "x": 2}], "pools": []})
        species = {"fire_dragon": 173, "ice_dragon": 179, "lightning_dragon": 181,
                   "cyclops": 137, "troll": 143} if ice else {}
        for name, hp in species.items():
            mob_fixture(name, "iceandfire:" + name, hp)
        launcher = run / "server.jar"
        url = "https://meta.fabricmc.net/v2/versions/loader/1.20.1/0.18.6/1.0.3/server/jar"
        with urllib.request.urlopen(url, timeout=90) as response, launcher.open("wb") as f:
            shutil.copyfileobj(response, f)
        def launch():
            return subprocess.Popen(["java", "-Xmx2G", "-jar", str(launcher), "nogui"], cwd=run,
                                stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                text=True, encoding="utf-8", errors="replace", bufsize=1)
        proc = launch()
        messages = queue.Queue()
        def collect(name="server" + suffix + ".log"):
            with (OUT / name).open("w", encoding="utf-8") as log:
                for line in proc.stdout:
                    log.write(line)
                    log.flush()
                    print(line, end="", flush=True)
                    messages.put(line)
            messages.put(None)
        reader = threading.Thread(target=collect, daemon=True)
        reader.start()
        def wait(pattern, timeout=120):
            deadline = time.monotonic() + timeout
            while time.monotonic() < deadline:
                try:
                    line = messages.get(timeout=min(1, max(.01, deadline-time.monotonic())))
                except queue.Empty:
                    continue
                if line is None:
                    raise RuntimeError("Server exited waiting for " + pattern)
                if re.search(pattern, line):
                    return line
            raise RuntimeError("Timed out waiting for " + pattern)
        def cmd(command):
            proc.stdin.write(command + "\n")
            proc.stdin.flush()
        try:
            wait(r'Done \(', 600)
            cmd("gamerule doDaylightCycle false")
            wait(r"Gamerule doDaylightCycle is now set to: false")
            cmd("time set midnight")
            wait(r"Set the time")
            cmd("forceload add 0 0")
            wait(r"Marked chunk")
            cmd("csummon cmobs_ci:probe 0 100 0")
            wait(r"Summoned custom mob cmobs_ci:probe")
            cmd("execute if entity @e[tag=cmobs_ci_probe,limit=1] run say CMOBS_PROBE_PRESENT")
            wait(r"\[Server\] CMOBS_PROBE_PRESENT")
            cmd("attribute @e[tag=cmobs_ci_probe,limit=1] minecraft:generic.max_health base get")
            wait(r"(?i)base value.*60(?:\.0+)?\b")
            cmd("data get entity @e[tag=cmobs_ci_probe,limit=1] Health")
            wait(r"(?i)following entity data: 60(?:\.0+)?f")
            cmd("csummon cmobs_ci:equipment 2 100 2")
            wait(r"Summoned custom mob cmobs_ci:equipment")
            cmd("data get entity @e[tag=cmobs_ci_equipment,limit=1] HandItems[0].id")
            wait(r'following entity data: "minecraft:diamond_sword"')
            cmd("data get entity @e[tag=cmobs_ci_equipment,limit=1] HandItems[0].tag.Unbreakable")
            wait(r"following entity data: 1b")
            cmd("data get entity @e[tag=cmobs_ci_equipment,limit=1] ArmorItems[3].id")
            wait(r'following entity data: "minecraft:iron_helmet"')
            cmd("data get entity @e[tag=cmobs_ci_equipment,limit=1] DeathLootTable")
            wait(r'following entity data: "minecraft:entities/cow"')
            cmd("data get entity @e[tag=cmobs_ci_equipment,limit=1] Passengers[0].id")
            wait(r'following entity data: "minecraft:chicken"')
            cmd("attribute @e[tag=cmobs_ci_passenger,limit=1] minecraft:generic.max_health base get")
            wait(r"(?i)base value.*40(?:\.0+)?\b")
            print("CMOBS_EQUIPMENT_NBT_LOOT_PASSENGER_OK")
            def count(selector, expected):
                cmd("execute if entity " + selector)
                if expected == 0:
                    wait(r"Test failed", 20)
                else:
                    wait(rf"Test passed, count: {expected}\b", 20)
            cmd('summon minecraft:rabbit 6 100 6 {Tags:["cmobs_ci_source"],NoAI:1b,PersistenceRequired:1b,CustomName:\'{"text":"CI Rabbit"}\'}')
            wait(r"Summoned new", 20)
            count("@e[tag=cmobs_ci_source]", 0)
            count("@e[tag=cmobs_ci_replacement]", 1)
            cmd("attribute @e[tag=cmobs_ci_replacement,limit=1] minecraft:generic.max_health base get")
            wait(r"(?i)base value.*42(?:\.0+)?\b")
            cmd('summon minecraft:pig 6 100 8 {Tags:["cmobs_ci_group_source"],NoAI:1b,PersistenceRequired:1b}')
            wait(r"Summoned new", 20)
            count("@e[tag=cmobs_ci_group_source]", 0)
            count("@e[tag=cmobs_ci_group_a]", 1)
            count("@e[tag=cmobs_ci_group_b]", 1)
            cmd('summon minecraft:cow 6 100 10 {Tags:["cmobs_ci_wrong_dimension"],NoAI:1b,NoGravity:1b,PersistenceRequired:1b}')
            wait(r"Summoned new", 20)
            count("@e[tag=cmobs_ci_wrong_dimension]", 1)
            print("CMOBS_REPLACEMENT_GROUP_DIMENSION_OK")
            errors = []
            def check_species(name, hp, stage, health=None):
                target = f"@e[tag=cmobs_ci_{name},limit=1]"
                cmd(f"attribute {target} minecraft:generic.max_health base get")
                line = wait(r"(?i)base value.* is [\d.]+|No entity was found", 20)
                match = re.search(r" is ([\d.]+)", line)
                actual = float(match.group(1)) if match else None
                print("CMOBS_SPECIES_BASE", stage, name, "expected", hp, "actual", actual)
                if actual != hp:
                    errors.append(f"{stage} {name}: base {actual}, expected {hp}")
                if health is not None:
                    cmd(f"data get entity {target} Health")
                    line = wait(r"following entity data: [\d.]+f|No entity was found", 20)
                    match = re.search(r"following entity data: ([\d.]+)f", line)
                    actual = float(match.group(1)) if match else None
                    print("CMOBS_SPECIES_HEALTH", stage, name, "expected", health, "actual", actual)
                    # Trolls naturally regenerate; allow a bounded amount of real healing
                    # after saving/restarting, but never an unintended full health reset.
                    tolerance = 4.0 if name == "troll" and stage == "restart" else .01
                    if actual is None or actual < health - .01 or actual > health + tolerance:
                        errors.append(f"{stage} {name}: health {actual}, expected {health}")
            for index, (name, hp) in enumerate(species.items()):
                cmd(f"csummon cmobs_ci:{name} {index * 3} 120 0")
                wait(f"Summoned custom mob cmobs_ci:{name}", 20)
                check_species(name, hp, "spawn", hp)
            if species:
                time.sleep(12)
                for name, hp in species.items():
                    check_species(name, hp, "delayed", hp)
                # Verify an untagged dragon still follows native age scaling.
                cmd('summon iceandfire:fire_dragon 4 130 4 {Tags:["cmobs_ci_native"],NoAI:1b,NoGravity:1b,PersistenceRequired:1b,AgeTicks:24000}')
                wait(r"Summoned new Fire Dragon", 20)
                def native_base():
                    cmd("attribute @e[tag=cmobs_ci_native,limit=1] minecraft:generic.max_health base get")
                    line = wait(r"(?i)base value.* is [\d.]+", 20)
                    return float(re.search(r" is ([\d.]+)", line).group(1))
                native_young = native_base()
                cmd("data merge entity @e[tag=cmobs_ci_native,limit=1] {AgeTicks:2400000}")
                wait(r"Modified entity data", 20)
                native_old = native_base()
                print("CMOBS_IAF_NATIVE_AGE", native_young, native_old)
                if native_old <= native_young:
                    errors.append("Native dragon no longer scales with age")
                # Force IaF's NBT/age recalculation and an actual damaged save state.
                for name, hp in species.items():
                    target = f"@e[tag=cmobs_ci_{name},limit=1]"
                    if name.endswith("_dragon"):
                        cmd(f"data merge entity {target} {{AgeTicks:1200000,Health:{hp-11}.0f}}")
                    else:
                        cmd(f"data merge entity {target} {{Health:{hp-11}.0f}}")
                    wait(r"Modified entity data", 20)
                    check_species(name, hp, "nbt", hp-11)
                # A vanilla attribute modifier must survive custom base restoration.
                target = "@e[tag=cmobs_ci_fire_dragon,limit=1]"
                cmd(f"attribute {target} minecraft:generic.max_health modifier add 95280900-180b-4b91-849d-b7dc10293b5a ci_bonus 13 add")
                wait(r"Added modifier", 20)
                cmd(f"data merge entity {target} {{AgeTicks:1440000}}")
                wait(r"Modified entity data", 20)
                cmd(f"attribute {target} minecraft:generic.max_health get")
                line = wait(r"(?i)value of attribute.* is [\d.]+", 20)
                if not re.search(r" is 186(?:\.0+)?\s*$", line):
                    errors.append("Fire Dragon modifier lost: " + line.strip())
            cmd("reload")
            wait(r"(?i)reload(?:ing|ed)")
            cmd("attribute @e[tag=cmobs_ci_probe,limit=1] minecraft:generic.max_health base get")
            wait(r"(?i)base value.*60(?:\.0+)?\b")
            if species:
                # Troll's own regeneration is legitimate; set damaged health just before saving.
                cmd('data merge entity @e[tag=cmobs_ci_troll,limit=1] {Health:132.0f}')
                wait(r"Modified entity data", 20)
            cmd("save-all flush")
            wait(r"Saved the game")
            cmd("stop")
            if proc.wait(timeout=90):
                raise RuntimeError("Server did not exit cleanly")
            reader.join(timeout=10)
            print("CMOBS_SERVER_SUMMON_RELOAD_OK")
            proc = launch()
            messages = queue.Queue()
            reader = threading.Thread(target=collect, args=("server-restart" + suffix + ".log",), daemon=True)
            reader.start()
            wait(r'Done \(', 600)
            cmd("attribute @e[tag=cmobs_ci_probe,limit=1] minecraft:generic.max_health base get")
            wait(r"(?i)base value.*60(?:\.0+)?\b")
            cmd("data get entity @e[tag=cmobs_ci_probe,limit=1] Health")
            wait(r"(?i)following entity data: 60(?:\.0+)?f")
            count("@e[tag=cmobs_ci_replacement]", 1)
            count("@e[tag=cmobs_ci_group_a]", 1)
            count("@e[tag=cmobs_ci_group_b]", 1)
            count("@e[tag=cmobs_ci_wrong_dimension]", 1)
            print("CMOBS_REPLACEMENT_GROUP_RESTART_OK")
            for name, hp in species.items():
                check_species(name, hp, "restart", hp-11)
            cmd("stop")
            if proc.wait(timeout=90):
                raise RuntimeError("Restarted server did not exit cleanly")
            print("CMOBS_SERVER_RESTART_OK")
            if errors:
                raise AssertionError("Ice and Fire regression failures:\n" + "\n".join(errors))
            if species:
                print("CMOBS_IAF_SPECIES_ALL_OK", len(species))
        finally:
            if proc.poll() is None:
                proc.terminate()
                try:
                    proc.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    proc.kill()
                    proc.wait()
            reader.join(timeout=10)
            if (run / "crash-reports").is_dir():
                shutil.copytree(run / "crash-reports", OUT / "crash-reports", dirs_exist_ok=True)

if __name__ == "__main__":
    main()
