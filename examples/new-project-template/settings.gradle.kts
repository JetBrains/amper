pluginManagement {
    includeBuild("../../prototype-implementation")
}
plugins {
    id("org.jetbrains.deft.proto.settings.plugin")
}

//buildscript {
//    repositories {
//        maven("https://jitpack.io")
//        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
//        mavenCentral()
//        google()
//        jcenter()
//        gradlePluginPortal()
//        maven("https://packages.jetbrains.team/maven/p/deft/deft-prototype")
//    }
//
//    dependencies {
//        // !!! Use syncVersions.kts to update these versions
//        classpath("org.jetbrains.deft.proto.settings.plugin:gradle-integration:1.2.9")
//    }
//}
//
//plugins.apply("org.jetbrains.deft.proto.settings.plugin")