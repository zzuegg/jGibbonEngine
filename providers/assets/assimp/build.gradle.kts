val lwjglNatives = when {
    System.getProperty("os.name").startsWith("Windows") -> "natives-windows"
    System.getProperty("os.name").startsWith("Mac") -> if (System.getProperty("os.arch") == "aarch64") "natives-macos-arm64" else "natives-macos"
    else -> "natives-linux"
}

dependencies {
    api(project(":core"))

    implementation(platform(libs.lwjgl.bom))
    implementation(libs.lwjgl.core)
    implementation(libs.lwjgl.assimp)
    implementation(libs.lwjgl.stb)

    runtimeOnly(libs.lwjgl.core.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.lwjgl.assimp.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.lwjgl.stb.natives) { artifact { classifier = lwjglNatives } }
}
