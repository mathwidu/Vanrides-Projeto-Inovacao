#!/usr/bin/env python3
"""Build/sign one private probe. Never installs, runs, downloads or prints secrets."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import zipfile


class ProbeError(Exception):
    pass


def run(argv, stage, env, stdin=None):
    try:
        result = subprocess.run(argv, input=stdin, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, env=env, timeout=120, check=False)
    except (OSError, subprocess.TimeoutExpired):
        raise ProbeError(stage) from None
    if result.returncode:
        raise ProbeError(stage)
    return result.stdout.decode("utf-8", errors="replace")


def signing_properties(path):
    """Strict subset written by prepare_driver_test_signing.py; never echo values."""
    try:
        info = path.stat()
        if info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) & 0o077:
            raise ProbeError("SIGNING_PROPERTIES_NOT_PRIVATE")
        if info.st_size > 16384:
            raise ProbeError("SIGNING_PROPERTIES_INVALID")
        values = {}
        for line in path.read_text(encoding="utf-8").splitlines():
            if not line.strip() or line.lstrip().startswith(("#", "!")):
                continue
            key, separator, value = line.partition("=")
            if not separator or key in values or value != value.strip() or "\\" in value:
                raise ProbeError("SIGNING_PROPERTIES_INVALID")
            values[key] = value
        if set(values) != {"storeFile", "storePassword", "keyAlias", "keyPassword"} or not all(values.values()):
            raise ProbeError("SIGNING_PROPERTIES_INVALID")
        store = Path(values["storeFile"])
        if not store.is_absolute() or not store.is_file():
            raise ProbeError("KEYSTORE_UNAVAILABLE")
        return values
    except (OSError, UnicodeError):
        raise ProbeError("SIGNING_PROPERTIES_UNAVAILABLE") from None


def certificate(apksigner, apk, env):
    output = run([str(apksigner), "verify", "--verbose", "--print-certs", str(apk)],
                 "APK_VERIFICATION_FAILED", env)
    digests = re.findall(r"^Signer #[0-9]+ certificate SHA-256 digest: ([a-fA-F0-9]{64})$",
                         output, re.MULTILINE)
    if len(digests) != 1:
        raise ProbeError("SINGLE_SIGNER_REQUIRED")
    return digests[0].lower()


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build(args):
    root = Path(__file__).resolve().parent
    sdk = args.android_sdk.resolve()
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", args.build_tools):
        raise ProbeError("BUILD_TOOLS_VERSION_INVALID")
    tools = sdk / "build-tools" / args.build_tools
    android_jar = sdk / "platforms" / f"android-{args.compile_sdk}" / "android.jar"
    java_home = args.java_home.resolve()
    javac = java_home / "bin" / "javac"
    required = [javac, java_home / "bin" / "java", android_jar]
    required += [tools / name for name in ("d8", "aapt2", "zipalign", "apksigner")]
    if not all(path.is_file() for path in required):
        raise ProbeError("TOOLCHAIN_UNAVAILABLE")
    if not all(os.access(path, os.X_OK) for path in required if path != android_jar):
        raise ProbeError("TOOLCHAIN_NOT_EXECUTABLE")
    env = os.environ.copy()
    env["JAVA_HOME"] = str(java_home)
    # javac writes its version to stderr on some JDKs; this command has no secrets.
    version_result = subprocess.run([str(javac), "-version"], capture_output=True,
                                    timeout=15, env=env, check=False)
    version = (version_result.stdout + version_result.stderr).decode("utf-8", errors="replace").strip()
    if version_result.returncode or not re.fullmatch(r"javac 21(?:\.[0-9]+)+(?:[+._a-zA-Z0-9-]*)", version):
        raise ProbeError("JDK21_REQUIRED")
    target = args.target_apk.resolve()
    if not target.is_file():
        raise ProbeError("TARGET_APK_UNAVAILABLE")
    target_certificate = certificate(tools / "apksigner", target, env)
    target_sha256 = sha256(target)
    badging = run([str(tools / "aapt2"), "dump", "badging", str(target)], "TARGET_MANIFEST_INVALID", env)
    if not re.search(r"^package: name='dev\.matheusduarte\.vanrides\.driver' ", badging, re.MULTILINE):
        raise ProbeError("TARGET_PACKAGE_MISMATCH")
    if "application-debuggable" in badging:
        raise ProbeError("RELEASE_TARGET_REQUIRED")
    output = args.output_dir.resolve()
    if output.exists() or not output.parent.is_dir():
        raise ProbeError("NEW_OUTPUT_DIRECTORY_REQUIRED")
    if any((parent / ".git").exists() for parent in [output, *output.parents]):
        raise ProbeError("OUTPUT_MUST_BE_OUTSIDE_GIT")
    signing = signing_properties(args.signing_properties)
    os.umask(0o077)
    output.mkdir(mode=0o700)
    work = output / "work"
    work.mkdir(mode=0o700)
    classes, dex = work / "classes", work / "dex"
    classes.mkdir(); dex.mkdir()
    run([str(javac), "--release", "8", "-classpath", str(android_jar), "-d", str(classes),
         str(root / "DirectGnssProbe.java")], "JAVA_COMPILATION_FAILED", env)
    jar = work / "classes.jar"
    with zipfile.ZipFile(jar, "w") as archive:
        for entry in sorted(classes.rglob("*.class")):
            archive.write(entry, entry.relative_to(classes).as_posix())
    if not any(classes.rglob("*.class")):
        raise ProbeError("NO_CLASSES")
    run([str(tools / "d8"), "--release", "--min-api", "29", "--lib", str(android_jar),
         "--output", str(dex), str(jar)], "DEX_COMPILATION_FAILED", env)
    unsigned = work / "unsigned.apk"
    run([str(tools / "aapt2"), "link", "-o", str(unsigned), "-I", str(android_jar),
         "--manifest", str(root / "AndroidManifest.xml")], "APK_PACKAGING_FAILED", env)
    dex_files = sorted(dex.glob("classes*.dex"))
    if len(dex_files) != 1 or dex_files[0].name != "classes.dex":
        raise ProbeError("SINGLE_DEX_REQUIRED")
    with zipfile.ZipFile(unsigned, "a") as archive:
        archive.write(dex_files[0], "classes.dex")
    aligned = work / "aligned.apk"
    run([str(tools / "zipalign"), "-f", "4", str(unsigned), str(aligned)], "ZIPALIGN_FAILED", env)
    apk = output / "gnss-probe.apk"
    passwords = (signing["storePassword"] + "\n" + signing["keyPassword"] + "\n").encode("utf-8")
    run([str(tools / "apksigner"), "sign", "--ks", signing["storeFile"], "--ks-key-alias", signing["keyAlias"],
         "--ks-pass", "stdin", "--key-pass", "stdin", "--v2-signing-enabled", "true", "--out", str(apk), str(aligned)],
        "APK_SIGNING_FAILED", env, passwords)
    del passwords, signing
    probe_certificate = certificate(tools / "apksigner", apk, env)
    if probe_certificate != target_certificate:
        raise ProbeError("SIGNER_MISMATCH")
    if sha256(target) != target_sha256:
        raise ProbeError("TARGET_APK_CHANGED")
    run([str(tools / "zipalign"), "-c", "4", str(apk)], "ALIGNMENT_VERIFICATION_FAILED", env)
    metadata = {"schemaVersion": 1, "probeVersion": 1, "apkSha256": sha256(apk),
                "targetApkSha256": target_sha256, "signerSha256": probe_certificate,
                "buildTools": args.build_tools, "compileSdk": args.compile_sdk,
                "minSdk": 29, "targetSdk": 29, "javacVersion": version.removeprefix("javac ")}
    encoded = json.dumps(metadata, sort_keys=True)
    (output / "build-metadata.json").write_text(encoded + "\n", encoding="utf-8")
    print(encoded)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--android-sdk", type=Path, required=True)
    parser.add_argument("--build-tools", required=True)
    parser.add_argument("--compile-sdk", type=int, default=33)
    parser.add_argument("--java-home", type=Path,
                        default=Path(os.environ.get("JAVA_HOME", "/opt/homebrew/opt/openjdk@21")))
    parser.add_argument("--signing-properties", type=Path, required=True)
    parser.add_argument("--target-apk", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    try:
        build(args)
    except ProbeError as failure:
        print("PROBE_BUILD_ERROR: " + str(failure), file=sys.stderr)
        return 1
    except (OSError, ValueError, subprocess.SubprocessError, zipfile.BadZipFile):
        # Do not print exception messages, subprocess argv, paths or tool output.
        print("PROBE_BUILD_ERROR: LOCAL_BUILD_FAILED", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
