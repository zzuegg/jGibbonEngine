plugins {
    application
}

val lwjglNatives = "natives-linux"

dependencies {
    implementation(project(":core"))
    implementation(project(":graphics:api"))
    implementation(project(":graphics:common"))
    implementation(project(":graphics:opengl"))
    implementation(project(":providers:lwjgl-gl"))
    implementation(project(":graphics:vulcan"))
    implementation(project(":providers:lwjgl-vk"))
    implementation(project(":graphics:webgpu"))
    implementation(project(":providers:jwebgpu"))
    implementation(project(":providers:slang"))
    implementation(project(":providers:lwjgl-glfw"))
    implementation(platform(libs.lwjgl.bom))
    implementation(libs.lwjgl.opengl)
    implementation(libs.lwjgl.vulkan)
    implementation(libs.lwjgl.glfw)

    runtimeOnly(libs.lwjgl.core.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.lwjgl.opengl.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.lwjgl.glfw.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.slf4j.api)
    runtimeOnly("ch.qos.logback:logback-classic:1.5.6")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // Fork a new JVM per test class to avoid GLFW/Vulkan conflicts
    forkEvery = 1
    val jemalloc = file("/lib/x86_64-linux-gnu/libjemalloc.so.2")
    if (jemalloc.exists()) {
        environment("LD_PRELOAD", jemalloc.absolutePath)
    }
}

// Always rerun screenshot tests to get fresh screenshots when generating reports
tasks.test { outputs.upToDateWhen { false } }

tasks.register<JavaExec>("screenshotReport") {
    group = "verification"
    description = "Runs screenshot tests and generates an HTML comparison report"
    dependsOn("test")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.engine.examples.ScreenshotReportGenerator"
    args = listOf(
        layout.buildDirectory.dir("screenshots").get().asFile.absolutePath,
        layout.buildDirectory.dir("test-results/test").get().asFile.absolutePath,
        layout.buildDirectory.dir("screenshots/report.html").get().asFile.absolutePath
    )
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    doLast {
        val report = layout.buildDirectory.file("screenshots/report.html").get().asFile
        println("Open: file://${report.absolutePath}")
    }
}

application {
    mainClass = providers.gradleProperty("mainClass").orElse("dev.engine.examples.TriangleExample")
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.named<JavaExec>("run") {
    val maxFrames = providers.gradleProperty("maxFrames").orElse("120")
    val backend = providers.gradleProperty("backend").orElse("opengl")
    jvmArgs("-Dengine.maxFrames=${maxFrames.get()}")
    jvmArgs("-Dengine.backend=${backend.get()}")

    // Preload jemalloc to avoid glibc heap corruption with Slang COM release
    val jemalloc = file("/lib/x86_64-linux-gnu/libjemalloc.so.2")
    if (jemalloc.exists()) {
        environment("LD_PRELOAD", jemalloc.absolutePath)
    }
}
