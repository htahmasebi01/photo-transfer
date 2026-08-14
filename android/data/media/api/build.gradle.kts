plugins {
    id("phototransfer.android.library")
}

android {
    namespace = "com.htahmasebi.phototransfer.data.media"
}

dependencies {
    api(projects.core.model)
}
