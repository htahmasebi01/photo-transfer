plugins {
    id("phototransfer.android.library")
    id("phototransfer.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.agiletech.android.phototransfer.data.transfer.impl"
}

dependencies {
    api(projects.data.transfer.api)

    implementation(projects.core.coroutines)
    implementation(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.data.media.api)
    implementation(projects.data.pairing.api)

    implementation(libs.coroutines.core)
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okio)

    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
