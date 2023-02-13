group = "org.example"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        mavenLocal()
        google()
    }
}

tasks.create("publishAllToLocal") {
    subprojects {
        dependsOn("${this@subprojects.name}:publishToMavenLocal")
    }
}