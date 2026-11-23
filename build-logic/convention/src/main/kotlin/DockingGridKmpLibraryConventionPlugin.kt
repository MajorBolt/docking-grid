import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Common setup of every Docking Grid library module: Kotlin Multiplatform with the targets the library supports
 * (Android, JVM desktop, iOS), explicit API mode, the library coordinates from `gradle.properties`, and kotlin-test.
 *
 * The Android namespace is derived from the module name: `docking-grid-core` becomes `io.github.majorbolt.dockinggrid.core`.
 */
class DockingGridKmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("com.android.kotlin.multiplatform.library")
            }

            group = providers.gradleProperty("GROUP").get()
            version = providers.gradleProperty("VERSION_NAME").get()

            val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

            extensions.configure<KotlinMultiplatformExtension> {
                // Every public declaration is a deliberate part of the library API.
                explicitApi()

                jvm {
                    compilerOptions {
                        jvmTarget.set(JvmTarget.JVM_17)
                    }
                }
                iosArm64()
                iosSimulatorArm64()

                extensions.configure<KotlinMultiplatformAndroidLibraryExtension> {
                    namespace = "io.github.majorbolt.dockinggrid." + name.removePrefix("docking-grid-").replace('-', '.')
                    compileSdk = catalog.intVersion("android-compile-sdk")
                    minSdk = catalog.intVersion("android-min-sdk")
                }

                sourceSets.getByName("commonTest").dependencies {
                    implementation(catalog.findLibrary("kotlin-test").get())
                }
            }
        }
    }
}

private fun VersionCatalog.intVersion(alias: String): Int = findVersion(alias).get().requiredVersion.toInt()
