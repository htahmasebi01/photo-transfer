plugins {
    id("phototransfer.android.library")
}

android {
    namespace = "com.agiletech.android.phototransfer.data.transfer"
}

dependencies {
    api(projects.core.model)
}
