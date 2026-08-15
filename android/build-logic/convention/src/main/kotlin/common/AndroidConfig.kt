package common

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

const val COMPILE_SDK = 37
const val MIN_SDK = 29
const val TARGET_SDK = 36

fun Project.configureAndroid(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk = COMPILE_SDK

        defaultConfig.minSdk = MIN_SDK

        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        testOptions.unitTests.isReturnDefaultValues = true
    }

    // AGP 9 ships built-in Kotlin support, so no Kotlin plugin is applied here.
    configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    addUnitTestDependencies()
}
