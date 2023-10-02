buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        
        // add repositories:
        google()
        maven("https://packages.jetbrains.team/maven/p/deft/deft-prototype")
    }

    // add plugin classpath:
    dependencies {
        classpath("org.jetbrains.deft.proto.settings.plugin:gradle-integration:178-NIGHTLY")
    }
}

// apply the plugin:
plugins.apply("org.jetbrains.deft.proto.settings.plugin")