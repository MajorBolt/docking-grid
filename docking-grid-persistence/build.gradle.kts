plugins {
    id("io.github.majorbolt.dockinggrid.kmp.library")
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

// Converts the docking state to and from a versioned JSON document. Storage (files, DataStore, ...) is left to
// the host application.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":docking-grid-core"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
