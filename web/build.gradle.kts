plugins {
    id("org.teavm") version "0.13.1"
}

val teavmVersion = "0.13.1"

configurations.all {
    // SLF4J uses SecurityManager, LinkedBlockingQueue, and ClassLoader.getResources()
    // — none of which exist in TeaVM's classlib. We provide our own minimal shim
    // in web/src/main/java/org/slf4j/ that routes to console.log instead.
    exclude(group = "org.slf4j", module = "slf4j-api")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":graphics:api"))
    implementation(project(":graphics:common"))
    implementation(project(":graphics:webgpu"))
    implementation(project(":providers:teavm-webgpu"))
    implementation(project(":providers:teavm-windowing"))

    implementation("org.teavm:teavm-jso:$teavmVersion")
    implementation("org.teavm:teavm-jso-apis:$teavmVersion")
    implementation("org.teavm:teavm-interop:$teavmVersion")
}

teavm {
    js {
        mainClass = "dev.engine.web.WebMain"
        outputDir = layout.buildDirectory.dir("generated/js/teavm").get().asFile
        // Obfuscated output is smaller but harder to debug; disable for now
        obfuscated = false
    }
}

// Copy webapp resources (index.html, slang-wasm) and assets next to generated JS
tasks.register<Copy>("assembleWeb") {
    dependsOn("generateJavaScript")
    from("src/main/webapp")
    into(layout.buildDirectory.dir("generated/js/teavm/js"))

    // Copy shader files from graphics:common so they are served via HTTP
    from(project(":graphics:common").file("src/main/resources")) {
        into("assets")
    }
}

tasks.named("generateJavaScript") {
    finalizedBy("assembleWeb")
}
