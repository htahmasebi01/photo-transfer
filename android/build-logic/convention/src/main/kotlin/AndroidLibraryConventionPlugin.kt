import com.android.build.api.dsl.LibraryExtension
import common.configureAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            configureAndroid(extensions.getByType<LibraryExtension>())
        }
    }
}
