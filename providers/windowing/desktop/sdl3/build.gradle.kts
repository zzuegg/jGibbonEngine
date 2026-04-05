val lwjglNatives = when {
    System.getProperty("os.name").startsWith("Windows") -> "natives-windows"
    System.getProperty("os.name").startsWith("Mac") -> if (System.getProperty("os.arch") == "aarch64") "natives-macos-arm64" else "natives-macos"
    else -> "natives-linux"
}

dependencies {
    api(project(":graphics:api"))

    implementation(platform(libs.lwjgl.bom))
    implementation(libs.lwjgl.core)
    implementation(libs.lwjgl.sdl)
    implementation(libs.lwjgl.opengl)

    runtimeOnly(libs.lwjgl.core.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.lwjgl.sdl.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.lwjgl.opengl.natives) { artifact { classifier = lwjglNatives } }
}
