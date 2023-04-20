plugins {
    kotlin("jvm") version "1.7.21"
    `java-gradle-plugin`
    `maven-publish`
}

group = "org.jetbrains.deft.proto.settings.plugin"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":frontend-api"))
    implementation("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:1.8.0")
    implementation("org.jetbrains.kotlin.android:org.jetbrains.kotlin.android.gradle.plugin:1.8.0")
    implementation("com.android.library:com.android.library.gradle.plugin:7.4.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.7.21")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.2")
}

gradlePlugin {
    plugins {
        create("deftProtoSettingsPlugin") {
            id = "org.jetbrains.deft.proto.settings.plugin"
            implementationClass = "org.jetbrains.deft.proto.gradle.BindingSettingsPlugin"
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    kotlinTestRegistry
}

// Add also this tests classes as plugin classpath for running plugin.
tasks.withType<PluginUnderTestMetadata>().configureEach {
    dependsOn("compileTestKotlin")
    dependsOn("processTestResources")
    pluginClasspath.setFrom(
        pluginClasspath
            .plus(files(project.buildDir.resolve("classes/kotlin/test")))
            .plus(files(project.buildDir.resolve("resources/test")))
    )
}