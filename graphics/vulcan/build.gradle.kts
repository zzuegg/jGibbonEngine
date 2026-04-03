val lwjglNatives = "natives-linux"

dependencies {
    api(project(":graphics:api"))
    implementation(project(":graphics:common"))
    implementation(project(":bindings:slang"))

    testImplementation(project(":providers:lwjgl-vk"))
    testImplementation(project(":windowing:glfw"))
    testImplementation(project(":bindings:slang"))
    testImplementation(platform(libs.lwjgl.bom))
    testImplementation(libs.lwjgl.glfw)
    testRuntimeOnly(libs.lwjgl.core.natives) { artifact { classifier = lwjglNatives } }
    testRuntimeOnly(libs.lwjgl.glfw.natives) { artifact { classifier = lwjglNatives } }
}
