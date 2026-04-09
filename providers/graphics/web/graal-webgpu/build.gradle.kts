tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("--add-modules", "org.graalvm.webimage.api"))
}

dependencies {
    api(project(":graphics:webgpu"))
    implementation(libs.slf4j.api)
}
