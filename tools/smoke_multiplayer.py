"""Two real graphical clients on an isolated loopback dedicated server."""
import os
import json
from pathlib import Path
import queue
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import threading
import time
import urllib.request

sys.path.insert(0, str(Path(__file__).resolve().parent))
from smoke_server import ROOT, OUT, resolve
from smoke_client import gradle, execute
from prepare_ci import metadata

def main():
    evidence = OUT / "multiplayer"
    evidence.mkdir(parents=True, exist_ok=True)
    chosen = resolve(extra=("bettercombat", "amethyst_imbuement"), client=True)
    with tempfile.TemporaryDirectory(prefix="cmobs-mp-", dir=os.environ["RUNNER_TEMP"], ignore_cleanup_errors=True) as tmp:
        root = Path(tmp)
        coord, mods, server_dir = root / "coord", root / "mods", root / "server"
        for p in (coord, mods, server_dir):
            p.mkdir()
        for jar in chosen:
            shutil.copy2(jar, mods / jar.name)
        project = root / "probe"
        shutil.copytree(ROOT / "tools/production-client", project, ignore=shutil.ignore_patterns("build", ".gradle"))
        binary = gradle()
        base = [str(binary), "--no-daemon", "--console=plain"]
        def gradle_cmd(project, run):
            return base + ["-p", str(project), "-PcmobsRuntimeMods=" + str(mods), "-PcmobsClientRunDir=" + str(run)]
        execute(gradle_cmd(project, root / "unused") + ["remapJar"], evidence / "probe-build.log", 420)
        probes = [p for p in (project / "build/libs").glob("*.jar")
                  if not p.name.endswith(("-dev.jar", "-sources.jar")) and metadata(p).get("id") == "cmobs_ci_client_probe"]
        if len(probes) != 1:
            raise RuntimeError("Expected exactly one production probe")
        shutil.copy2(probes[0], mods / probes[0].name)
        shutil.copytree(mods, server_dir / "mods")
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            port = sock.getsockname()[1]
        (server_dir / "eula.txt").write_text("eula=true\n")
        (server_dir / "server.properties").write_text(
            f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nlevel-type=minecraft:flat\n"
            "view-distance=4\nsimulation-distance=3\nspawn-protection=0\n")
        pack = server_dir / "world/datapacks/cmobs_ci_mp"
        datapath = pack / "data/cmobs_ci_mp/cmobs"
        datapath.mkdir(parents=True)
        (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "CI multiplayer spawn fixtures"}}))
        def fixture(folder, name, value):
            p = datapath / folder / (name + ".json")
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(json.dumps(value))
        fixture("custom_mobs", "cap", {"entity": "minecraft:chicken", "tags": ["cmobs_ci_cap"],
                                       "nbt": "{NoAI:1b,PersistenceRequired:1b}"})
        fixture("spawn_rules", "cap", {"custom_mob": "cmobs_ci_mp:cap", "extra_spawn": True,
                                      "chance": 1, "interval_ticks": 5, "attempts_per_player": 12,
                                      "min_distance": 12, "max_distance": 22, "mob_cap": 1,
                                      "blocks": ["minecraft:grass_block"], "dimensions": ["minecraft:overworld"],
                                      "obey_do_mob_spawning": True})
        fixture("custom_mobs", "stand", {"entity": "minecraft:chicken", "tags": ["cmobs_ci_stand_result"],
                                         "nbt": "{NoAI:1b,PersistenceRequired:1b}"})
        fixture("spawn_stands", "stand", {"tag": "cmobs_ci_spawn_stand", "custom_mob": "cmobs_ci_mp:stand",
                                         "chance": 1, "required_items": [{"item": "minecraft:gold_ingot",
                                         "count": 2, "distance": 2, "consume": True}]})
        # Explicit skill tags on only one player; the peer is an untagged control.
        for name, trigger, effect, target in (
                ("melee", "when_attacks", "speed", "self"),
                ("ranged", "on_ranged_hit", "strength", "self"),
                ("miss", "failed_attack", "haste", "self"),
                ("cast", "when_spell_cast", "resistance", "target")):
            definition = {"trigger": trigger, "chance": 1, "actions": [
                {"type": "effect", "id": "minecraft:" + effect, "duration": 1200,
                 "target": target, "stack": True, "max_amplifier": 10}]}
            if name == "cast":
                definition["spell"] = "cmobs_ci_mp:spell_test"
            fixture("skills", name, definition)
        fixture("custom_mobs", "skill_target", {"entity": "minecraft:sheep",
                "tags": ["cmobs_ci_skill_target"], "health": 100,
                "attributes": [{"id": "minecraft:generic.max_health", "base": 100}],
                "nbt": "{NoAI:1b,NoGravity:1b,PersistenceRequired:1b}"})
        launcher = server_dir / "server.jar"
        with urllib.request.urlopen("https://meta.fabricmc.net/v2/versions/loader/1.20.1/0.18.6/1.0.3/server/jar", timeout=90) as r, launcher.open("wb") as file:
            shutil.copyfileobj(r, file)
        processes, logs, client_runs = [], [], []
        messages = queue.Queue()
        server = subprocess.Popen(["java", "-Xmx2G", "-Dcmobs.ci.posture=" + str(coord), "-jar", str(launcher), "nogui"],
                                  cwd=server_dir, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                  text=True, encoding="utf-8", errors="replace", bufsize=1)
        processes.append(server)
        def collect():
            with (evidence / "server.log").open("w", encoding="utf-8") as log:
                for line in server.stdout:
                    log.write(line)
                    log.flush()
                    messages.put(line)
            messages.put(None)
        reader = threading.Thread(target=collect, daemon=True)
        reader.start()
        def wait_log(pattern, timeout=60):
            deadline = time.monotonic() + timeout
            while time.monotonic() < deadline:
                try:
                    line = messages.get(timeout=1)
                except queue.Empty:
                    continue
                if line is None:
                    raise RuntimeError("Server quit before " + pattern)
                if "CMOBS_PROBE_FAILURE " in line:
                    raise RuntimeError(line.strip())
                match = re.search(pattern, line)
                if match:
                    return match
            raise RuntimeError("Server marker missing: " + pattern)
        def cmd(command):
            server.stdin.write(command + "\n")
            server.stdin.flush()
        def wait_files(paths, timeout):
            deadline = time.monotonic() + timeout
            while time.monotonic() < deadline:
                if all(p.is_file() for p in paths):
                    return
                if any(p.poll() is not None for p in processes):
                    raise RuntimeError("A fixture process exited prematurely")
                time.sleep(.25)
            raise RuntimeError("Missing evidence: " + ", ".join(p.name for p in paths if not p.is_file()))
        def truth(target, action):
            cmd(f"cmobs_ci_posture {target} {action}")
            match = wait_log(r"CMOBS_POSTURE_TRUTH " + target + r" (\d+) (true|false) (true|false)")
            return int(match[1]), match[2], match[3]
        def phase(label, target, state, selfbar=True, view="back", remote=True):
            percent, visible, staggered = state
            content = "|".join([label, target, str(percent), visible, staggered, str(selfbar).lower(), view, str(remote).lower()])
            temp = coord / "phase.tmp"
            temp.write_text(content, encoding="utf-8")
            os.replace(temp, coord / "phase.txt")
            wait_files([coord / f"{label}-{name}.ok" for name in ("CMobsA", "CMobsB")], 25)
            print("CMOBS_MULTIPLAYER_PHASE_OK", content)
        try:
            wait_log(r"CMOBS_POSTURE_SERVER_READY", 180)
            cmd("gamerule doMobSpawning false")
            cmd("gamerule doDaylightCycle false")
            cmd("time set day")
            for name in ("CMobsA", "CMobsB"):
                p, run = root / ("p-" + name), root / ("game-" + name)
                shutil.copytree(ROOT / "tools/production-client", p, ignore=shutil.ignore_patterns("build", ".gradle"))
                run.mkdir()
                (run / "options.txt").write_text("version:3465\nonboardAccessibility:false\npauseOnLostFocus:false\nfullscreen:false\ntutorialStep:none\n")
                client_runs.append((name, run))
                for attempt in range(2):
                    output = (evidence / (name + (".log" if attempt == 0 else "-retry.log"))).open("w", encoding="utf-8")
                    logs.append(output)
                    proc = subprocess.Popen(gradle_cmd(p, run) + [
                        "-PcmobsUsername=" + name, f"-PcmobsMultiplayer=127.0.0.1:{port}",
                        "-PcmobsPostureCoordinator=" + str(coord), "runCmobsClient"],
                        cwd=ROOT, stdout=output, stderr=subprocess.STDOUT)
                    processes.append(proc)
                    # Retry ONLY the verified pre-join null-profile handshake failure.
                    # No gameplay phase or assertion is eligible for retry.
                    result = wait_log(name + r" joined the game|id=<null>,name=" + name + r",.*lost connection: Disconnected", 300)
                    if "joined the game" in result[0]:
                        break
                    if attempt or server.poll() is not None or (coord / "phase.txt").exists():
                        raise RuntimeError("Pre-join handshake failed: " + result[0])
                    print("CMOBS_PREJOIN_RETRY", name, result[0], flush=True)
                    if proc.poll() is None:
                        subprocess.run(["taskkill", "/PID", str(proc.pid), "/T", "/F"],
                                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30)
                        proc.wait(timeout=30)
                    processes.remove(proc)
                    output.close()
                    if (run / "logs/latest.log").is_file():
                        shutil.copy2(run / "logs/latest.log", evidence / (name + "-prejoin-first.log"))
                    time.sleep(2)
            wait_files([coord / (name + ".ready") for name in ("CMobsA", "CMobsB")], 90)
            # Flat worlds in 1.20.1 start at -64; build a known platform
            # instead of assuming y=4 is ground. Keep combat damage enabled.
            cmd("fill -8 -61 -8 8 -61 8 minecraft:stone")
            wait_log(r"Successfully filled")
            cmd("tp CMobsA 0 -60 0 -90 0")
            cmd("tp CMobsB 3 -60 0 90 0")
            phase("idle", "CMobsB", truth("CMobsB", "snapshot"))
            partial = truth("CMobsB", "partial")
            if not 0 < partial[0] < 100 or partial[1:] != ("true", "false"):
                raise AssertionError("Unexpected partial posture: " + str(partial))
            phase("partial-first", "CMobsB", partial, view="first")
            phase("partial-back", "CMobsB", partial, view="back")
            cmd("tp CMobsA -4 -60 0 -90 0")
            cmd("tp CMobsB 4 -60 0 90 0")
            phase("partial-front", "CMobsB", partial, view="front")
            cmd("tp CMobsA 0 -60 0 -90 0")
            cmd("tp CMobsB 3 -60 0 90 0")
            phase("partial-inventory", "CMobsB", partial, view="inventory")
            phase("self-toggle-off", "CMobsB", truth("CMobsB", "hideSelf"), False, "back")
            phase("self-toggle-on", "CMobsB", truth("CMobsB", "showSelf"), True, "back")
            other = truth("CMobsA", "partial")
            if not 0 < other[0] < 100:
                raise AssertionError("Role reversal failed")
            phase("reversed", "CMobsA", other)
            broken = truth("CMobsB", "stagger")
            if broken != (0, "true", "true"):
                raise AssertionError("Expected broken posture, got " + str(broken))
            phase("stagger", "CMobsB", broken)
            cmd("team add ci_hidden")
            cmd("team modify ci_hidden nametagVisibility never")
            cmd("team join ci_hidden CMobsB")
            phase("hidden-name", "CMobsB", broken, view="back", remote=False)
            cmd("team leave CMobsB")
            phase("visible-again", "CMobsB", broken)
            print("CMOBS_MULTIPLAYER_POSTURE_PHASES_OK")
            # Leave the observer's actual tracking range, then re-enter it.
            # The owner remains damaged throughout (long fixture regen delay).
            cmd("tp CMobsA 112 -60 0 -90 0")
            (coord / "phase.txt").write_text("tracking-away")
            wait_files([coord / "tracking-away-CMobsA.ok"], 30)
            cmd("tp CMobsA 0 -60 0 -90 0")
            phase("tracking-return", "CMobsB", broken)
            print("CMOBS_MULTIPLAYER_TRACKING_REENTRY_OK")

            # Separate Stage 2 checks with two real players and many extra-spawn
            # attempts per tick. A cap of one must remain one across both players.
            def exact_count(selector, count):
                cmd("execute if entity " + selector)
                if count == 0:
                    wait_log(r"Test failed")
                else:
                    wait_log(rf"Test passed, count: {count}\b")
            cmd("gamerule doMobSpawning true")
            wait_log(r"Gamerule doMobSpawning is now set to: true")
            # Confirm a successful spawn rather than treating zero as a cap pass.
            for _ in range(30):
                cmd("execute if entity @e[tag=cmobs_ci_cap]")
                match = wait_log(r"Test failed|Test passed, count: (\d+)")
                if match[1]:
                    if int(match[1]) != 1:
                        raise AssertionError("Extra spawn cap exceeded: " + match[1])
                    break
                time.sleep(1)
            else:
                raise AssertionError("Extra spawn fixture never spawned")
            time.sleep(3)
            exact_count("@e[tag=cmobs_ci_cap]", 1)
            print("CMOBS_MULTIPLAYER_SPAWN_CAP_OK")
            cmd("gamerule doMobSpawning false")
            cmd("gamerule cmobsDisableSpawnStands true")
            cmd('summon minecraft:armor_stand 1 -60 5 {Tags:["cmobs_ci_spawn_stand"],NoGravity:1b}')
            wait_log(r"Summoned new")
            cmd('summon minecraft:item 1 -60 5 {Tags:["cmobs_ci_payment"],PickupDelay:32767s,NoGravity:1b,Item:{id:"minecraft:gold_ingot",Count:2b}}')
            wait_log(r"Summoned new")
            exact_count("@e[tag=cmobs_ci_stand_result]", 0)
            exact_count("@e[tag=cmobs_ci_payment]", 1)
            cmd("gamerule cmobsDisableSpawnStands false")
            time.sleep(2)
            exact_count("@e[tag=cmobs_ci_stand_result]", 1)
            exact_count("@e[tag=cmobs_ci_spawn_stand]", 0)
            exact_count("@e[tag=cmobs_ci_payment]", 0)
            print("CMOBS_MULTIPLAYER_SPAWN_STAND_OK")
            # Real melee and arrow DamageSource paths; spell and failed-swing
            # dispatch are invoked directly by the test-only server command.
            cmd("csummon cmobs_ci_mp:skill_target 1 -60 2")
            wait_log(r"Summoned custom mob cmobs_ci_mp:skill_target")
            for name in ("melee", "ranged", "miss", "cast"):
                cmd("tag CMobsA add custom_skill_cmobs_ci_mp_" + name)
                wait_log(r"Added tag")
            def skill(action, expected, player="CMobsA"):
                cmd(f"cmobs_ci_skill {player} @e[tag=cmobs_ci_skill_target,limit=1] {action}")
                match = wait_log(r"CMOBS_SKILL_PROBE " + action + r" (-?\d+) (-?\d+) (-?\d+) (-?\d+)", 30)
                actual = tuple(map(int, match.groups()))
                if actual != expected:
                    raise AssertionError(f"{player} {action}: {actual}, expected {expected}")
            cmd("item replace entity CMobsA weapon.mainhand with minecraft:iron_sword")
            time.sleep(1)
            skill("melee", (0,-1,-1,-1))
            print("CMOBS_SKILL_PLAYER_MELEE_OK")
            time.sleep(1.2)
            cmd("item replace entity CMobsA weapon.mainhand with minecraft:bow")
            skill("ranged", (1,0,-1,-1))
            print("CMOBS_SKILL_RANGED_DAMAGE_CALLBACK_OK")
            skill("success", (1,0,-1,-1))
            time.sleep(.5)
            skill("snapshot", (1,0,-1,-1))
            skill("miss", (1,0,-1,-1))
            time.sleep(.5)
            skill("snapshot", (1,0,0,-1))
            print("CMOBS_SKILL_FAILED_ATTACK_DISPATCH_OK")
            skill("wrongspell", (1,0,0,-1))
            skill("spell", (1,0,0,0))
            skill("snapshot", (-1,-1,-1,0), player="CMobsB")
            print("CMOBS_SKILL_SPELL_RELEASE_DISPATCH_OK")
            # Exercise the actual registered Fabric client-to-server packet.
            (coord / "phase.txt").write_text("miss-packet")
            wait_files([coord / ("miss-packet-" + name + ".ok") for name in ("CMobsA", "CMobsB")], 20)
            time.sleep(.6)
            skill("snapshot", (1,0,1,0))
            print("CMOBS_SKILL_FAILED_ATTACK_PACKET_OK")
            # Real damage callback and duplicate suppression; verify client HUD
            # matches server count, then actual miss packet and timeout resets.
            def combo(action, expected=None):
                cmd(f"cmobs_ci_skill CMobsA @e[tag=cmobs_ci_skill_target,limit=1] {action}")
                value = int(wait_log(r"CMOBS_COMBO_STATE (\d+)")[1])
                if expected is not None and value != expected:
                    raise AssertionError(f"Combo {action}: {value}, expected {expected}")
                return value
            def combo_client(expected, label):
                phase_name = f"combo-check-{expected}-{label}"
                (coord / "phase.txt").write_text(phase_name)
                wait_files([coord / (phase_name + "-CMobsA.ok")], 20)
            combo("combosnapshot", 0)
            cmd("item replace entity CMobsA weapon.mainhand with minecraft:iron_sword")
            time.sleep(1.1)
            combo("combohit", 1)
            combo_client(1, "first")
            time.sleep(1.1)
            combo("combohit", 2)
            combo_client(2, "second")
            (coord / "phase.txt").write_text("miss-packet-combo")
            wait_files([coord / ("miss-packet-combo-" + name + ".ok") for name in ("CMobsA", "CMobsB")], 20)
            time.sleep(.4)
            combo("combosnapshot", 0)
            combo_client(0, "miss")
            combo("combohit", 1)
            for _ in range(30):
                if combo("combosnapshot") == 0:
                    break
                time.sleep(.5)
            else:
                raise AssertionError("Combo did not expire")
            combo_client(0, "expired")
            print("CMOBS_COMBAT_COMBO_GAIN_MISS_TIMEOUT_SYNC_OK")
            cmd("cmobs_ci_skill CMobsA @e[tag=cmobs_ci_skill_target,limit=1] mana")
            wait_log(r"CMOBS_MANA_COMPAT_OK", 30)
            print("CMOBS_COMBAT_MANA_SPEND_RESTORE_OVERCHARGE_OK")

            # Real damage pipeline with both connected players. Reset only the
            # disposable fixture's previous synthetic posture states.
            for player, peer in (("CMobsA", "CMobsB"), ("CMobsB", "CMobsA")):
                cmd(f"cmobs_ci_skill {player} {peer} combatreset")
                wait_log(r"CMOBS_SKILL_PROBE combatreset")
            cmd("cmobs_ci_skill CMobsB CMobsA parry")
            wait_log(r"CMOBS_COMBAT_PROBE parry", 30)
            phase("real-parry-attacker", "CMobsA", truth("CMobsA", "snapshot"))
            print("CMOBS_COMBAT_REAL_PARRY_OK")
            time.sleep(1.1)
            cmd("cmobs_ci_skill CMobsB CMobsA startblock")
            wait_log(r"CMOBS_SKILL_PROBE startblock")
            (coord / "phase.txt").write_text("hold-block")
            wait_files([coord / "hold-block-CMobsB.ok"], 25)
            for _ in range(30):
                cmd("cmobs_ci_skill CMobsB CMobsA blockstate")
                match = wait_log(r"CMOBS_BLOCK_STATE (true|false) (\d+) (\d+)")
                if match[1] == "true" and int(match[2]) > int(match[3]) + 2:
                    break
                time.sleep(.2)
            else:
                raise AssertionError("Client-held shield never reached sustained server block")
            cmd("cmobs_ci_skill CMobsB CMobsA block")
            wait_log(r"CMOBS_COMBAT_PROBE block", 30)
            (coord / "phase.txt").write_text("release-block")
            wait_files([coord / "block-released-CMobsB.ok"], 20)
            state = truth("CMobsB", "snapshot")
            if not 0 < state[0] < 100:
                raise AssertionError("Real block posture not reduced")
            print("CMOBS_COMBAT_REAL_BLOCK_OK")
            time.sleep(1.1)
            cmd("cmobs_ci_skill CMobsB CMobsA unblocked")
            wait_log(r"CMOBS_COMBAT_PROBE unblocked", 30)
            print("CMOBS_COMBAT_REAL_UNBLOCKED_DAMAGE_OK")
            # Natural configured regeneration; poll server truth rather than
            # assuming the runner sustains precisely 20 TPS.
            for _ in range(30):
                state = truth("CMobsB", "snapshot")
                if state == (100, "false", "false"):
                    break
                time.sleep(.5)
            else:
                raise AssertionError("Player posture did not recover")
            phase("real-recovered", "CMobsB", state)
            print("CMOBS_COMBAT_NATURAL_REGEN_OK")
            # Keep a damaged owner stable during observer dimension transfer.
            truth("CMobsB", "longDisplay")
            partial = truth("CMobsB", "partial")
            if not 0 < partial[0] < 100:
                raise AssertionError("Dimension fixture did not start with damaged posture")
            phase("dimension-before", "CMobsB", partial)
            cmd("execute in minecraft:the_nether run tp CMobsA 0 129 0")
            (coord / "phase.txt").write_text("dimension-away")
            wait_files([coord / "dimension-away-CMobsA.ok"], 50)
            cmd("execute in minecraft:overworld run tp CMobsA 0 -60 0 -90 0")
            phase("dimension-return", "CMobsB", partial)
            print("CMOBS_MULTIPLAYER_DIMENSION_REENTRY_OK")
            # A real death/respawn must clear both HUD and overhead state;
            # keep the peer connected, and do not reset fixture combat maps.
            cmd("gamerule doImmediateRespawn true")
            cmd("spawnpoint CMobsB 3 -60 0")
            (coord / "phase.txt").write_text("respawn-wait")
            cmd("kill CMobsB")
            wait_files([coord / "respawn-wait-CMobsB.ok"], 40)
            state = truth("CMobsB", "snapshot")
            if state != (100, "false", "false"):
                raise AssertionError("Respawn inherited previous combat state: " + str(state))
            phase("respawn-clean", "CMobsB", state)
            print("CMOBS_MULTIPLAYER_RESPAWN_CLEAN_OK")



            (coord / "phase.txt").write_text("stop")
            for proc in processes[1:]:
                if proc.wait(timeout=45):
                    raise RuntimeError("Client did not exit cleanly")
            cmd("stop")
            server.stdin.close()
            wait_log(r"All dimensions are saved", 90)
            wait_log(r"CMOBS_POSTURE_SERVER_STOPPED", 30)
            try:
                if server.wait(timeout=20):
                    raise RuntimeError("Server did not exit cleanly")
            except subprocess.TimeoutExpired:
                # Diagnose the post-save process hang rather than silently passing.
                jcmd = Path(os.environ["JAVA_HOME"]) / "bin/jcmd.exe"
                with (evidence / "server-shutdown-threads.log").open("w", encoding="utf-8") as dump:
                    subprocess.run([str(jcmd), str(server.pid), "Thread.print", "-l"],
                                   stdout=dump, stderr=subprocess.STDOUT, timeout=30)
                dump_text = (evidence / "server-shutdown-threads.log").read_text(encoding="utf-8", errors="replace")
                # Keep all non-daemon thread headers/stacks in the workflow log, not just the tail.
                for block in re.split(r"\n\s*\n", dump_text):
                    if re.match(r'"[^"]+"', block) and " daemon " not in block.splitlines()[0]:
                        print("CMOBS_SHUTDOWN_THREAD", block, flush=True)
                # This is a known installed dependency defect, not a clean JVM exit.
                # Permit fixture cleanup ONLY when every remaining non-daemon Java
                # thread is an idle Fzzy Config worker or DestroyJavaVM. Any other
                # shutdown hang remains a hard test failure.
                remaining = [block for block in re.split(r"\n\s*\n", dump_text)
                             if re.match(r'"[^"]+" #\d+', block)
                             and " daemon " not in block.splitlines()[0]]
                workers = [block for block in remaining
                           if re.match(r'"Fzzy Config Worker-\d+"', block)
                           and "ThreadPoolExecutor.getTask" in block
                           and "LinkedBlockingQueue.take" in block
                           and "WAITING" in block]
                other = [block for block in remaining if block not in workers
                         and not block.startswith('"DestroyJavaVM"')]
                if not workers or other:
                    raise RuntimeError("Unexpected JVM shutdown hang; see thread dump")
                print("::warning::Installed Fzzy Config leaves idle non-daemon workers after SERVER_STOPPED; JVM exit failed")
                print("CMOBS_DEPENDENCY_SHUTDOWN_BLOCKER", len(workers))
                # The world save and lifecycle callback were already observed.
                # Only terminate this isolated, already-stopped fixture process.
                server.terminate()
                server.wait(timeout=30)
                print("CMOBS_FIXTURE_PROCESS_CLEANED_AFTER_SAVE")
            else:
                print("CMOBS_MULTIPLAYER_JVM_EXIT_OK")
            print("CMOBS_MULTIPLAYER_POSTURE_CORE_OK")
        finally:
            for proc in reversed(processes):
                if proc.poll() is None:
                    subprocess.run(["taskkill", "/PID", str(proc.pid), "/T", "/F"],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30)
                    proc.wait(timeout=30)
            reader.join(timeout=5)
            for log in logs:
                log.close()
            shutil.copytree(coord, evidence / "screens", dirs_exist_ok=True)
            for name, run in client_runs:
                if (run / "logs/latest.log").is_file():
                    shutil.copy2(run / "logs/latest.log", evidence / (name + "-latest.log"))
            for log in evidence.glob("*.log"):
                print(log.name, log.read_text(encoding="utf-8", errors="replace")[-8000:])

if __name__ == "__main__":
    main()
