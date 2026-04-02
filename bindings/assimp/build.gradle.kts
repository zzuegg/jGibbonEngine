val lwjglNatives = "natives-linux"

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
