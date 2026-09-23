"""Package the signed Android release and exact committed project source."""
import hashlib
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
version = re.search(r'versionName = "([^"]+)"', (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")).group(1)
output = ROOT / "outputs/releases" / version
output.mkdir(parents=True, exist_ok=True)
prefix = "chathelp-android-" + version
apk = output / (prefix + "-release.apk")
shutil.copyfile(ROOT / "app/build/outputs/apk/release/app-release.apk", apk)
source = output / (prefix + "-project-source.zip")
subprocess.run(["git", "archive", "--format=zip", "--prefix=" + prefix + "/", "-o", str(source), "HEAD"], cwd=ROOT, check=True)
for name in ("CHANGELOG.md", "dependencies.json"):
    shutil.copyfile(ROOT / name, output / name)
shutil.copyfile(ROOT / "README.md", output / "INSTALL.txt")
assets = [apk, source, output / "CHANGELOG.md", output / "dependencies.json", output / "INSTALL.txt"]
fixture_version = re.search(r'versionName = "([^"]+)"', (ROOT / "test-chat/build.gradle.kts").read_text(encoding="utf-8")).group(1)
fixture = output / ("chathelp-test-chat-" + fixture_version + "-release.apk")
shutil.copyfile(ROOT / "test-chat/build/outputs/apk/release/test-chat-release.apk", fixture)
shutil.copyfile(ROOT / "test-chat/README.md", output / "TEST-CHAT.txt")
shutil.copyfile(ROOT / "test-chat/dependencies.json", output / "test-chat-dependencies.json")
assets.extend([fixture, output / "TEST-CHAT.txt", output / "test-chat-dependencies.json"])
lines = []
for path in assets:
    with path.open("rb") as stream:
        lines.append(hashlib.file_digest(stream, "sha256").hexdigest() + "  " + path.name)
    print(path.name, path.stat().st_size)
(output / "SHA256SUMS.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
