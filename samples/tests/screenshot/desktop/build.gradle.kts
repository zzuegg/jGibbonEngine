val lwjglNatives = when {
    System.getProperty("os.name").startsWith("Windows") -> "natives-windows"
    System.getProperty("os.name").startsWith("Mac") -> if (System.getProperty("os.arch") == "aarch64") "natives-macos-arm64" else "natives-macos"
    else -> "natives-linux"
}

dependencies {
    testImplementation(project(":samples:tests:screenshot:scenes"))
    testImplementation(project(":core"))
    testImplementation(project(":graphics:api"))
    testImplementation(project(":graphics:common"))
    testImplementation(project(":graphics:opengl"))
    testImplementation(project(":graphics:vulcan"))
    testImplementation(project(":graphics:webgpu"))
    testImplementation(project(":providers:lwjgl-gl"))
    testImplementation(project(":providers:lwjgl-vk"))
    testImplementation(project(":providers:jwebgpu"))
    testImplementation(project(":providers:wgpu-ffm"))
    testImplementation(project(":providers:slang"))
    testImplementation(project(":providers:lwjgl-glfw"))
    testImplementation(project(":platforms:desktop"))
    testImplementation(platform(libs.lwjgl.bom))
    testImplementation(libs.lwjgl.opengl)
    testImplementation(libs.lwjgl.vulkan)
    testImplementation(libs.lwjgl.glfw)

    testRuntimeOnly(libs.lwjgl.core.natives) { artifact { classifier = lwjglNatives } }
    testRuntimeOnly(libs.lwjgl.opengl.natives) { artifact { classifier = lwjglNatives } }
    testRuntimeOnly(libs.lwjgl.glfw.natives) { artifact { classifier = lwjglNatives } }
    testRuntimeOnly(libs.slf4j.api)
    testRuntimeOnly("ch.qos.logback:logback-classic:1.5.6")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    forkEvery = 1
    outputs.upToDateWhen { false }
    val jemallocPaths = listOf(
        "/lib/x86_64-linux-gnu/libjemalloc.so.2",
        "/usr/lib/libjemalloc.so.2",
        "/usr/lib64/libjemalloc.so.2",
        "/opt/homebrew/lib/libjemalloc.dylib"
    )
    jemallocPaths.map { file(it) }.firstOrNull { it.exists() }?.let {
        environment("LD_PRELOAD", it.absolutePath)
    }
}

tasks.register<JavaExec>("saveReferences") {
    group = "verification"
    description = "Renders all scenes and saves output as reference screenshots for regression tracking"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.engine.tests.screenshot.desktop.SaveReferences"
    workingDir = projectDir
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    val jemallocPaths = listOf(
        "/lib/x86_64-linux-gnu/libjemalloc.so.2",
        "/usr/lib/libjemalloc.so.2",
        "/usr/lib64/libjemalloc.so.2",
        "/opt/homebrew/lib/libjemalloc.dylib"
    )
    jemallocPaths.map { file(it) }.firstOrNull { it.exists() }?.let {
        environment("LD_PRELOAD", it.absolutePath)
    }
}

// Run tests + generate report (even on test failure)
tasks.register("screenshotReport") {
    group = "verification"
    description = "Runs screenshot tests and generates an HTML comparison report — even on failures"
    dependsOn("test", "generateReport")
}

// Make test not block generateReport on failure
tasks.test { finalizedBy("generateReport") }

tasks.register<JavaExec>("generateReport") {
    group = "verification"
    description = "Generates the HTML screenshot report from existing test results"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.engine.tests.screenshot.desktop.ScreenshotReportGenerator"
    args = listOf(
        layout.buildDirectory.dir("screenshots").get().asFile.absolutePath,
        layout.buildDirectory.dir("test-results/test").get().asFile.absolutePath,
        layout.buildDirectory.dir("screenshots/report.html").get().asFile.absolutePath
    )
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    doLast {
        val report = layout.buildDirectory.file("screenshots/report.html").get().asFile
        println("Report: file://${report.absolutePath}")
    }
}
