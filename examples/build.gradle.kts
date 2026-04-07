plugins {
    application
}

val lwjglNatives = when {
    System.getProperty("os.name").startsWith("Windows") -> "natives-windows"
    System.getProperty("os.name").startsWith("Mac") -> if (System.getProperty("os.arch") == "aarch64") "natives-macos-arm64" else "natives-macos"
    else -> "natives-linux"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":graphics:api"))
    implementation(project(":graphics:common"))
    implementation(project(":platforms:desktop"))
    implementation(project(":graphics:opengl"))
    implementation(project(":providers:lwjgl-gl"))
    implementation(project(":graphics:vulcan"))
    implementation(project(":providers:lwjgl-vk"))
    implementation(project(":graphics:webgpu"))
    implementation(project(":providers:jwebgpu"))
    implementation(project(":providers:slang"))
    implementation(project(":providers:lwjgl-glfw"))
    implementation(project(":providers:sdl3"))
    implementation(platform(libs.lwjgl.bom))
    implementation(libs.lwjgl.opengl)
    implementation(libs.lwjgl.vulkan)
    implementation(libs.lwjgl.glfw)
    implementation(libs.lwjgl.sdl)

    runtimeOnly(libs.lwjgl.core.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.lwjgl.opengl.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.lwjgl.glfw.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.lwjgl.sdl.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.slf4j.api)
    runtimeOnly("ch.qos.logback:logback-classic:1.5.6")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // Fork a new JVM per test class to avoid GLFW/Vulkan conflicts
    forkEvery = 1
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

application {
    mainClass = providers.gradleProperty("mainClass").orElse("dev.engine.examples.TriangleExample")
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.named<JavaExec>("run") {
    val maxFrames = providers.gradleProperty("maxFrames").orElse("120")
    val backend = providers.gradleProperty("backend").orElse("opengl")
    val windowing = providers.gradleProperty("windowing").orElse("glfw")
    jvmArgs("-Dengine.maxFrames=${maxFrames.get()}")
    jvmArgs("-Dengine.backend=${backend.get()}")
    jvmArgs("-Dengine.windowing=${windowing.get()}")

    // Preload jemalloc to avoid glibc heap corruption with Slang COM release
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
