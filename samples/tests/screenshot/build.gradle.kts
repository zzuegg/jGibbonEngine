val lwjglNatives = "natives-linux"

dependencies {
    testImplementation(project(":core"))
    testImplementation(project(":graphics:api"))
    testImplementation(project(":graphics:common"))
    testImplementation(project(":graphics:opengl"))
    testImplementation(project(":graphics:vulcan"))
    testImplementation(project(":graphics:webgpu"))
    testImplementation(project(":providers:lwjgl-gl"))
    testImplementation(project(":providers:lwjgl-vk"))
    testImplementation(project(":providers:jwebgpu"))
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
    val jemalloc = file("/lib/x86_64-linux-gnu/libjemalloc.so.2")
    if (jemalloc.exists()) {
        environment("LD_PRELOAD", jemalloc.absolutePath)
    }
}

tasks.register<JavaExec>("saveReferences") {
    group = "verification"
    description = "Renders all scenes and saves output as reference screenshots for regression tracking"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.engine.tests.screenshot.SaveReferences"
    workingDir = projectDir
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    val jemalloc = file("/lib/x86_64-linux-gnu/libjemalloc.so.2")
    if (jemalloc.exists()) {
        environment("LD_PRELOAD", jemalloc.absolutePath)
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
    mainClass = "dev.engine.tests.screenshot.ScreenshotReportGenerator"
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
