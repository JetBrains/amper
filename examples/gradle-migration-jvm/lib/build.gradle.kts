plugins {
    // Apply any plugins as usual.
    java

    // Note: The following plugins are preconfigured and their versions can't be changed:
    //   * `org.jetbrains.kotlin.multiplatform`
    //   * `org.jetbrains.kotlin.android`  ¡
    //   * `com.android.library`
    //   * `com.android.application`
    //   * `org.jetbrains.compose`
    //
    // The following code:
    //   `kotlin("multiplatform") version 1.9.0`
    // should be replaced with:
    //   `kotlin("multiplatform")
    //
    // Note: currently kotlin("jvm) plugin not supported in Deft Modules.
    // Replace
    //    `kotlin("jvm)`
    // with
    //    `kotlin("multiplatform")`
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
}

// Configure existing tasks or plugins
tasks.test {
    useJUnitPlatform()
}
