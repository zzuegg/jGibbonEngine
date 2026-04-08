dependencies {
    api(project(":providers:slang-wasm"))
    implementation(libs.slf4j.api)
    implementation(libs.graalvm.polyglot)
    runtimeOnly(libs.graalvm.js)
    runtimeOnly(libs.graalvm.wasm)
}
