rootProject.name = "deviceai-runtime-kmp"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":runtime-core")
include(":runtime-speech")
include(":samples:composeApp")
