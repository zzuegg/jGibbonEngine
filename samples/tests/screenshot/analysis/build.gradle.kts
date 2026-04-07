dependencies {
    implementation(project(":samples:tests:screenshot:scenes"))
}

// ── Shared paths ────────────────────────────────────────────────────
val screenshotBuildDir = rootProject.layout.buildDirectory.dir("screenshots")
val screenshotParentDir = project.parent!!.projectDir
val referencesDir = screenshotParentDir.resolve("references")
val profile = project.findProperty("screenshot.profile")?.toString() ?: "local"

// ── Pipeline Pass 3: Compare ────────────────────────────────────────
tasks.register<JavaExec>("compare") {
    group = "verification"
    description = "Pass 3: Compare screenshots against references and cross-backend"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.engine.tests.screenshot.analysis.ScreenshotComparator"
    args = listOf(
        screenshotBuildDir.get().file("screenshot-report.json").asFile.absolutePath,
        screenshotBuildDir.get().asFile.absolutePath,
        referencesDir.resolve(profile).absolutePath
    )
    outputs.upToDateWhen { false }
    dependsOn(":samples:tests:screenshot:desktop-runner:runDesktop")
}

// ── Pipeline Pass 4: Report ─────────────────────────────────────────
tasks.register<JavaExec>("report") {
    group = "verification"
    description = "Pass 4: Generate HTML report from manifest"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.engine.tests.screenshot.analysis.ReportBuilder"
    args = listOf(
        screenshotBuildDir.get().file("screenshot-report.json").asFile.absolutePath,
        screenshotBuildDir.get().asFile.absolutePath,
        screenshotBuildDir.get().file("report.html").asFile.absolutePath
    )
    outputs.upToDateWhen { false }
    dependsOn("compare")
    doLast {
        val report = screenshotBuildDir.get().file("report.html").asFile
        println("Report: file://${report.absolutePath}")
    }
}

// ── Regression gate ─────────────────────────────────────────────────
tasks.register<JavaExec>("failOnRegression") {
    group = "verification"
    description = "Fail build if any screenshot comparison shows regression"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.engine.tests.screenshot.analysis.RegressionChecker"
    args = listOf(
        screenshotBuildDir.get().file("screenshot-report.json").asFile.absolutePath,
        referencesDir.resolve(profile).absolutePath
    )
    outputs.upToDateWhen { false }
    dependsOn("report")
}

// ── Lifecycle task ──────────────────────────────────────────────────
tasks.register("screenshotTest") {
    group = "verification"
    description = "Full screenshot test pipeline: collect -> run -> compare -> report -> check"
    dependsOn("failOnRegression")
}
