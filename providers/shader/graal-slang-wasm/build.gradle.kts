tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("--add-modules", "org.graalvm.webimage.api"))
}

dependencies {
    api(project(":providers:slang-wasm"))
    implementation(libs.slf4j.api)
}
