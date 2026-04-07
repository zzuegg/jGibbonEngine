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
