---
layout: page
title: "GraalVM Toolchain Selection"
description: "Why graal-* modules bypass Gradle's toolchain selection for the javac override."
---

# GraalVM Toolchain Selection

The `graal-*` modules (`platforms:graalwasm`, `providers:graal-webgpu`,
`providers:graal-windowing`, `providers:graal-slang-wasm`, and
`samples:tests:screenshot:graalwasm-runner`) need to compile with Oracle
**GraalVM's** `javac`, because they use `--add-modules
org.graalvm.webimage.api` — a module that only exists in GraalVM, not in
regular Oracle JDK or OpenJDK builds.

Gradle's built-in toolchain selection **cannot reliably pick GraalVM**
when other Oracle-vendored JDKs of the same language version are also
installed. This document explains why, and the workaround the build
scripts apply.

## The root problem

Gradle's toolchain spec API exposes only three selectors:
`languageVersion`, `vendor`, and `implementation`. None of them
distinguishes Oracle GraalVM from regular Oracle JDK.

Running `./gradlew :platforms:graalwasm-runner:javaToolchains` on a
typical SDKMAN setup lists these Java 26 JDKs, *all with `Vendor: Oracle`*:

| Path | Binary | What it actually is |
|---|---|---|
| `~/.sdkman/candidates/java/26.ea.13-graal` | `26+13-jvmci-b01` | **Oracle GraalVM 26 EA** (the one we want) |
| `~/.sdkman/candidates/java/26.ea.35-open`  | `26+35-2893`       | Oracle OpenJDK 26 EA (no WebImage module) |
| `~/.sdkman/candidates/java/26-oracle`      | `26+35-2893`       | Oracle JDK 26 (no WebImage module) |

The only distinguishing marker is the `-jvmci-b01` JVMCI suffix in the
build string — but Gradle's public toolchain API has no way to filter
by that. `JvmVendorSpec.ORACLE` and `JvmVendorSpec.GRAAL_VM` both match
all three; `JvmImplementation` has no GraalVM value. Gradle picks the
"Current JVM" first by default, which is controlled by
`$HOME/.sdkman/candidates/java/current` (SDKMAN's default symlink).

**The trap:** if you install a new Java JDK via `sdk install java <id>`,
SDKMAN automatically repoints `current` at the newly-installed JDK. So
installing any new Java 26 JDK silently breaks the graal-* builds —
Gradle picks the latest non-GraalVM install as the Current JVM and uses
*that* for the toolchain.

This is exactly what happened on 2026-04-09: the
`graalvm-java = "26"` version was committed in the morning while
GraalVM 26 EA was the SDKMAN default, then later that day OpenJDK 26
EA was installed and SDKMAN shifted `current` to point at it. The next
build failed with
`error: module not found: org.graalvm.webimage.api`.

## The fix

Rather than fight Gradle's toolchain selection, the root
`build.gradle.kts` scans for a GraalVM installation at configuration
time and exposes the detected path as
`rootProject.extra["graalVmHome"]`:

```kotlin
val detectedGraalVmHome: String? = run {
    val envHome = System.getenv("GRAALVM_HOME")
    if (envHome != null && JFile(envHome, "bin/javac").exists()) {
        return@run envHome
    }
    val sdkman = JFile(System.getProperty("user.home"), ".sdkman/candidates/java")
    if (!sdkman.isDirectory) return@run null
    val entries: Array<JFile> = sdkman.listFiles() ?: return@run null
    val graalDirs: List<JFile> = entries
        .filter { f -> f.isDirectory && f.name.endsWith("-graal") }
        .sortedByDescending { f -> f.name }
    for (dir in graalDirs) {
        if (JFile(dir, "bin/javac").exists()) {
            return@run dir.absolutePath
        }
    }
    null
}
rootProject.extra["graalVmHome"] = detectedGraalVmHome
```

**Detection priority:**

1. `GRAALVM_HOME` environment variable (if set and valid)
2. Latest `~/.sdkman/candidates/java/*-graal` with a working `bin/javac`
3. Null (subprojects fall back to Gradle's default toolchain picking)

Each graal-* module then overrides the JavaCompile task's `javac`
executable directly, bypassing the toolchain selection:

```kotlin
val graalVmHome = rootProject.extra["graalVmHome"] as String?

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("--add-modules", "org.graalvm.webimage.api"))
    if (graalVmHome != null) {
        options.isFork = true
        options.forkOptions.javaHome = file(graalVmHome)
    }
}
```

`options.isFork = true` with `forkOptions.javaHome` tells Gradle to
invoke `javac` from that specific JDK, regardless of which toolchain
the task-level `java { toolchain { ... } }` spec resolved to. The
`java.toolchain` block is kept as a floor — Gradle still needs *some*
Java 26 JDK on hand to satisfy its task graph — but the actual
compilation runs against the GraalVM `javac` we force.

The `wasmCompile` task in `samples:tests:screenshot:graalwasm/build.gradle.kts`
uses the same detected path to locate `native-image`:

```kotlin
val graalHome: Provider<String> = if (graalVmHome != null) {
    providers.provider { graalVmHome }
} else {
    javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(graalJavaVersion)
        vendor = JvmVendorSpec.ORACLE
    }.map { it.executablePath.asFile.parentFile.parentFile.absolutePath }
}
```

## Verifying

When the detection succeeds, every Gradle invocation prints:

```
GraalVM detected at: /home/you/.sdkman/candidates/java/26.ea.13-graal
```

If you see `No GraalVM detected — graal-* modules will fall back to
default toolchain selection` (at `--info` level), you either need to
install a GraalVM into SDKMAN (e.g. `sdk install java 26.ea.13-graal`)
or set `GRAALVM_HOME` in your shell.

## Overriding the detected path

To force a specific GraalVM (e.g. to test against a nightly build not in
SDKMAN), export `GRAALVM_HOME` before running Gradle:

```bash
export GRAALVM_HOME=/opt/graalvm-community-26-dev
./gradlew :samples:tests:screenshot:graalwasm-runner:wasmCompile
```

`GRAALVM_HOME` takes priority over the SDKMAN scan.

## Why we didn't use `org.gradle.java.installations.fromEnv`

Gradle supports `org.gradle.java.installations.fromEnv=GRAALVM_HOME`
in `gradle.properties`, which adds the JDK at `$GRAALVM_HOME` to the
toolchain candidate pool. This doesn't solve the selection problem
though — it just adds one more "Oracle 26" candidate to a list where
Gradle can't tell them apart. The actively-selected JDK is still
whichever one matches the criteria *first*, which depends on Gradle's
internal ordering (typically: Current JVM, then alphabetical), not on
user intent.

Overriding `forkOptions.javaHome` directly sidesteps the whole mess.

## When to revisit this

If Gradle ever introduces a more specific JVM implementation selector
(e.g. `JvmImplementation.GRAALVM_NATIVE_IMAGE`, or a custom
`JavaToolchainResolver` plugin surface), the manual detection in the
root `build.gradle.kts` can be replaced with standard toolchain specs.
Until then, the filesystem scan is the reliable path.
