val lwjglNatives = when {
    System.getProperty("os.name").startsWith("Windows") -> "natives-windows"
    System.getProperty("os.name").startsWith("Mac") -> if (System.getProperty("os.arch") == "aarch64") "natives-macos-arm64" else "natives-macos"
    else -> "natives-linux"
}

dependencies {
    implementation(project(":samples:tests:screenshot:scenes"))
    implementation(project(":samples:tests:screenshot:runner"))
    implementation(project(":graphics:opengl"))
    implementation(project(":graphics:vulcan"))
    implementation(project(":graphics:webgpu"))
    implementation(project(":providers:lwjgl-gl"))
    implementation(project(":providers:lwjgl-vk"))
    implementation(project(":providers:jwebgpu"))
    implementation(project(":providers:slang"))
    implementation(project(":providers:lwjgl-glfw"))
    implementation(project(":platforms:desktop"))

    runtimeOnly(platform(libs.lwjgl.bom))
    runtimeOnly(libs.lwjgl.core.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.lwjgl.opengl.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.lwjgl.glfw.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.slf4j.api)
    runtimeOnly("ch.qos.logback:logback-classic:1.5.6")
}

// ── Shared paths ────────────────────────────────────────────────────
val screenshotBuildDir = rootProject.layout.buildDirectory.dir("screenshots")
val screenshotParentDir = project.parent!!.projectDir
val referencesDir = screenshotParentDir.resolve("references")
val profile = project.findProperty("screenshot.profile")?.toString() ?: "local"
val sceneFilter = project.findProperty("screenshot.scene")?.toString() ?: ""

// ── Jemalloc for native safety ──────────────────────────────────────
val jemallocPaths = listOf(
    "/lib/x86_64-linux-gnu/libjemalloc.so.2",
    "/usr/lib/libjemalloc.so.2",
    "/usr/lib64/libjemalloc.so.2",
    "/opt/homebrew/lib/libjemalloc.dylib"
)
val jemalloc = jemallocPaths.map { file(it) }.firstOrNull { it.exists() }

// ── Pipeline Pass 1: Collect scenes ─────────────────────────────────
tasks.register<JavaExec>("collectScenes") {
    group = "verification"
    description = "Pass 1: Discover scenes and write initial manifest"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.engine.tests.screenshot.scenes.CollectScenes"
    args = listOf(
        screenshotBuildDir.get().file("screenshot-report.json").asFile.absolutePath,
        profile
    )
    outputs.upToDateWhen { false }
}

// ── Pipeline Pass 2: Run desktop backends ───────────────────────────
tasks.register<JavaExec>("runDesktop") {
    group = "verification"
    description = "Pass 2: Run all scenes on desktop backends"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.engine.tests.screenshot.desktop.DesktopRunnerMain"
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    if (jemalloc != null) environment("LD_PRELOAD", jemalloc.absolutePath)
    args = listOfNotNull(
        screenshotBuildDir.get().file("screenshot-report.json").asFile.absolutePath,
        screenshotBuildDir.get().asFile.absolutePath,
        referencesDir.resolve(profile).absolutePath,
        profile,
        sceneFilter.ifEmpty { null }
    )
    outputs.upToDateWhen { false }
    dependsOn("collectScenes")
}

// ── Save references ─────────────────────────────────────────────────
tasks.register<JavaExec>("saveReferences") {
    group = "verification"
    description = "Render all scenes and save as reference screenshots"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.engine.tests.screenshot.desktop.DesktopRunnerMain"
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    if (jemalloc != null) environment("LD_PRELOAD", jemalloc.absolutePath)
    args = listOfNotNull(
        screenshotBuildDir.get().file("screenshot-report.json").asFile.absolutePath,
        referencesDir.resolve(profile).absolutePath,
        referencesDir.resolve(profile).absolutePath,
        profile,
        sceneFilter.ifEmpty { null }
    )
    outputs.upToDateWhen { false }
    dependsOn("collectScenes")
}
