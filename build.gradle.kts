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
