import java.util.*

group = "org.example"
version = "1.0-SNAPSHOT"

val localProperties = Properties().apply {
    load(rootDir.resolve("local.properties").inputStream())
}
var spaceUsername: String by extra
var spacePassword: String by extra

spaceUsername = localProperties.getProperty("spaceUsername") ?: error("No spaceUsername in local.properties!")
spacePassword = localProperties.getProperty("spacePassword") ?: error("No spacePassword in local.properties!")


allprojects {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        mavenLocal()
        google()
    }
}