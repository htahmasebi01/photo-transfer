import java.util.Properties

plugins {
    id("phototransfer.android.application")
    id("phototransfer.android.compose")
    id("phototransfer.android.hilt")
}

val keystoreFileName = "keystore.properties"

android {
    namespace = "com.agiletech.android.phototransfer"

    defaultConfig {
        applicationId = "com.agiletech.android.phototransfer"
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        releaseKeystore()?.let { keystore ->
            create("release") {
                storeFile = rootProject.file(keystore.required("storeFile"))
                storePassword = keystore.required("storePassword")
                keyAlias = keystore.required("keyAlias")
                keyPassword = keystore.required("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Null on a machine without signing keys, which yields an
            // app-release-unsigned.apk that cannot be installed. See docs/sharing.md.
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

dependencies {
    implementation(projects.feature.transfer)

    // The composition root is the only place that knows about implementations.
    // Hilt aggregates @InstallIn modules from the compile classpath, so these
    // must be `implementation` rather than `runtimeOnly`.
    implementation(projects.core.coroutines)
    implementation(projects.core.network)
    implementation(projects.data.discovery.impl)
    implementation(projects.data.media.impl)
    implementation(projects.data.pairing.impl)
    implementation(projects.data.transfer.impl)
    implementation(projects.domain.discovery.impl)
    implementation(projects.domain.media.impl)
    implementation(projects.domain.pairing.impl)
    implementation(projects.domain.transfer.impl)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.material3)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.coroutines.android)
}

/** Reads `android/keystore.properties`, which is deliberately outside version control. */
fun releaseKeystore(): Properties? {
    val file = rootProject.file(keystoreFileName)
    if (!file.exists()) return null
    return Properties().apply { file.inputStream().use(::load) }
}

fun Properties.required(name: String): String =
    getProperty(name) ?: error("$keystoreFileName is missing '$name'")
