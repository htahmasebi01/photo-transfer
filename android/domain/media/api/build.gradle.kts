plugins {
    id("phototransfer.android.library")
}

android {
    namespace = "com.htahmasebi.phototransfer.domain.media"
}

dependencies {
    api(projects.core.model)
}
