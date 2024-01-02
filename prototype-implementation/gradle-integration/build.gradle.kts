
plugins {
    id("java-gradle-plugin")
}

amper.useAmperLayout = true

amperGradlePlugin()

gradlePlugin {
    plugins {
        create("amperProtoSettingsPlugin") {
            id = "org.jetbrains.amper.settings.plugin"
            implementationClass = "org.jetbrains.amper.gradle.BindingSettingsPlugin"
        }
    }
}

kotlin {
    sourceSets.named("jvm") {
        dependencies {
            implementation("org.jetbrains.kotlin:kotlin-serialization") {
                version {
                    // Should be replaced by synchVersions.sh
                    /*kotlin_magic_replacement*/ strictly("1.9.20")
                }
            }
        }
    }
}
