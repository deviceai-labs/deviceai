rootProject.name = "deviceai"

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

include(":kotlin:core")
include(":kotlin:speech")
include(":kotlin:llm")
include(":samples:androidApp")
