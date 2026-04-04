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
        val jemalloc = file("/lib/x86_64-linux-gnu/libjemalloc.so.2")
        if (jemalloc.exists()) {
            environment("LD_PRELOAD", jemalloc.absolutePath)
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
