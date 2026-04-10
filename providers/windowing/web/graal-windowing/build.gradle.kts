val graalJavaVersion = rootProject.extensions
    .getByType<VersionCatalogsExtension>()
    .named("libs")
    .findVersion("graalvm-java").orElseThrow().requiredVersion

val graalVmHome = rootProject.extra["graalVmHome"] as String?

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(graalJavaVersion)
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("--add-modules", "org.graalvm.webimage.api"))
    if (graalVmHome != null) {
        // Override Gradle's toolchain-picked javac with the detected GraalVM
        // install (see root build.gradle.kts / docs/graalwasm-toolchain.md).
        options.isFork = true
        options.forkOptions.javaHome = file(graalVmHome)
    }
}

dependencies {
    api(project(":graphics:api"))
    api(project(":core"))
    implementation(libs.slf4j.api)
}
