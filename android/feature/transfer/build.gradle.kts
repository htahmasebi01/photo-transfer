plugins {
    id("phototransfer.android.library")
    id("phototransfer.android.compose")
    id("phototransfer.android.hilt")
}

android {
    namespace = "com.agiletech.android.phototransfer.feature.transfer"
}

dependencies {
    // Features depend on domain contracts only; implementations are wired by :app.
    implementation(projects.core.model)
    implementation(projects.domain.discovery.api)
    implementation(projects.domain.media.api)
    implementation(projects.domain.transfer.api)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.text)
    implementation(libs.compose.ui.unit)
    implementation(libs.coroutines.core)
    implementation(libs.javax.inject)
}
