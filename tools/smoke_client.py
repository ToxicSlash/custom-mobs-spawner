"""Production client test with explicitly staged probe JAR and bounded process tree."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
from smoke_server import resolve, ROOT, OUT, sha
from prepare_ci import metadata

def gradle():
    temp = Path(os.environ["RUNNER_TEMP"])
    root = temp / "cmobs-production-gradle"
    binary = root / "gradle-8.12.1/bin/gradle.bat"
    if not binary.is_file():
        archive = temp / "cmobs-gradle-8.12.1.zip"
        with urllib.request.urlopen("https://services.gradle.org/distributions/gradle-8.12.1-bin.zip", timeout=120) as r, archive.open("wb") as f:
            shutil.copyfileobj(r, f)
        if sha(archive) != "8d97a97984f6cbd2b85fe4c60a743440a347544bf18818048e611f5288d46c94":
            raise RuntimeError("Production client Gradle checksum mismatch")
        with zipfile.ZipFile(archive) as z:
            z.extractall(root)
    if not binary.is_file():
        raise RuntimeError("Missing production Gradle")
    return binary

def execute(command, log, timeout):
    # Keep the parent alive until taskkill /T has terminated its own descendants.
    with log.open("w", encoding="utf-8") as output:
        proc = subprocess.Popen(command, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT)
        try:
            rc = proc.wait(timeout=timeout)
            if rc:
                raise RuntimeError("Gradle process failed: " + str(rc))
        except BaseException:
            if proc.poll() is None:
                subprocess.run(["taskkill", "/PID", str(proc.pid), "/T", "/F"],
                               stdout=output, stderr=subprocess.STDOUT, timeout=30)
                proc.wait(timeout=30)
            raise

def main():
    evidence = OUT / "client"
    evidence.mkdir(parents=True, exist_ok=True)
    chosen = resolve(extra=("bettercombat", "amethyst_imbuement"), client=True)
    (evidence / "runtime.json").write_text(json.dumps({p.name: sha(p) for p in chosen}, indent=2))
    with tempfile.TemporaryDirectory(prefix="cmobs-client-", dir=os.environ["RUNNER_TEMP"], ignore_cleanup_errors=True) as tmp:
        root = Path(tmp)
        mods, run = root / "mods", root / "game"
        mods.mkdir()
        run.mkdir()
        # Skip vanilla first-run accessibility onboarding, not the title screen.
        (run / "options.txt").write_text("version:3465\nonboardAccessibility:false\npauseOnLostFocus:false\n", encoding="utf-8")
        for jar in chosen:
            shutil.copy2(jar, mods / jar.name)
        project = root / "probe"
        shutil.copytree(ROOT / "tools/production-client", project, ignore=shutil.ignore_patterns("build", ".gradle"))
        binary = gradle()
        cmd = [str(binary), "-p", str(project), "--no-daemon", "--console=plain",
               "-PcmobsRuntimeMods=" + str(mods), "-PcmobsClientRunDir=" + str(run)]
        try:
            execute(cmd + ["remapJar"], evidence / "probe-build.log", 420)
            probes = [p for p in (project / "build/libs").glob("*.jar")
                      if not p.name.endswith(("-dev.jar", "-sources.jar")) and metadata(p).get("id") == "cmobs_ci_client_probe"]
            if len(probes) != 1:
                raise RuntimeError("Expected exactly one remapped probe JAR: " + str(probes))
            shutil.copy2(probes[0], mods / probes[0].name)
            print("CMOBS_CLIENT_PROBE_STAGED", probes[0].name, sha(probes[0]))
            execute(cmd + ["runCmobsClient"], evidence / "gradle-client.log", 240)
            if not (run / "cmobs-title-ready.txt").is_file() or not (run / "cmobs-title.png").is_file():
                raise RuntimeError("Client exited without actual title screen evidence")
            if not (run / "cmobs-title-ready.txt").read_text().startswith("CMOBS_CLIENT_TITLE_OK "):
                raise RuntimeError("Invalid title probe marker")
            print((run / "cmobs-title-ready.txt").read_text())
        finally:
            for path in ("cmobs-title-ready.txt", "cmobs-title.png", "logs/latest.log"):
                src = run / path
                if src.is_file():
                    shutil.copy2(src, evidence / src.name)
            if (run / "crash-reports").is_dir():
                shutil.copytree(run / "crash-reports", evidence / "crash-reports", dirs_exist_ok=True)
            for name in ("probe-build.log", "gradle-client.log", "latest.log"):
                log = evidence / name
                if log.is_file():
                    value = log.read_text(encoding="utf-8", errors="replace")
                    print(name, value[:7000], "\n--- tail ---\n", value[-12000:])

if __name__ == "__main__":
    main()
