val lwjglNatives = "natives-linux"

dependencies {
    api(project(":graphics:api"))
    implementation(project(":graphics:opengl"))

    implementation(platform(libs.lwjgl.bom))
    implementation(libs.lwjgl.core)
    implementation(libs.lwjgl.vulkan)
    implementation(libs.lwjgl.glfw)
    implementation(libs.lwjgl.shaderc)

    runtimeOnly(libs.lwjgl.core.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.lwjgl.glfw.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.lwjgl.shaderc.natives) { artifact { classifier = lwjglNatives } }

    testImplementation(project(":graphics:opengl"))
    testRuntimeOnly(libs.lwjgl.opengl.natives) { artifact { classifier = lwjglNatives } }
}
