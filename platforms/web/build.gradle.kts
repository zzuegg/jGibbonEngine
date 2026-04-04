plugins {
    id("org.teavm") version "0.13.1"
}

val teavmVersion = "0.13.1"

configurations.all {
    // SLF4J uses SecurityManager, LinkedBlockingQueue, and ClassLoader.getResources()
    // — none of which exist in TeaVM's classlib. We provide our own minimal shim
    // in teavm-windowing that routes to console.log instead.
    exclude(group = "org.slf4j", module = "slf4j-api")
}

dependencies {
    api(project(":core"))
    api(project(":graphics:api"))
    api(project(":graphics:common"))
    api(project(":graphics:webgpu"))
    implementation(project(":providers:teavm-webgpu"))
    implementation(project(":providers:teavm-windowing"))

    implementation("org.teavm:teavm-jso:$teavmVersion")
    implementation("org.teavm:teavm-jso-apis:$teavmVersion")
    implementation("org.teavm:teavm-interop:$teavmVersion")
}

// Copy webapp resources (index.html, slang-wasm) and assets next to generated JS
tasks.register<Copy>("assembleWeb") {
    from("src/main/webapp")
    into(layout.buildDirectory.dir("generated/js/teavm/js"))

    // Copy shader files from graphics:common so they are served via HTTP
    from(project(":graphics:common").file("src/main/resources")) {
        into("assets")
    }
}
