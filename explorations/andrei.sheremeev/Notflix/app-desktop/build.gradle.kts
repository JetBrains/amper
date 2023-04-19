plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.compose.desktop.plugin)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)

    implementation(libs.voyager.core)
    implementation(libs.voyager.navigator)
    implementation(libs.voyager.tabNavigator)

    implementation(libs.kamel)

    testImplementation(kotlin("test"))
    testImplementation(libs.turbine)
    testImplementation(libs.ktor.mock)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinX.coroutines.test)
}

compose.desktop {
    application {
        mainClass = "NotflixDesktopKt"
    }

    /*nativeDistributions {
        targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
    }*/
}
