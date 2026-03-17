rootProject.name = "engine"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("gradle/libs.versions.toml"))
        }
    }
}

include("engine-math")
include("engine-core")
include("engine-platform")
include("engine-render")
include("engine-app")
include("sandbox")
