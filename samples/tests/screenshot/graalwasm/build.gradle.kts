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
tasks.register<Exec>("wasmCompile") {
    dependsOn(tasks.jar)
    // Ensure all dependency jars are built
    dependsOn(configurations.runtimeClasspath)
    group = "build"
    description = "Compile GraalWasm test app to WebAssembly"

    val outputDir = layout.buildDirectory.dir("wasm")

    doFirst {
        outputDir.get().asFile.mkdirs()
    }

    // Use doFirst to resolve classpath at execution time
    doFirst {
        val cp = sourceSets.main.get().runtimeClasspath.asPath
        val wat2wasm = project.findProperty("wat2wasm.path")?.toString()
        val cmd = mutableListOf(
            "native-image",
            "--tool:svm-wasm",
            "-H:-AutoRunVM",
            "-cp", cp,
            "dev.engine.tests.screenshot.graalwasm.GraalWasmTestApp",
            "-o", outputDir.get().file("main").asFile.absolutePath
        )
        if (wat2wasm != null) {
            cmd.add("-H:-UseBinaryen")
            cmd.add("-H:Wat2WasmPath=$wat2wasm")
        }
        commandLine(cmd)
    }
}

// Copy test.html and assets next to compiled WASM
tasks.register<Copy>("assembleWasmTest") {
    dependsOn("wasmCompile")
    from("src/main/webapp")
    into(layout.buildDirectory.dir("wasm"))
    // Copy shader files from graphics:common
    from(project(":graphics:common").file("src/main/resources")) {
        into("assets")
    }
    // Copy Slang WASM
    if (rootProject.file("platforms/web/src/main/webapp/slang").exists()) {
        from(rootProject.file("platforms/web/src/main/webapp/slang")) {
            into("slang")
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
