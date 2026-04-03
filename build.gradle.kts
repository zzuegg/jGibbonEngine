import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    kotlin("jvm") version "2.0.0"
    id("org.jetbrains.dokka") version "1.9.20"
    `maven-publish`
}

group   = "io.github.zzuegg"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // ---- Core dependencies (placeholders — fill in as the engine grows) ----
    implementation(kotlin("stdlib"))

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.13")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.6")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

// ---------------------------------------------------------------------------
// Dokka — API documentation
// ---------------------------------------------------------------------------

tasks.withType<DokkaTask>().configureEach {
    outputDirectory.set(layout.buildDirectory.dir("dokka/html"))

    dokkaSourceSets {
        named("main") {
            moduleName.set("jGibbonEngine")
            moduleVersion.set(project.version.toString())

            sourceLink {
                localDirectory.set(projectDir.resolve("src/main/kotlin"))
                remoteUrl.set(
                    uri("https://github.com/zzuegg/jGibbonEngine/tree/main/src/main/kotlin").toURL()
                )
                remoteLineSuffix.set("#L")
            }

            // Suppress internal packages from the public API docs
            perPackageOption {
                matchingRegex.set(".*\\.internal.*")
                suppress.set(true)
            }
        }
    }
}

// Convenience task: generate Dokka HTML and copy output into docs/javadoc/api/
// so GitHub Pages can serve it directly.
tasks.register<Copy>("copyDokkaToPages") {
    dependsOn("dokkaHtml")
    from(layout.buildDirectory.dir("dokka/html"))
    into(layout.projectDirectory.dir("docs/javadoc/api"))
    description = "Copies Dokka HTML output into the docs/javadoc/api/ folder for GitHub Pages."
    group       = "documentation"
}

// ---------------------------------------------------------------------------
// Maven publication (for future publishing to Maven Central / GitHub Packages)
// ---------------------------------------------------------------------------

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set("jGibbonEngine")
                description.set(
                    "A modern, high-performance 3D game engine for the JVM — " +
                    "spiritual successor of jMonkeyEngine."
                )
                url.set("https://zzuegg.github.io/jGibbonEngine")

                licenses {
                    license {
                        name.set("BSD 3-Clause License")
                        url.set("https://opensource.org/licenses/BSD-3-Clause")
                    }
                }

                developers {
                    developer {
                        id.set("zzuegg")
                        name.set("zzuegg")
                        url.set("https://github.com/zzuegg")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/zzuegg/jGibbonEngine.git")
                    developerConnection.set("scm:git:ssh://github.com/zzuegg/jGibbonEngine.git")
                    url.set("https://github.com/zzuegg/jGibbonEngine")
                }
            }
        }
    }
}
