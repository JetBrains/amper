buildscript {
    repositories {
        maven("https://jitpack.io")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        mavenCentral()
        google()
        jcenter()
        gradlePluginPortal()
        maven("https://packages.jetbrains.team/maven/p/deft/deft-prototype")
    }

    dependencies {
        // !!! Use syncVersions.kts to update these versions
        classpath("org.jetbrains.deft.proto.settings.plugin:gradle-integration:103-NIGHTLY")
    }
}

plugins.apply("org.jetbrains.deft.proto.settings.plugin")