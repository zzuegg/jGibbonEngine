dependencies {
    api(project(":engine-core"))
    api(project(":engine-platform"))

    api(platform(libs.lwjgl.bom))
    api(libs.lwjgl.opengl)
    runtimeOnly(libs.lwjgl.opengl.natives)
}
