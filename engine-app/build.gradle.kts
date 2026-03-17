dependencies {
    api(project(":engine-core"))
    api(project(":engine-render"))
    api(project(":engine-platform"))

    implementation(libs.logback.classic)
}
