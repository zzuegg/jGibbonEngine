val graalJavaVersion = rootProject.extensions
    .getByType<VersionCatalogsExtension>()
    .named("libs")
    .findVersion("graalvm-java").orElseThrow().requiredVersion

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(graalJavaVersion)
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("--add-modules", "org.graalvm.webimage.api"))
}

dependencies {
    api(project(":providers:slang-wasm"))
    implementation(libs.slf4j.api)
}
