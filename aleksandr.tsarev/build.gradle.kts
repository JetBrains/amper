group = "org.example"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        maven("https://jitpack.io")
        mavenLocal()
        mavenCentral()
    }
}

tasks.create("publishAllToLocal") {
    subprojects {
        dependsOn("${this@subprojects.name}:publishToMavenLocal")
    }
}