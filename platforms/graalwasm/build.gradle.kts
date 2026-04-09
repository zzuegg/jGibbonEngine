val graalJavaVersion = rootProject.extensions
    .getByType<VersionCatalogsExtension>()
    .named("libs")
    .findVersion("graalvm-java").orElseThrow().requiredVersion

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(graalJavaVersion)
        vendor = JvmVendorSpec.ORACLE
    }
}

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
