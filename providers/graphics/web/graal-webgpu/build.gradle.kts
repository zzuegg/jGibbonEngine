dependencies {
    api(project(":graphics:webgpu"))
    implementation(libs.slf4j.api)
    implementation(libs.graalvm.polyglot)
    runtimeOnly(libs.graalvm.js)
    runtimeOnly(libs.graalvm.wasm)
}
