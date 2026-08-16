plugins {
    id("phototransfer.android.library")
}

android {
    namespace = "com.agiletech.android.phototransfer.data.pairing"
}

dependencies {
    api(projects.core.model)
}
