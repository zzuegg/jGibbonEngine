dependencies {
    api(project(":graphics:api"))
    api(project(":core"))

    compileOnly("org.teavm:teavm-jso:0.13.1")
    compileOnly("org.teavm:teavm-jso-apis:0.13.1")
    compileOnly("org.teavm:teavm-interop:0.13.1")
    // TeaVM classlib needed to compile our T-prefixed shims (TCleaner, etc.)
    compileOnly("org.teavm:teavm-classlib:0.13.1")
}
