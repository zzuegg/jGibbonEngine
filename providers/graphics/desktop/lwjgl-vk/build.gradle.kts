val lwjglNatives = when {
    System.getProperty("os.name").startsWith("Windows") -> "natives-windows"
    System.getProperty("os.name").startsWith("Mac") -> if (System.getProperty("os.arch") == "aarch64") "natives-macos-arm64" else "natives-macos"
    else -> "natives-linux"
}

dependencies {
    api(project(":graphics:vulcan"))
    api(project(":providers:lwjgl-glfw"))
    implementation(project(":providers:slang"))

    implementation(platform(libs.lwjgl.bom))
    implementation(libs.lwjgl.core)
    implementation(libs.lwjgl.vulkan)

    runtimeOnly(libs.lwjgl.core.natives) { artifact { classifier = lwjglNatives } }
}
