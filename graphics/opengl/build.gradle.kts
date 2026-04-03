val lwjglNatives = "natives-linux"

dependencies {
    api(project(":graphics:api"))
    implementation(project(":graphics:common"))
    implementation(project(":providers:lwjgl-glfw"))

    testImplementation(project(":providers:lwjgl-gl"))
    testImplementation(platform(libs.lwjgl.bom))
    testImplementation(libs.lwjgl.core)
    testImplementation(libs.lwjgl.opengl)
    testImplementation(libs.lwjgl.glfw)
    testRuntimeOnly(libs.lwjgl.core.natives) { artifact { classifier = lwjglNatives } }
    testRuntimeOnly(libs.lwjgl.opengl.natives) { artifact { classifier = lwjglNatives } }
    testRuntimeOnly(libs.lwjgl.glfw.natives) { artifact { classifier = lwjglNatives } }
}
