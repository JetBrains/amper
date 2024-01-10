plugins {
    `java-gradle-plugin`
}

amperGradlePlugin()

gradlePlugin {
    plugins {
        create("amperGradleAndroidPlugin") {
            id = "org.jetbrains.amper.android.settings.plugin"
            implementationClass = "AmperAndroidIntegrationSettingsPlugin"
        }
    }
}