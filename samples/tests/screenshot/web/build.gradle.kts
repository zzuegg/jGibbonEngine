plugins {
    id("org.teavm") version "0.13.1"
}

val teavmVersion = "0.13.1"

// SLF4J is incompatible with TeaVM's classlib — teavm-windowing provides its own shim.
// Only exclude from main (TeaVM-compiled) configurations, not test (JVM) configurations.
configurations.matching { !it.name.startsWith("test") }.configureEach {
    exclude(group = "org.slf4j", module = "slf4j-api")
}

dependencies {
    // Main source set: TeaVM-compiled test app
    implementation(project(":samples:tests:screenshot:scenes"))
    implementation(project(":core"))
    implementation(project(":graphics:api"))
    implementation(project(":graphics:common"))
    implementation(project(":graphics:webgpu"))
    implementation(project(":providers:teavm-webgpu"))
    implementation(project(":providers:teavm-windowing"))
    implementation(project(":ui"))

    implementation("org.teavm:teavm-jso:$teavmVersion")
    implementation("org.teavm:teavm-jso-apis:$teavmVersion")
    implementation("org.teavm:teavm-interop:$teavmVersion")

    // Test source set: JUnit runner with CDP client (runs on JVM)
    testImplementation(project(":samples:tests:screenshot:scenes"))
    testImplementation(project(":graphics:api"))
    testImplementation(project(":graphics:common"))
    testRuntimeOnly("org.slf4j:slf4j-api:2.0.16")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.5.6")
}

teavm {
    js {
        mainClass = "dev.engine.tests.screenshot.web.WebTestApp"
        outputDir = layout.buildDirectory.dir("web")
        obfuscated = false
        targetFileName = "web-test.js"
    }
}

// Copy webapp resources and assets next to generated JS
tasks.register<Copy>("assembleWebTest") {
    dependsOn("generateJavaScript")
    from("src/main/webapp")
    into(layout.buildDirectory.dir("web"))

    // Copy shader files from graphics:common so they are served via HTTP
    from(project(":graphics:common").file("src/main/resources")) {
        into("assets")
    }

    // Copy Slang WASM files from the web platform if available
    val webPlatformWebapp = project(":platforms:web").file("src/main/webapp")
    if (webPlatformWebapp.exists()) {
        from(webPlatformWebapp) {
            include("slang/**")
        }
    }
}

tasks.register<JavaExec>("saveReferences") {
    group = "verification"
    description = "Captures screenshots from headless Chrome and saves as references"
    dependsOn("assembleWebTest")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.engine.tests.screenshot.web.SaveWebReferences"
    workingDir = projectDir
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.test {
    // Ensure the TeaVM app is built before running tests
    dependsOn("assembleWebTest")
    useJUnitPlatform()
    // The test JVM needs native access for ScreenshotHelper (ImageIO)
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    forkEvery = 1
    outputs.upToDateWhen { false }
    // Set working dir to project dir so build/web is findable
    workingDir = projectDir
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}
