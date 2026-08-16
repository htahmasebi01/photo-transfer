plugins {
    id("phototransfer.android.library")
}

android {
    namespace = "com.agiletech.android.phototransfer.domain.pairing"
}

dependencies {
    api(projects.core.model)
}
