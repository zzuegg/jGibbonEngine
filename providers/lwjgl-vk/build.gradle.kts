val lwjglNatives = "natives-linux"

dependencies {
    api(project(":graphics:vulcan"))
    api(project(":providers:lwjgl-glfw"))

    implementation(platform(libs.lwjgl.bom))
    implementation(libs.lwjgl.core)
    implementation(libs.lwjgl.vulkan)

    runtimeOnly(libs.lwjgl.core.natives) { artifact { classifier = lwjglNatives } }
}
