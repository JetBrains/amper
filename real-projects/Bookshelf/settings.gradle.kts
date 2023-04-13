pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        // Only required for realm-kotlin snapshots
        maven("https://oss.sonatype.org/content/repositories/snapshots")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // Only required for realm-kotlin snapshots
        maven("https://oss.sonatype.org/content/repositories/snapshots")
    }
}

rootProject.name = "Bookshelf"
include(":androidApp")
include(":shared")
