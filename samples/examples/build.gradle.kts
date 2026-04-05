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

application {
    mainClass = providers.gradleProperty("mainClass").orElse("dev.engine.examples.MyGame")
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}
