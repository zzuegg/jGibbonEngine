tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("--add-modules", "org.graalvm.webimage.api"))
}

dependencies {
    api(project(":core"))
    api(project(":graphics:api"))
    api(project(":graphics:common"))
    api(project(":graphics:webgpu"))
    implementation(project(":providers:graal-webgpu"))
    implementation(project(":providers:graal-windowing"))
    implementation(project(":providers:graal-slang-wasm"))
}
