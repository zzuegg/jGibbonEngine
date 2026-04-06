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

tasks.register("generateReport") {
    group = "verification"
    description = "Generates an HTML report comparing captured vs reference screenshots"
    doLast {
        val captureDir = file("build/screenshots/webgpu-browser")
        val refDir = file("src/test/resources/reference-screenshots/webgpu-browser")
        val report = file("build/screenshots/report.html")
        report.parentFile.mkdirs()

        val captures = captureDir.listFiles { f -> f.extension == "png" }
            ?.sortedBy { it.name } ?: emptyList()

        val html = buildString {
            appendLine("""<!DOCTYPE html><html><head><meta charset="utf-8">
            <title>Web Screenshot Report</title>
            <style>
                body { font-family: system-ui; background: #1a1a2e; color: #e0e0e0; padding: 20px; }
                h1 { border-bottom: 2px solid #333; padding-bottom: 10px; }
                .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 16px; }
                .card { background: #16213e; border-radius: 8px; padding: 12px; }
                .card h3 { margin: 0 0 8px; font-size: 0.9em; }
                .images { display: flex; gap: 8px; }
                .images div { text-align: center; }
                .images img { width: 128px; height: 128px; border: 1px solid #333; border-radius: 4px; background: #0a0a1a; }
                .label { font-size: 0.7em; color: #888; }
                .pass { color: #4ade80; } .fail { color: #f87171; } .skip { color: #888; }
            </style></head><body>
            <h1>Web Screenshot Report (${captures.size} scenes)</h1><div class="grid">""")

            for (cap in captures) {
                val name = cap.nameWithoutExtension
                val refFile = File(refDir, cap.name)
                val hasRef = refFile.exists()
                appendLine("""<div class="card"><h3>${name} <span class="${if (hasRef) "pass" else "skip"}">${if (hasRef) "✓ ref" else "○ no ref"}</span></h3>""")
                appendLine("""<div class="images">""")
                appendLine("""<div><img src="webgpu-browser/${cap.name}"><div class="label">Captured</div></div>""")
                if (hasRef) {
                    appendLine("""<div><img src="file://${refFile.absolutePath}"><div class="label">Reference</div></div>""")
                }
                appendLine("</div></div>")
            }
            appendLine("</div></body></html>")
        }
        report.writeText(html)
        println("Report: file://${report.absolutePath}")
    }
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
