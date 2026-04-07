plugins {
    id("org.teavm") version "0.13.1"
}

val teavmVersion = "0.13.1"

configurations.all {
    // SLF4J not available in TeaVM classlib
    exclude(group = "org.slf4j", module = "slf4j-api")
}

dependencies {
    // Main source set: compiled to JS by TeaVM
    implementation(project(":core"))
    implementation(project(":graphics:api"))
    implementation(project(":graphics:common"))
    implementation(project(":graphics:webgpu"))
    implementation(project(":providers:teavm-webgpu"))
    implementation(project(":providers:teavm-windowing"))
    implementation(project(":samples:tests:screenshot:scenes"))

    implementation("org.teavm:teavm-jso:$teavmVersion")
    implementation("org.teavm:teavm-jso-apis:$teavmVersion")
    implementation("org.teavm:teavm-interop:$teavmVersion")

    // Test source set: JVM-only runner (not processed by TeaVM)
    testImplementation(project(":samples:tests:screenshot:runner"))
    testImplementation(project(":samples:tests:screenshot:scenes"))
    testImplementation(project(":graphics:api"))

    testRuntimeOnly("org.slf4j:slf4j-api:2.0.16")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.5.6")
}

teavm {
    js {
        mainClass = "dev.engine.tests.screenshot.web.WebTestApp"
        outputDir = layout.buildDirectory.dir("web")
        obfuscated = false
        targetFileName = "web-test.js"
    }
}

// Copy test.html and shared assets next to generated JS
tasks.register<Copy>("assembleWebTest") {
    dependsOn("generateJavaScript")
    from("src/main/webapp")
    into(layout.buildDirectory.dir("web"))
    // Copy shader files from graphics:common
    from(project(":graphics:common").file("src/main/resources")) {
        into("assets")
    }
    // Copy Slang WASM from platforms/web (shared asset)
    from(rootProject.file("platforms/web/src/main/webapp/slang")) {
        into("slang")
    }
}

// ── Shared paths ────────────────────────────────────────────────────
val screenshotBuildDir = rootProject.layout.buildDirectory.dir("screenshots")
val screenshotParentDir = project.parent!!.projectDir
val referencesDir = screenshotParentDir.resolve("references")
val profile = project.findProperty("screenshot.profile")?.toString() ?: "local"
val sceneFilter = project.findProperty("screenshot.scene")?.toString() ?: ""

// ── Pipeline Pass 2b: Run web backend ───────────────────────────────
tasks.register<JavaExec>("runWeb") {
    group = "verification"
    description = "Pass 2b: Run all scenes on web (TeaVM/WebGPU) backend via headless Chrome"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.engine.tests.screenshot.web.WebRunnerMain"
    args = listOfNotNull(
        screenshotBuildDir.get().file("screenshot-report.json").asFile.absolutePath,
        screenshotBuildDir.get().asFile.absolutePath,
        referencesDir.resolve(profile).absolutePath,
        profile,
        layout.buildDirectory.dir("web").get().asFile.absolutePath,
        sceneFilter.ifEmpty { null }
    )
    outputs.upToDateWhen { false }
    dependsOn("assembleWebTest")
    // Must run after desktop so both append to the same manifest without conflict.
    // When triggered standalone, collectScenes ensures the manifest exists.
    mustRunAfter(":samples:tests:screenshot:desktop-runner:runDesktop")
    dependsOn(":samples:tests:screenshot:desktop-runner:collectScenes")
}

// ── Save references ─────────────────────────────────────────────────
tasks.register<JavaExec>("saveReferences") {
    group = "verification"
    description = "Render all web scenes and save as reference screenshots"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.engine.tests.screenshot.web.WebRunnerMain"
    args = listOfNotNull(
        screenshotBuildDir.get().file("screenshot-report.json").asFile.absolutePath,
        referencesDir.resolve(profile).absolutePath,
        referencesDir.resolve(profile).absolutePath,
        profile,
        layout.buildDirectory.dir("web").get().asFile.absolutePath,
        sceneFilter.ifEmpty { null }
    )
    outputs.upToDateWhen { false }
    dependsOn("assembleWebTest")
    dependsOn(":samples:tests:screenshot:desktop-runner:collectScenes")
}
