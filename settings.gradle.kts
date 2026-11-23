rootProject.name = "docking-grid"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
    versionCatalogs {
        // Shared with the host project while the library lives in this repository. A standalone
        // repository would ship its own copy of the catalog.
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

include(":docking-grid-core")
include(":docking-grid-persistence")
include(":docking-grid-compose")
include(":docking-grid-material3")

// Not published: dockables shared by the tests of the UI modules, and a desktop sample.
include(":docking-grid-test-fixtures")
include(":sample")
