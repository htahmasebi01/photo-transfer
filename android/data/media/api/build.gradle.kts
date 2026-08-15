plugins {
    id("phototransfer.android.library")
}

android {
    namespace = "com.agiletech.android.phototransfer.data.media"
}

dependencies {
    api(projects.core.model)
}
