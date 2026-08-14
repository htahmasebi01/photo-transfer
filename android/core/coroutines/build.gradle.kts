plugins {
    id("phototransfer.android.library")
    id("phototransfer.hilt")
}

android {
    namespace = "com.htahmasebi.phototransfer.core.coroutines"
}

dependencies {
    api(libs.coroutines.core)
    implementation(libs.javax.inject)
}
