pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "photo-transfer"

include(":app")

include(":feature:transfer")

include(":domain:discovery:api")
include(":domain:discovery:impl")
include(":domain:media:api")
include(":domain:media:impl")
include(":domain:pairing:api")
include(":domain:pairing:impl")
include(":domain:transfer:api")
include(":domain:transfer:impl")

include(":data:discovery:api")
include(":data:discovery:impl")
include(":data:media:api")
include(":data:media:impl")
include(":data:pairing:api")
include(":data:pairing:impl")
include(":data:transfer:api")
include(":data:transfer:impl")

include(":core:coroutines")
include(":core:model")
include(":core:network")
