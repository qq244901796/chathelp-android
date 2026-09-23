"""Create a persistent local release certificate without printing its passwords."""

import os
from pathlib import Path
import secrets
import shutil
import subprocess


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    signing = root / ".signing"
    properties = signing / "keystore.properties"
    keystore = signing / "chathelp-release.p12"
    if properties.exists() and keystore.exists():
        print("Release signing already configured; existing certificate preserved.")
        return
    if properties.exists() or keystore.exists():
        raise SystemExit("Incomplete signing setup. Preserve and check the existing files before retrying.")

    java_home = os.environ.get("JAVA_HOME")
    candidate = Path(java_home) / "bin" / "keytool.exe" if java_home else None
    keytool = str(candidate) if candidate and candidate.is_file() else shutil.which("keytool")
    if not keytool:
        raise SystemExit("keytool is unavailable. Install JDK 17 or set JAVA_HOME.")

    signing.mkdir(parents=True, exist_ok=True)
    password = secrets.token_urlsafe(36)
    child_env = dict(os.environ)
    child_env["CHATHELP_SIGNING_PASSWORD"] = password
    result = subprocess.run(
        [keytool, "-genkeypair", "-keystore", str(keystore), "-storetype", "PKCS12",
         "-alias", "chathelp-release", "-keyalg", "RSA", "-keysize", "4096",
         "-sigalg", "SHA256withRSA", "-validity", "10000",
         "-dname", "CN=ChatHelp Release, OU=Android, O=ChatHelp, C=CN",
         "-storepass:env", "CHATHELP_SIGNING_PASSWORD",
         "-keypass:env", "CHATHELP_SIGNING_PASSWORD", "-noprompt"],
        env=child_env, capture_output=True, check=False,
    )
    if result.returncode:
        # Do not relay subprocess output that could contain credential material.
        raise SystemExit(f"keytool failed (exit {result.returncode}); signing configuration was not written.")
    with properties.open("x", encoding="utf-8", newline="\n") as output:
        output.write(
            "storeFile=chathelp-release.p12\n"
            "keyAlias=chathelp-release\n"
            f"storePassword={password}\n"
            f"keyPassword={password}\n"
        )
    print("Release certificate created in .signing/. Back up this directory for future updates.")


if __name__ == "__main__":
    main()
