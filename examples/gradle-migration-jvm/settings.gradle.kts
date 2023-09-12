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
        classpath("org.jetbrains.deft.proto.settings.plugin:gradle-integration:150-NIGHTLY")
    }
}
// apply the plugin:
plugins.apply("org.jetbrains.deft.proto.settings.plugin")


rootProject.name = "my-project-name"

include(":lib")
include(":app")


