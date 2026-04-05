val lwjglNatives = when {
    System.getProperty("os.name").startsWith("Windows") -> "natives-windows"
    System.getProperty("os.name").startsWith("Mac") -> if (System.getProperty("os.arch") == "aarch64") "natives-macos-arm64" else "natives-macos"
    else -> "natives-linux"
}

dependencies {
    api(project(":graphics:api"))
    implementation(project(":graphics:common"))
    implementation(project(":providers:slang"))

    testImplementation(project(":providers:lwjgl-vk"))
    testImplementation(project(":providers:lwjgl-glfw"))
    testImplementation(project(":providers:slang"))
    testImplementation(platform(libs.lwjgl.bom))
    testImplementation(libs.lwjgl.glfw)
    testRuntimeOnly(libs.lwjgl.core.natives) { artifact { classifier = lwjglNatives } }
    testRuntimeOnly(libs.lwjgl.glfw.natives) { artifact { classifier = lwjglNatives } }
}
