plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

// Desktop sample of the Docking Grid library; not published. Run it with `gradlew -p docking-grid :sample:run`.
kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":docking-grid-material3"))
            implementation(project(":docking-grid-persistence"))
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.github.majorbolt.dockinggrid.sample.MainKt"
    }
}
