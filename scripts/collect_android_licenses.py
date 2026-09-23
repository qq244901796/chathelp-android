"""Generate public runtime inventory and offline notices from Gradle's resolved artifacts.

First run :app:exportRuntimeArtifacts. All input paths stay in ignored build output.
"""
import hashlib
import io
import json
import argparse
import os
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
import zipfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", default=os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle")))
    parser.add_argument("--fetch-missing", action="store_true")
    args = parser.parse_args()
    cache = Path(args.cache) / "caches/modules-2/files-2.1"
    records, texts = [], {}
    target = ROOT / "licenses" / "third-party"
    target.mkdir(parents=True, exist_ok=True)
    def scan(blob, coordinate, depth=0):
        with zipfile.ZipFile(blob) as archive:
            for name in archive.namelist():
                low = name.lower()
                if low.endswith("/"):
                    continue
                if depth == 0 and low.endswith(".jar"):
                    scan(io.BytesIO(archive.read(name)), coordinate, 1)
                elif any(w in low for w in ("license", "notice", "copyright")) and archive.getinfo(name).file_size < 2000000:
                    try:
                        text = archive.read(name).decode("utf-8")
                    except UnicodeDecodeError:
                        continue
                    if "\x00" in text:
                        continue
                    digest = hashlib.sha256(text.encode("utf-8")).hexdigest()
                    item = texts.setdefault(digest, {"text": text, "sources": []})
                    item["sources"].append(coordinate + " / " + name)
    for line in (ROOT / "app/build/compliance/runtime-artifacts.tsv").read_text(encoding="utf-8").splitlines():
        coordinate, filename = line.split("\t", 1)
        path = Path(filename)
        group, artifact, version = coordinate.split(":")
        public_pom = target / (coordinate.replace(":", "_") + ".pom")
        poms = [public_pom] if public_pom.exists() else list((cache / group / artifact / version).glob("*/*.pom"))
        declarations = []
        if poms:
            tree = ET.parse(poms[0])
            declarations = [{"name": l.findtext("m:name", "", NS), "url": l.findtext("m:url", "", NS)}
                            for l in tree.findall("m:licenses/m:license", NS)]
            if poms[0] != public_pom:
                shutil.copyfile(poms[0], public_pom)
        if not declarations:
            public_pom = target / (coordinate.replace(":", "_") + ".pom")
            if args.fetch_missing:
                base = "https://dl.google.com/dl/android/maven2/" if group.startswith("androidx.") else "https://repo.maven.apache.org/maven2/"
                url = base + group.replace(".", "/") + f"/{artifact}/{version}/{artifact}-{version}.pom"
                with urllib.request.urlopen(url, timeout=30) as response:
                    public_pom.write_bytes(response.read())
            if public_pom.exists():
                tree = ET.parse(public_pom)
                declarations = [{"name": l.findtext("m:name", "", NS), "url": l.findtext("m:url", "", NS)}
                                for l in tree.findall("m:licenses/m:license", NS)]
        if not declarations and coordinate in ("javax.inject:javax.inject:1", "com.google.guava:listenablefuture:1.0"):
            declarations = [{"name": "Apache License 2.0 (verified source header; see ADDITIONAL_NOTICES.txt)",
                             "url": "https://www.apache.org/licenses/LICENSE-2.0"}]
        records.append({"coordinate": coordinate, "artifact": path.name,
                        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "licenses": declarations})
        scan(path, coordinate)
    (ROOT / "dependencies.json").write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding="utf-8")
    lines = ["ChatHelp Android 第三方组件和完整通知", "", "自有代码 MIT；依赖各依原许可。Google ML Kit 并非全部开源，不因本项目 MIT 获得额外授权。",
             "ML Kit 条款：https://developers.google.com/ml-kit/terms", "Android SDK 条款：https://developer.android.com/studio/terms", "",
             "下列版本来自发布版 releaseRuntimeClasspath，POM 原文和完整通知位于源码 licenses/。", ""]
    for item in records:
        names = "; ".join(l["name"] + " " + l["url"] for l in item["licenses"])
        lines.append(item["coordinate"] + " — " + (names or "参见本组件的内嵌通知/POM 来源"))
    for digest, item in texts.items():
        (target / (digest + ".txt")).write_text(item["text"], encoding="utf-8")
        lines.extend(["", "=" * 60, "\n".join(item["sources"]), "", item["text"]])
    apache = ROOT / "licenses/Apache-2.0.txt"
    if apache.exists():
        lines.extend(["", "Apache License 2.0（供采用本许可的组件使用）", apache.read_text(encoding="utf-8")])
    additional = ROOT / "licenses/ADDITIONAL_NOTICES.txt"
    if additional.exists():
        lines.extend(["", additional.read_text(encoding="utf-8")])
    output = "\n".join(lines) + "\n"
    (ROOT / "THIRD_PARTY_NOTICES.txt").write_text(output, encoding="utf-8")
    assets = ROOT / "app/src/main/assets/legal"
    assets.mkdir(parents=True, exist_ok=True)
    for name in ("ABOUT.md", "PRIVACY.md", "LICENSING.md", "THIRD_PARTY_NOTICES.txt"):
        shutil.copyfile(ROOT / name, assets / name)
    print(f"Runtime components: {len(records)}; embedded notices: {len(texts)}")
    print("No POM declaration:", [p["coordinate"] for p in records if not p["licenses"]])


if __name__ == "__main__":
    main()
