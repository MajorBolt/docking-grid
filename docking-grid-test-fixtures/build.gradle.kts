plugins {
    id("io.github.majorbolt.dockinggrid.kmp.library")
}

// Test data for the UI modules' tests. Not published.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":docking-grid-core"))
        }
    }
}
