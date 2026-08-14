plugins {
    id("phototransfer.android.library")
    id("phototransfer.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.htahmasebi.phototransfer.data.transfer.impl"
}

dependencies {
    api(projects.data.transfer.api)

    implementation(projects.core.coroutines)
    implementation(projects.core.model)
    implementation(projects.data.media.api)

    implementation(libs.coroutines.core)
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okio)

    testImplementation(libs.okhttp.mockwebserver)
}
