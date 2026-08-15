import common.libs
import common.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Hilt for plain JVM modules: they only declare bindings, so they need the core
 * runtime and processor without the Android plugin's bytecode transform.
 */
class JvmHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")

            dependencies {
                "implementation"(libs.library("hilt-core"))
                "ksp"(libs.library("hilt-compiler"))
            }
        }
    }
}
