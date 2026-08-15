plugins {
    id("phototransfer.android.library")
}

android {
    namespace = "com.agiletech.android.phototransfer.domain.media"
}

dependencies {
    api(projects.core.model)
}
