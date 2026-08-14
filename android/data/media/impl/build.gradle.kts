plugins {
    id("phototransfer.android.library")
    id("phototransfer.hilt")
}

android {
    namespace = "com.htahmasebi.phototransfer.data.media.impl"
}

dependencies {
    api(projects.data.media.api)

    implementation(projects.core.model)
    implementation(libs.javax.inject)
}
