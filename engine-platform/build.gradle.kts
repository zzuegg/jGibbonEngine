dependencies {
    api(project(":engine-core"))

    api(platform(libs.lwjgl.bom))
    api(libs.bundles.lwjgl)
    runtimeOnly(libs.bundles.lwjgl.natives)
}
