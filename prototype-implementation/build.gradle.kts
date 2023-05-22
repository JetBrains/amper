import java.util.*

group = "org.example"
version = "1.0-SNAPSHOT"

var spaceUsername: String? by extra
var spacePassword: String? by extra

val localProperties = Properties().apply {
    val stream = rootDir.resolve("local.properties").takeIf { it.exists() }?.inputStream()
    if (stream != null) load(stream)
}

spaceUsername = localProperties.getProperty("spaceUsername")
spacePassword = localProperties.getProperty("spacePassword")


allprojects {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        mavenLocal()
        google()
    }
}