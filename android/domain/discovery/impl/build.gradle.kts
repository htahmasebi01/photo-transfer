plugins {
    id("phototransfer.android.library")
    id("phototransfer.android.hilt")
}

android {
    namespace = "com.agiletech.android.phototransfer.domain.discovery.impl"
}

dependencies {
    api(projects.domain.discovery.api)

    implementation(projects.core.model)
    implementation(projects.data.discovery.api)

    implementation(libs.coroutines.core)
    implementation(libs.javax.inject)
}
