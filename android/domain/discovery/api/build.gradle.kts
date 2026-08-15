plugins {
    id("phototransfer.android.library")
}

android {
    namespace = "com.agiletech.android.phototransfer.domain.discovery"
}

dependencies {
    api(projects.core.model)
    api(libs.coroutines.core)
}
