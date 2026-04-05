val lwjglNatives = when {
    System.getProperty("os.name").startsWith("Windows") -> "natives-windows"
    System.getProperty("os.name").startsWith("Mac") -> if (System.getProperty("os.arch") == "aarch64") "natives-macos-arm64" else "natives-macos"
    else -> "natives-linux"
}

tasks.test {
    forkEvery = 1
}

dependencies {
    api(project(":graphics:api"))
    implementation(project(":graphics:common"))
    implementation(project(":providers:lwjgl-glfw"))

    testImplementation(project(":providers:jwebgpu"))

    implementation(platform(libs.lwjgl.bom))
    implementation(libs.lwjgl.core)
    implementation(libs.lwjgl.glfw)

    runtimeOnly(libs.lwjgl.core.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.lwjgl.glfw.natives) { artifact { classifier = lwjglNatives } }
}
