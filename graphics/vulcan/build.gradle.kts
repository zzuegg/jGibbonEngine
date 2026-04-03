val lwjglNatives = "natives-linux"

dependencies {
    api(project(":graphics:api"))
    implementation(project(":graphics:common"))
    implementation(project(":bindings:slang"))

    implementation(platform(libs.lwjgl.bom))
    implementation(libs.lwjgl.core)
    implementation(libs.lwjgl.vulkan)

    runtimeOnly(libs.lwjgl.core.natives) { artifact { classifier = lwjglNatives } }

    testImplementation(project(":windowing:glfw"))
    testImplementation(project(":bindings:slang"))
    testImplementation(libs.lwjgl.glfw)
    testRuntimeOnly(libs.lwjgl.glfw.natives) { artifact { classifier = lwjglNatives } }
}
