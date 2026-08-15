plugins {
    id("phototransfer.android.library")
    id("phototransfer.android.hilt")
}

android {
    namespace = "com.htahmasebi.phototransfer.domain.transfer.impl"
}

dependencies {
    api(projects.domain.transfer.api)

    implementation(projects.core.coroutines)
    implementation(projects.core.model)
    implementation(projects.data.transfer.api)

    implementation(libs.coroutines.core)
    implementation(libs.javax.inject)
}
