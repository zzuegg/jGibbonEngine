val lwjglNatives = when {
    System.getProperty("os.name").startsWith("Windows") -> "natives-windows"
    System.getProperty("os.name").startsWith("Mac") -> if (System.getProperty("os.arch") == "aarch64") "natives-macos-arm64" else "natives-macos"
    else -> "natives-linux"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":graphics:api"))
    implementation(project(":graphics:common"))
    implementation(project(":graphics:opengl"))
    implementation(project(":platforms:desktop"))
    implementation(project(":providers:lwjgl-gl"))
    implementation(project(":providers:slang"))
    implementation(project(":providers:lwjgl-glfw"))
    implementation(platform(libs.lwjgl.bom))
    implementation(libs.lwjgl.opengl)
    implementation(libs.lwjgl.glfw)

    runtimeOnly(libs.lwjgl.core.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.lwjgl.opengl.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.lwjgl.glfw.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.slf4j.api)
    runtimeOnly("ch.qos.logback:logback-classic:1.5.6")
}

tasks.register<JavaExec>("generateTutorials") {
    group = "documentation"
    description = "Generates Jekyll markdown pages from annotated tutorial Java sources"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.engine.tutorials.TutorialGenerator"
    args = listOf(
        projectDir.resolve("src/main/java").absolutePath,
        rootProject.projectDir.resolve("docs/tutorials").absolutePath
    )
}
