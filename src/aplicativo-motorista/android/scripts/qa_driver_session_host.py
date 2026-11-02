#!/usr/bin/env python3
"""Offline, bounded Kotlin/JUnit checks; adapter compilation is not an APK/device gate."""
from pathlib import Path
import os
import subprocess


def main():
    android = Path(__file__).resolve().parents[1]
    cache = Path.home() / ".gradle/caches/modules-2/files-2.1"

    def jar(group, artifact, version=None):
        folder = cache / group / artifact
        if version:
            folder /= version
        matches = sorted(folder.glob("**/*.jar"))
        if not matches:
            raise RuntimeError("Cached dependency missing: " + artifact)
        return str(matches[0])

    compiler = [jar("org.jetbrains.kotlin", name, "2.2.20") for name in
                ("kotlin-compiler-embeddable", "kotlin-stdlib", "kotlin-script-runtime")]
    compiler += [jar("org.jetbrains.kotlin", "kotlin-reflect", "2.2.0"),
                 jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.8.1"), jar("org.jetbrains", "annotations")]
    stdlib = jar("org.jetbrains.kotlin", "kotlin-stdlib", "2.2.20")
    json = jar("org.json", "json", "20180813")
    junit = jar("junit", "junit", "4.13.2")
    hamcrest = jar("org.hamcrest", "hamcrest-core")
    java = Path("/opt/homebrew/opt/openjdk@21/bin/java")
    sdk = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Library/Android/sdk")))
    android_jar = sdk / "platforms/android-36/android.jar"
    if not java.is_file() or not android_jar.is_file():
        raise RuntimeError("Prepared Java21/Android36 toolchain required")
    output = android / ".gradle/session060-host"
    output.mkdir(parents=True, exist_ok=True)
    sources = android / "app/src/driver/kotlin/com/example/vanrides_frontend_dev/driver"
    test = android / "app/src/testDriver/kotlin/com/example/vanrides_frontend_dev/driver/DriverSessionCoreTest.kt"

    def compile_kotlin(files, classpath, destination):
        subprocess.run([str(java), "-Xmx384m", "-cp", ":".join(compiler),
                        "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "17",
                        "-classpath", ":".join(classpath), "-d", str(destination)] + [str(file) for file in files], check=True)

    tests = output / "tests.jar"
    compile_kotlin([sources / "DriverSessionProtocol.kt", sources / "DriverSessionCore.kt", test],
                   [stdlib, json, junit, hamcrest], tests)
    subprocess.run([str(java), "-Xmx256m", "-cp", ":".join([str(tests), stdlib, json, junit, hamcrest]),
                    "org.junit.runner.JUnitCore", "com.example.vanrides_frontend_dev.driver.DriverSessionCoreTest"], check=True)
    # Compile the real adapters against SDK/Flutter APIs, with explicit generated-constant stubs.
    # Gradle/manifest merging, APK signing, Keystore and TLS still require the platform gate/smoke.
    stub = output / "BuildConfig.kt"
    stub.write_text('package com.example.vanrides_frontend_dev\nobject BuildConfig { const val DEBUG=true; '
                    'const val PAIRING_API_ORIGIN="https://localhost:18443"; const val PAIRING_LAB_ENABLED=true; '
                    'const val PAIRING_LINK_HOST=""; const val VERSION_NAME="0.1.0+3" }\n')
    compile_kotlin(sorted(sources.glob("DriverSession*.kt")) + [stub],
                   [stdlib, str(android_jar), jar("io.flutter", "flutter_embedding_debug")], output / "android-adapters.jar")
    # Compile the exact alternative factories in separate outputs. The030 contract
    # is a fixture on the MVP test classpath, never an MVP packaging source.
    package = "kotlin/com/example/vanrides_frontend_dev/driver"
    contract = android / "app/src/driverGpsSpikeContract" / package / "DriverSpikeContract.kt"
    tests_folder = android / "app/src/testDriver" / package
    pure = [sources / name for name in ("DriverSessionProtocol.kt", "DriverSessionCore.kt",
            "TrackingProtocol.kt", "TrackingWire.kt", "DriverEnergyDiagnostics.kt", "DriverHttpClient.kt")]
    for mode, folder in (("mvp", "driverProduction"), ("spike", "driverGpsSpike")):
        selected = sorted((android / "app/src" / folder / package).glob("*.kt"))
        mode_tests = output / (mode + "-tracking-tests.jar")
        compile_kotlin(pure + selected + [contract] + sorted(tests_folder.glob("*.kt")),
                       [stdlib, json, junit, hamcrest, str(android_jar)], mode_tests)
        subprocess.run([str(java), "-Xmx256m", "-cp", ":".join([str(mode_tests), stdlib, json, junit, hamcrest, str(android_jar)]),
                        "org.junit.runner.JUnitCore", *["com.example.vanrides_frontend_dev.driver." + name for name in
                        ("DriverSessionCoreTest", "TrackingProtocolTest", "DriverEnergyDiagnosticsTest",
                         "ProductionTrackingWireTest", "DriverTrackingBuildTest")]], check=True)
        print("PASS: " + mode + " source selection and host tracking contracts")
    print("PASS: session host tests and Android session adapters (no Gradle, APK or device)")


if __name__ == "__main__":
    main()
