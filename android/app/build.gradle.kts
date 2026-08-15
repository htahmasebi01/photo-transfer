plugins {
    id("phototransfer.android.application")
    id("phototransfer.android.compose")
    id("phototransfer.android.hilt")
}

android {
    namespace = "com.agiletech.android.phototransfer"

    defaultConfig {
        applicationId = "com.agiletech.android.phototransfer"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(projects.feature.transfer)

    // The composition root is the only place that knows about implementations.
    // Hilt aggregates @InstallIn modules from the compile classpath, so these
    // must be `implementation` rather than `runtimeOnly`.
    implementation(projects.core.coroutines)
    implementation(projects.data.discovery.impl)
    implementation(projects.data.media.impl)
    implementation(projects.data.transfer.impl)
    implementation(projects.domain.discovery.impl)
    implementation(projects.domain.media.impl)
    implementation(projects.domain.transfer.impl)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.material3)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.coroutines.android)
}
