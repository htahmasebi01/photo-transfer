plugins {
    id("phototransfer.android.library")
    id("phototransfer.hilt")
}

android {
    namespace = "com.htahmasebi.phototransfer.data.discovery.impl"
}

dependencies {
    api(projects.data.discovery.api)

    implementation(projects.core.model)
    implementation(libs.coroutines.core)
    implementation(libs.javax.inject)
}
