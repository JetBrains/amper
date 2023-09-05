plugins {
    // You can apply any plugins as usual.
    // Yet, android, multiplatform, apple plugins are bundled,
    // so you cant specify their version.
    // id("com.android.library") version 1.9.0 - this will lead to an error.
    id("com.android.library")
    kotlin("multiplatform")
}

kotlin {
    // No targets section.
    //
    // You can try to uncomment them, but it will likely lead to
    // build errors, since targets are already defined before this script
    // evaluation.
//    targets {
//        jvm()
//        androidTarget()
//    }

    // You can access all source sets configured by Deft plugin:
    sourceSets {
        val jvm by getting {
            println("Hello, Deft $name source set!")
        }
    }
}

// You can configure all other plugins also.
android {
    namespace = "com.example"
    compileSdkVersion = "android-34"
}

// You can do usual Gradle stuff here:
val customTask by tasks.creating {
    doLast {
        println("I'm custom task!")
    }
}