import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * Publishes a Docking Grid module with Gradle's built-in `maven-publish`. Kotlin Multiplatform creates one publication
 * per target plus the root module that Gradle uses to pick the right variant; this plugin adds the POM metadata
 * that repositories such as Maven Central require.
 *
 * Apply it only to modules that are part of the public library. The module's `description` becomes the POM
 * description. Publishing all Apple targets requires a macOS host; on other hosts they are skipped.
 */
class DockingGridPublishingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("maven-publish")

            extensions.configure<PublishingExtension> {
                publications.withType<MavenPublication>().configureEach {
                    pom {
                        name.set(provider { "Docking Grid: ${project.name.removePrefix("docking-grid-")}" })
                        description.set(provider { project.description ?: "Docking Grid library module ${project.name}." })
                        url.set(PROJECT_URL)
                        licenses {
                            license {
                                name.set("The Apache License, Version 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                                distribution.set("repo")
                            }
                        }
                        developers {
                            developer {
                                id.set("MajorBolt")
                                name.set("Marian Kondziella")
                                url.set(PROJECT_URL)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The publisher's GitHub profile; replace it with the repository URL once the library has its own repository. */
private const val PROJECT_URL = "https://github.com/MajorBolt"
