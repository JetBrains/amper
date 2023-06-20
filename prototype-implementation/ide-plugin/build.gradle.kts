plugins {
    id("java")
    kotlin("jvm")
    alias(libs.plugins.gradleIntelliJPlugin)
}

group = "org.jetbrains.deft.ide"
version = properties("ide-plugin.version").get()

fun properties(key: String) = providers.gradleProperty(key)
fun env(key: String) = providers.environmentVariable(key)

kotlin {
    jvmToolchain(11)
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
}
