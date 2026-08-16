plugins {
    id("phototransfer.android.library")
    id("phototransfer.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.agiletech.android.phototransfer.data.pairing.impl"
}

dependencies {
    api(projects.data.pairing.api)

    implementation(projects.core.coroutines)
    implementation(projects.core.model)
    implementation(projects.core.network)

    implementation(libs.coroutines.core)
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    // The keystore has no JVM implementation, so AndroidPairingLocalStore is only
    // verifiable on a device.
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.kluent)
}
