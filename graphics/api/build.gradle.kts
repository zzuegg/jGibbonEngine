dependencies {
    api(project(":core"))
    annotationProcessor(project(":core-processor"))
    testImplementation(project(":graphics:common"))
    testImplementation(project(":providers:slang"))
}
