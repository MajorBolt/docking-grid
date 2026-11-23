plugins {
    id("io.github.majorbolt.dockinggrid.kmp.library")
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

// The dock host for Compose Multiplatform: rendering, pointer interaction, resize cursors and the style and slot
// API. Depends on Compose foundation only, so it can be used with any design system.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":docking-grid-core"))
            api(libs.jetbrains.compose.runtime)
            api(libs.jetbrains.compose.ui)
            api(libs.jetbrains.compose.foundation)
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
