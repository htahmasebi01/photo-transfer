plugins {
    id("phototransfer.android.library")
    id("phototransfer.android.hilt")
}

android {
    namespace = "com.agiletech.android.phototransfer.data.media.impl"
}

dependencies {
    api(projects.data.media.api)

    implementation(projects.core.model)
    implementation(libs.javax.inject)
}
