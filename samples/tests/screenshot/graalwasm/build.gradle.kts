val graalJavaVersion = rootProject.extensions
    .getByType<VersionCatalogsExtension>()
    .named("libs")
    .findVersion("graalvm-java").orElseThrow().requiredVersion

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(graalJavaVersion)
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("--add-modules", "org.graalvm.webimage.api"))
}

dependencies {
    // Main source set: compiled to WASM by native-image
    implementation(project(":core"))
    implementation(project(":graphics:api"))
    implementation(project(":graphics:common"))
    implementation(project(":graphics:webgpu"))
    implementation(project(":providers:graal-webgpu"))
    implementation(project(":providers:graal-windowing"))
    implementation(project(":providers:graal-slang-wasm"))
    implementation(project(":platforms:graalwasm"))
    implementation(project(":samples:tests:screenshot:scenes"))

    // Test source set: JVM-only runner (not compiled to WASM)
    testImplementation(project(":samples:tests:screenshot:runner"))
    testImplementation(project(":samples:tests:screenshot:scenes"))
    testImplementation(project(":graphics:api"))

    testRuntimeOnly("org.slf4j:slf4j-api:2.0.16")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.5.6")
}

tasks.test {
    useJUnitPlatform()
    failOnNoDiscoveredTests = false
}

// ── WASM compilation via native-image --tool:svm-wasm ───────────────

// Resolve native-image from the GraalVM toolchain
val graalHome: Provider<String> = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(graalJavaVersion)
    vendor = JvmVendorSpec.ORACLE
}.map { it.executablePath.asFile.parentFile.parentFile.absolutePath }

tasks.register<Exec>("wasmCompile") {
    dependsOn(tasks.jar)
    dependsOn(configurations.runtimeClasspath)
    group = "build"
    description = "Compile GraalWasm test app to WebAssembly"

    val outputDir = layout.buildDirectory.dir("wasm")

    doFirst {
        outputDir.get().asFile.mkdirs()
    }

    doFirst {
        val cp = sourceSets.main.get().runtimeClasspath.asPath
        val graalBin = graalHome.get() + "/bin"
        val nativeImage = "$graalBin/native-image"

        // Find wasm-as (Binaryen) on PATH or common locations
        val wasmAs = listOf(
            System.getenv("WASM_AS"),
            findOnPath("wasm-as"),
            "/tmp/binaryen-version_129/bin/wasm-as",
            "/usr/bin/wasm-as",
            "/usr/local/bin/wasm-as"
        ).firstOrNull { it != null && file(it).exists() }

        val wat2wasm = project.findProperty("wat2wasm.path")?.toString()
            ?: listOf(
                findOnPath("wat2wasm"),
                "/tmp/wabt-1.0.40/bin/wat2wasm",
                "/usr/bin/wat2wasm",
                "/usr/local/bin/wat2wasm"
            ).firstOrNull { it != null && file(it).exists() }

        val cmd = mutableListOf(
            nativeImage,
            "--tool:svm-wasm",
            "-H:-AutoRunVM",
            "-cp", cp,
            "dev.engine.tests.screenshot.graalwasm.GraalWasmTestApp",
            "-o", outputDir.get().file("main").asFile.absolutePath
        )

        // Prefer Binaryen (wasm-as), fall back to wabt (wat2wasm)
        if (wasmAs != null) {
            // Binaryen is on PATH or found — native-image finds it automatically
            val binDir = file(wasmAs).parentFile.absolutePath
            environment("PATH", binDir + ":" + System.getenv("PATH"))
        } else if (wat2wasm != null) {
            cmd.add("-H:-UseBinaryen")
            cmd.add("-H:Wat2WasmPath=$wat2wasm")
        } else {
            throw GradleException(
                "Neither wasm-as (Binaryen) nor wat2wasm (WABT) found. " +
                "Install Binaryen (apt install binaryen) or set -Pwat2wasm.path=<path>."
            )
        }

        commandLine(cmd)
    }
}

fun findOnPath(name: String): String? {
    val pathDirs = System.getenv("PATH")?.split(File.pathSeparator) ?: return null
    return pathDirs.map { File(it, name) }.firstOrNull { it.exists() && it.canExecute() }?.absolutePath
}

// Copy test.html and assets next to compiled WASM.
// Uses Sync instead of Copy to avoid wiping the wasmCompile output (main.js/main.js.wasm).
// Sync only adds/updates files from the source, preserving existing files in the dest.
tasks.register("assembleWasmTest") {
    dependsOn("wasmCompile")
    val wasmDir = layout.buildDirectory.dir("wasm")
    doLast {
        // Copy webapp files
        copy {
            from("src/main/webapp")
            into(wasmDir)
        }
        // Copy shader files
        copy {
            from(project(":graphics:common").file("src/main/resources"))
            into(wasmDir.get().dir("assets"))
        }
        // Copy Slang WASM
        val slangDir = rootProject.file("platforms/web/src/main/webapp/slang")
        if (slangDir.exists()) {
            copy {
                from(slangDir)
                into(wasmDir.get().dir("slang"))
            }
        }
    }
}

// ── Shared paths ────────────────────────────────────────────────────
val screenshotBuildDir = rootProject.layout.buildDirectory.dir("screenshots")
val screenshotParentDir = project.parent!!.projectDir
val referencesDir = screenshotParentDir.resolve("references")
val profile = project.findProperty("screenshot.profile")?.toString() ?: "local"
val sceneFilter = project.findProperty("screenshot.scene")?.toString() ?: ""

// ── Pipeline Pass 2c: Run GraalWasm backend ─────────────────────────
tasks.register<JavaExec>("runGraalWasm") {
    group = "verification"
    description = "Pass 2c: Run all scenes on GraalWasm/WebGPU backend via headless Chrome"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.engine.tests.screenshot.graalwasm.GraalWasmRunnerMain"
    args = listOfNotNull(
        screenshotBuildDir.get().file("screenshot-report.json").asFile.absolutePath,
        screenshotBuildDir.get().asFile.absolutePath,
        referencesDir.resolve(profile).absolutePath,
        profile,
        layout.buildDirectory.dir("wasm").get().asFile.absolutePath,
        sceneFilter.ifEmpty { null }
    )
    outputs.upToDateWhen { false }
    dependsOn("assembleWasmTest")
    mustRunAfter(":samples:tests:screenshot:desktop-runner:runDesktop")
    mustRunAfter(":samples:tests:screenshot:web-runner:runWeb")
    dependsOn(":samples:tests:screenshot:desktop-runner:collectScenes")
}
