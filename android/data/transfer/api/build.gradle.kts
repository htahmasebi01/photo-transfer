plugins {
    id("phototransfer.android.library")
}

android {
    namespace = "com.htahmasebi.phototransfer.data.transfer"
}

dependencies {
    api(projects.core.model)
}
