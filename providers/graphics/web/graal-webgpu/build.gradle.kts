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
    api(project(":graphics:webgpu"))
    implementation(libs.slf4j.api)
}
