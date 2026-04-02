val lwjglNatives = "natives-linux"

dependencies {
    api(project(":graphics:api"))

    implementation(platform(libs.lwjgl.bom))
    implementation(libs.lwjgl.core)
    implementation(libs.lwjgl.sdl)

    runtimeOnly(libs.lwjgl.core.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.lwjgl.sdl.natives) { artifact { classifier = lwjglNatives } }
}
