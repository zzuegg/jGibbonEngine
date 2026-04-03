val lwjglNatives = "natives-linux"

dependencies {
    api(project(":graphics:api"))

    implementation(platform(libs.lwjgl.bom))
    implementation(libs.lwjgl.core)
    implementation(libs.lwjgl.glfw)
    // GLFW Vulkan support for surface creation
    implementation(libs.lwjgl.vulkan)

    runtimeOnly(libs.lwjgl.core.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.lwjgl.glfw.natives) { artifact { classifier = lwjglNatives } }
}
