plugins {
    id("io.github.majorbolt.dockinggrid.kmp.library")
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

// Material 3 defaults for the dock host: a style taken from MaterialTheme and Material tab strip, header,
// placeholder and drag label.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":docking-grid-compose"))
            api(libs.jetbrains.compose.material3)
        }

        commonTest.dependencies {
            implementation(project(":docking-grid-test-fixtures"))
        }

        jvmTest.dependencies {
            implementation(libs.jetbrains.compose.ui.test)
            implementation(compose.desktop.currentOs)
        }
    }
}
