plugins {
    application
}

application {
    mainClass = "dev.engine.sandbox.Main"
}

dependencies {
    implementation(project(":engine-app"))
}

tasks.named<JavaExec>("run") {
    jvmArgs = listOf(
        "--enable-preview",
        "-XstartOnFirstThread" // required for macOS GLFW
    )
}
