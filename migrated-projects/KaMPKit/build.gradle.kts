plugins {
//    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.sqlDelight) apply false
    alias(libs.plugins.skie) apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://androidx.dev/storage/compose-compiler/repository/")
    }
}

// fails in tests, but brings no value for Amper
//subprojects {
//    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
//
//    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
//        version.set("1.0.0")
//        enableExperimentalRules.set(true)
//        verbose.set(true)
//        filter {
//            exclude { it.file.path.contains("build/") }
//        }
//    }
//
//    afterEvaluate {
//        tasks.named("check").configure {
//            dependsOn(tasks.getByName("ktlintCheck"))
//        }
//    }
//}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
