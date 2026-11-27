"""Launch the actual remapped CMobs jar with a separate, CI-only client probe."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
from smoke_server import resolve, ROOT, OUT, sha

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

def main():
    evidence = OUT / "client"
    evidence.mkdir(parents=True, exist_ok=True)
    chosen = resolve()
    (evidence / "runtime.json").write_text(json.dumps({p.name: sha(p) for p in chosen}, indent=2))
    with tempfile.TemporaryDirectory(prefix="cmobs-client-", dir=os.environ["RUNNER_TEMP"]) as tmp:
        root = Path(tmp)
        mods, run = root / "mods", root / "game"
        mods.mkdir()
        run.mkdir()
        for jar in chosen:
            shutil.copy2(jar, mods / jar.name)
        binary = gradle()
        cmd = [str(binary), "-p", str(ROOT / "tools/production-client"), "--no-daemon", "--console=plain",
               "-PcmobsRuntimeMods=" + str(mods), "-PcmobsClientRunDir=" + str(run), "runCmobsClient"]
        try:
            with (evidence / "gradle-client.log").open("w", encoding="utf-8") as log:
                result = subprocess.run(cmd, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, timeout=1200)
            if result.returncode:
                raise RuntimeError("Production client launch failed: " + str(result.returncode))
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
            if (evidence / "gradle-client.log").is_file():
                print((evidence / "gradle-client.log").read_text(encoding="utf-8", errors="replace")[-10000:])

if __name__ == "__main__":
    main()
