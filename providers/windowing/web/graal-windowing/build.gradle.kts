dependencies {
    api(project(":graphics:api"))
    api(project(":core"))
    implementation(libs.slf4j.api)
    implementation(libs.graalvm.polyglot)
    runtimeOnly(libs.graalvm.js)
}
