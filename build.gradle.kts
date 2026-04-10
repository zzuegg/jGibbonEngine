import java.io.File as JFile

plugins {
    java
}

allprojects {
    group = "dev.engine"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

// ── GraalVM installation detection ──────────────────────────────────────
// Gradle's toolchain selection cannot distinguish Oracle GraalVM from regular
// Oracle JDK when both have the same language version — both report
// vendor=Oracle. To let graal-* modules compile reliably regardless of which
// Java 26 JDK is SDKMAN's current default, we scan the filesystem here and
// expose the detected GraalVM install path to subprojects via
// rootProject.extra["graalVmHome"]. Subprojects then use it to override
// JavaCompile.forkOptions.javaHome, bypassing the toolchain-picked javac.
//
// Priority order:
//   1. $GRAALVM_HOME environment variable
//   2. Latest ~/.sdkman/candidates/java/*-graal with a valid bin/javac
// Returns null if no GraalVM was found — subprojects fall back to default.
//
// See docs/graalwasm-toolchain.md for the full rationale.
val detectedGraalVmHome: String? = run {
    val envHome = System.getenv("GRAALVM_HOME")
    if (envHome != null && JFile(envHome, "bin/javac").exists()) {
        return@run envHome
    }
    val sdkman = JFile(System.getProperty("user.home"), ".sdkman/candidates/java")
    if (!sdkman.isDirectory) return@run null
    val entries: Array<JFile> = sdkman.listFiles() ?: return@run null
    val graalDirs: List<JFile> = entries
        .filter { f: JFile -> f.isDirectory && f.name.endsWith("-graal") }
        .sortedByDescending { f: JFile -> f.name }
    for (dir in graalDirs) {
        if (JFile(dir, "bin/javac").exists()) {
            return@run dir.absolutePath
        }
    }
    null
}
rootProject.extra["graalVmHome"] = detectedGraalVmHome
if (detectedGraalVmHome != null) {
    logger.lifecycle("GraalVM detected at: $detectedGraalVmHome")
} else {
    logger.info("No GraalVM detected — graal-* modules will fall back to default toolchain selection")
}

// ── Aggregated Javadoc ──────────────────────────────────────────────────
val javadocModules = listOf(
    ":core",
    ":graphics:api",
    ":graphics:common",
    ":graphics:opengl",
    ":graphics:vulcan",
    ":graphics:webgpu"
)

tasks.register<Javadoc>("javadocAll") {
    group = "documentation"
    description = "Generates aggregated Javadoc for all public modules"

    val catalog = rootProject.extensions
        .getByType<VersionCatalogsExtension>()
        .named("libs")
    val javaVersion = catalog.findVersion("java").orElseThrow().requiredVersion

    val javadocProjects = javadocModules.map { project(it) }

    source = files(javadocProjects.map { it.sourceSets.main.get().allJava }).asFileTree
    classpath = files(javadocProjects.map { it.sourceSets.main.get().compileClasspath })

    setDestinationDir(file("docs/javadoc"))

    javadocTool = javaToolchains.javadocToolFor {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }

    options {
        this as StandardJavadocDocletOptions
        encoding = "UTF-8"
        docEncoding = "UTF-8"
        charSet = "UTF-8"
        windowTitle = "jGibbonEngine API"
        docTitle = "jGibbonEngine API Documentation"
        header = "<a href='https://zzuegg.github.io/jGibbonEngine'>jGibbonEngine</a>"
        addStringOption("Xdoclint:none", "-quiet")
        addBooleanOption("-enable-preview", true)
        addStringOption("source", javaVersion)
        addStringOption("-add-stylesheet", file("tools/site-generator/src/main/resources/javadoc-theme.css").absolutePath)
        links("https://docs.oracle.com/en/java/javase/25/docs/api/")
    }

    javadocProjects.forEach { dependsOn("${it.path}:classes") }
}

subprojects {
    // Skip java-library for grouping-only projects (e.g. :graphics)
    if (childProjects.isNotEmpty() && projectDir.resolve("src").exists().not()) return@subprojects

    apply(plugin = "java-library")

    val catalog = rootProject.extensions
        .getByType<VersionCatalogsExtension>()
        .named("libs")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(
                catalog.findVersion("java").orElseThrow().requiredVersion
            )
        }
    }

    dependencies {
        "testImplementation"(platform(catalog.findLibrary("junit-bom").orElseThrow()))
        "testImplementation"(catalog.findLibrary("junit-jupiter").orElseThrow())
        "testRuntimeOnly"(catalog.findLibrary("junit-launcher").orElseThrow())
    }

    tasks.test {
        useJUnitPlatform()
        jvmArgs("--enable-native-access=ALL-UNNAMED")

        // Slang's C++ runtime can trigger glibc heap metadata corruption on
        // certain glibc versions when COM objects are released (free/delete).
        // Using jemalloc as a drop-in replacement avoids the issue.
        val jemallocPaths = listOf(
            "/lib/x86_64-linux-gnu/libjemalloc.so.2",
            "/usr/lib/libjemalloc.so.2",
            "/usr/lib64/libjemalloc.so.2",
            "/opt/homebrew/lib/libjemalloc.dylib"
        )
        jemallocPaths.map { file(it) }.firstOrNull { it.exists() }?.let {
            environment("LD_PRELOAD", it.absolutePath)
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}

// ── Screenshot tests: ./gradlew screenshotTest ──────────────────────
tasks.register("screenshotTest") {
    group = "verification"
    description = "Runs the full screenshot regression test pipeline"
    dependsOn(":samples:tests:screenshot:analysis:screenshotTest")
}

// ── testAll: unit tests + screenshot tests ──────────────────────────
tasks.register("testAll") {
    group = "verification"
    description = "Runs all unit tests and screenshot regression tests"
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("test") })
    dependsOn("screenshotTest")
}
