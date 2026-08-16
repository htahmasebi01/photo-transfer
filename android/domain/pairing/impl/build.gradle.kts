plugins {
    id("phototransfer.android.library")
    id("phototransfer.android.hilt")
}

android {
    namespace = "com.agiletech.android.phototransfer.domain.pairing.impl"
}

dependencies {
    api(projects.domain.pairing.api)

    implementation(projects.core.model)
    implementation(projects.data.pairing.api)

    implementation(libs.coroutines.core)
    implementation(libs.javax.inject)

    testImplementation(libs.coroutines.test)
}
