plugins {
    id("phototransfer.android.library")
    id("phototransfer.android.hilt")
}

android {
    namespace = "com.agiletech.android.phototransfer.core.network"
}

dependencies {
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.okhttp.mockwebserver)
}
