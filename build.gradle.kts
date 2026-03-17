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
        "testImplementation"(catalog.findBundle("testing").orElseThrow())
    }

    tasks.test {
        useJUnitPlatform()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
