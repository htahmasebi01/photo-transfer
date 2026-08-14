plugins {
    id("phototransfer.android.library")
}

android {
    namespace = "com.htahmasebi.phototransfer.domain.transfer"
}

dependencies {
    api(projects.core.model)
    api(libs.coroutines.core)
}
