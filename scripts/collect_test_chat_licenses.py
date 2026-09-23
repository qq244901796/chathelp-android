"""Generate the small fixture APK's notices from its actual Gradle artifacts."""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / "test-chat"
lines = ["ChatHelp 测试聊天 1.0.0", "Copyright (c) 2026 ChatHelp contributors", "",
         "自有代码按 MIT 提供。以下保留项目完整许可：", (ROOT / "LICENSE").read_text(encoding="utf-8"),
         "本测试 APK 仅使用 Kotlin 标准库及 JetBrains annotations；不包含 ChatHelp 的 OCR / 模型 SDK。", ""]
records = []
for row in (MODULE / "build/compliance/runtime-artifacts.tsv").read_text(encoding="utf-8").splitlines():
    coordinate, filename = row.split("\t", 1)
    archive_path = Path(filename)
    records.append({"coordinate": coordinate, "artifact": archive_path.name,
                    "sha256": hashlib.sha256(archive_path.read_bytes()).hexdigest(), "license": "Apache-2.0"})
    lines.append(coordinate)
    with zipfile.ZipFile(archive_path) as archive:
        for name in archive.namelist():
            if not name.endswith("/") and any(word in name.lower() for word in ("license", "notice", "copyright")):
                lines.extend(["", coordinate + " / " + name, archive.read(name).decode("utf-8")])
lines.extend(["", (ROOT / "licenses/Apache-2.0.txt").read_text(encoding="utf-8")])
output = "\n".join(lines) + "\n"
(MODULE / "THIRD_PARTY_NOTICES.txt").write_text(output, encoding="utf-8")
assets = MODULE / "src/main/assets"
assets.mkdir(parents=True, exist_ok=True)
(assets / "THIRD_PARTY_NOTICES.txt").write_text(output, encoding="utf-8")
(MODULE / "dependencies.json").write_text(json.dumps(records, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Fixture runtime components: {len(records)}")
