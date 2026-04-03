dependencies {
    api(project(":graphics:webgpu"))
    api(project(":providers:teavm-windowing"))

    compileOnly("org.teavm:teavm-jso:0.13.1")
    compileOnly("org.teavm:teavm-jso-apis:0.13.1")
    compileOnly("org.teavm:teavm-interop:0.13.1")
}
