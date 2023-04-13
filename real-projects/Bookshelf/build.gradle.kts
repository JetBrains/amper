plugins {
    kotlin("multiplatform") version "1.7.20" apply false
    id("com.android.library") version "7.3.0" apply false
}

// Explicitly adding the plugin to the classpath as it makes it easier to control the version
// centrally (don't need version in the 'plugins' block). Further, snapshots are not published with
// marker interface so would need to be added to the classpath manually anyway.
buildscript {
    dependencies {
        classpath("io.realm.kotlin:gradle-plugin:1.6.0")
    }
}
rootProject.extra["realmVersion"] = "1.6.0"

allprojects {
    group = "io.realm.sample.bookshelf"
    version = "1.0.0"
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
