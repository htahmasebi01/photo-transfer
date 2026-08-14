import com.android.build.api.dsl.CommonExtension
import common.libs
import common.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.findByType

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            val extension = requireNotNull(extensions.findByType<CommonExtension>()) {
                "phototransfer.android.compose requires an Android plugin to be applied first"
            }
            extension.buildFeatures.compose = true

            dependencies {
                "implementation"(platform(libs.library("compose-bom")))
                "debugImplementation"(libs.library("compose-ui-tooling"))
            }
        }
    }
}
