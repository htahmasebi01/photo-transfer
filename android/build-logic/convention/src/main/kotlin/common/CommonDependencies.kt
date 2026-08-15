package common

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.library(alias: String) = findLibrary(alias).get()

fun Project.addUnitTestDependencies() {
    dependencies {
        "testImplementation"(libs.library("junit"))
        "testImplementation"(libs.library("mockito-kotlin"))
        "testImplementation"(libs.library("coroutines-test"))
    }
}
