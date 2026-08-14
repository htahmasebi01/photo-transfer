plugins {
    id("phototransfer.android.library")
}

android {
    namespace = "com.htahmasebi.phototransfer.domain.discovery"
}

dependencies {
    api(projects.core.model)
    api(libs.coroutines.core)
}
