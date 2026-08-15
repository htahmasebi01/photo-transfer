plugins {
    id("phototransfer.android.library")
    id("phototransfer.android.hilt")
}

android {
    namespace = "com.agiletech.android.phototransfer.domain.media.impl"
}

dependencies {
    api(projects.domain.media.api)

    implementation(projects.core.coroutines)
    implementation(projects.core.model)
    implementation(projects.data.media.api)

    implementation(libs.coroutines.core)
    implementation(libs.javax.inject)
}
