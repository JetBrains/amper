import java.util.*

plugins {
    id("java")
    kotlin("jvm")
    alias(libs.plugins.gradleIntelliJPlugin)
}

group = "org.jetbrains.deft.ide"
version = properties("ide-plugin.version").get()

fun properties(key: String) = providers.gradleProperty(key)
fun env(key: String) = providers.environmentVariable(key)

val localProperties = Properties().apply {
    val stream = rootDir.resolve("root.local.properties")
        .takeIf { it.exists() }
        ?.inputStream()
    if (stream != null) load(stream)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

intellij {
    pluginName = properties("ide-plugin.name")
    version = properties("ide-plugin.platform.version")
    type = properties("ide-plugin.platform.type")
    plugins = properties("ide-plugin.platform.plugins").map { it.split(',').map(String::trim).filter(String::isNotEmpty) }
}

tasks {
    patchPluginXml {
        version = properties("ide-plugin.version")
        sinceBuild = properties("ide-plugin.compatibility.since-build")
        untilBuild = properties("ide-plugin.compatibility.until-build")
    }
    publishPlugin {
        toolboxEnterprise = true
        host = "https://tbe.labs.jb.gg"
        token = localProperties.getProperty("ide-plugin.publish.token")
        channels = listOf(properties("ide-plugin.channel").get())
    }
}

dependencies {
    implementation(project(mapOf("path" to ":frontend:plain:yaml")) as ProjectDependency) {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation(project(mapOf("path" to ":frontend-api")) as ProjectDependency) {
        exclude(group = "org.jetbrains.kotlin")
    }
}
