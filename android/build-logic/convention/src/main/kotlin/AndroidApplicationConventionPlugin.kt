import com.android.build.api.dsl.ApplicationExtension
import common.TARGET_SDK
import common.configureAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            val extension = extensions.getByType<ApplicationExtension>()
            configureAndroid(extension)
            extension.defaultConfig.targetSdk = TARGET_SDK
        }
    }
}
