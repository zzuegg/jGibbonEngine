dependencies {
    api(project(":graphics:webgpu"))
    api(project(":providers:lwjgl-glfw"))
    implementation(project(":providers:slang"))
    implementation(files("../../../../libs/webgpu-ffm-v27.0.4.0.jar"))
}
