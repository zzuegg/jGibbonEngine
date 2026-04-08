dependencies {
    api(project(":core"))
    api(project(":graphics:api"))
    api(project(":graphics:common"))
    api(project(":graphics:webgpu"))
    implementation(project(":providers:graal-webgpu"))
    implementation(project(":providers:graal-windowing"))
    implementation(project(":providers:graal-slang-wasm"))

    implementation(libs.graalvm.polyglot)
    runtimeOnly(libs.graalvm.js)
    runtimeOnly(libs.graalvm.wasm)
}
