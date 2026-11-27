"""Two real graphical clients on an isolated loopback dedicated server."""
import os
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
        def phase(label, target, state, selfbar=True, view="back"):
            percent, visible, staggered = state
            content = "|".join([label, target, str(percent), visible, staggered, str(selfbar).lower(), view])
            temp = coord / "phase.tmp"
            temp.write_text(content, encoding="utf-8")
            os.replace(temp, coord / "phase.txt")
            wait_files([coord / f"{label}-{name}.ok" for name in ("CMobsA", "CMobsB")], 14)
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
                (run / "options.txt").write_text("version:3465\nonboardAccessibility:false\npauseOnLostFocus:false\nfullscreen:false\n")
                output = (evidence / (name + ".log")).open("w", encoding="utf-8")
                logs.append(output)
                client_runs.append((name, run))
                proc = subprocess.Popen(gradle_cmd(p, run) + [
                    "-PcmobsUsername=" + name, f"-PcmobsMultiplayer=127.0.0.1:{port}",
                    "-PcmobsPostureCoordinator=" + str(coord), "runCmobsClient"],
                    cwd=ROOT, stdout=output, stderr=subprocess.STDOUT)
                processes.append(proc)
            wait_files([coord / (name + ".ready") for name in ("CMobsA", "CMobsB")], 240)
            cmd("tp CMobsA 0 4 0 -90 0")
            cmd("tp CMobsB 3 4 0 90 0")
            phase("idle", "CMobsB", truth("CMobsB", "snapshot"))
            partial = truth("CMobsB", "partial")
            if not 0 < partial[0] < 100 or partial[1:] != ("true", "false"):
                raise AssertionError("Unexpected partial posture: " + str(partial))
            phase("partial-first", "CMobsB", partial, view="first")
            phase("partial-back", "CMobsB", partial, view="back")
            phase("partial-front", "CMobsB", partial, view="front")
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
            (coord / "phase.txt").write_text("stop")
            for proc in processes[1:]:
                if proc.wait(timeout=45):
                    raise RuntimeError("Client did not exit cleanly")
            cmd("stop")
            if server.wait(timeout=60):
                raise RuntimeError("Server did not exit cleanly")
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
