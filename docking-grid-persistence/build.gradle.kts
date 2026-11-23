plugins {
    id("io.github.majorbolt.dockinggrid.kmp.library")
    id("io.github.majorbolt.dockinggrid.publishing")
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

description = "Converts the docking state to and from a versioned JSON document; storage is left to the host application."
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":docking-grid-core"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
