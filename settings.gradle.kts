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
    // The version catalog gradle/libs.versions.toml is imported as `libs` by Gradle's convention.
}

include(":docking-grid-core")
include(":docking-grid-persistence")
include(":docking-grid-compose")
include(":docking-grid-material3")

// Not published: dockables shared by the tests of the UI modules, and a desktop sample.
include(":docking-grid-test-fixtures")
include(":sample")
