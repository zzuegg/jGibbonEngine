val lwjglNatives = "natives-linux"

tasks.test {
    forkEvery = 1
}

dependencies {
    api(project(":graphics:api"))
    implementation(project(":graphics:common"))
    implementation(project(":windowing:glfw"))

    implementation("com.github.xpenatan.jWebGPU:webgpu-core:0.1.15")
    implementation("com.github.xpenatan.jWebGPU:webgpu-desktop:0.1.15")

    implementation(platform(libs.lwjgl.bom))
    implementation(libs.lwjgl.core)
    implementation(libs.lwjgl.glfw)

    runtimeOnly(libs.lwjgl.core.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.lwjgl.glfw.natives) { artifact { classifier = lwjglNatives } }
}
