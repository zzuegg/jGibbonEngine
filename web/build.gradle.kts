plugins {
    id("org.teavm") version "0.13.1"
}

val teavmVersion = "0.13.1"

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
