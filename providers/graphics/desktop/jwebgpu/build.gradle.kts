dependencies {
    api(project(":graphics:webgpu"))
    api(project(":providers:lwjgl-glfw"))
    implementation(project(":providers:slang"))

    implementation("com.github.xpenatan.jWebGPU:webgpu-core:0.1.15")
    implementation("com.github.xpenatan.jWebGPU:webgpu-desktop:0.1.15")
}
