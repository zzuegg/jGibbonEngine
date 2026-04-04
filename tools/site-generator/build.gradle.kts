plugins {
    application
}

dependencies {
    implementation(project(":core"))
}

// Source directories to scan for annotations
val tutorialSourceDir = project(":samples:tutorials").projectDir.resolve("src/main/java")
val exampleSourceDir = project(":samples:examples").projectDir.resolve("src/main/java")
val docsOutputDir = rootProject.projectDir.resolve("docs")

tasks.register<JavaExec>("generateModules") {
    group = "documentation"
    description = "Generates module pages from @EngineModule annotations on package-info.java"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.engine.sitegen.ModulePageGenerator"
    args = listOf(
        rootProject.projectDir.absolutePath,
        docsOutputDir.absolutePath
    )
}

tasks.register<JavaExec>("generateExamples") {
    group = "documentation"
    description = "Generates example pages from @Example annotations"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.engine.sitegen.ExamplePageGenerator"
    args = listOf(
        exampleSourceDir.absolutePath,
        docsOutputDir.absolutePath
    )
}

tasks.register<JavaExec>("generateTutorials") {
    group = "documentation"
    description = "Generates tutorial pages from @Tutorial annotations"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.engine.sitegen.TutorialGenerator"
    args = listOf(
        tutorialSourceDir.absolutePath,
        docsOutputDir.resolve("tutorials").absolutePath
    )
}

tasks.register<JavaExec>("generateLandingData") {
    group = "documentation"
    description = "Generates _data/stats.json and _data/modules.json for the landing page"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.engine.sitegen.LandingPageGenerator"
    args = listOf(
        docsOutputDir.absolutePath
    )
    dependsOn("generateModules", "generateExamples", "generateTutorials")
}

tasks.register("generateSite") {
    group = "documentation"
    description = "Generates the entire website from source annotations"
    dependsOn("generateLandingData")
}
