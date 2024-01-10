import org.eclipse.jgit.internal.storage.file.FileRepository

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.eclipse.jgit:org.eclipse.jgit:6.7.0.202309050840-r")
    }
}

val generateBuildProperties by tasks.creating(WriteProperties::class.java) {
    destinationFile.set(project.layout.buildDirectory.file("build.properties"))

    comment = "Build info"
    property("version", project.version)

    // no git directory in bootstrap test
    if (project.findProperty("inBootstrapMode") != "true") {
        val gitRoot = rootProject.projectDir.parentFile.resolve(".git")
        val git = FileRepository(gitRoot)
        val head = git.getReflogReader("HEAD").lastEntry
        property("commitHash", head.newId.name)
        property("commitDate", head.who.`when`.toInstant())
    }
}

tasks.jvmProcessResources {
    dependsOn(generateBuildProperties)
    from(generateBuildProperties)
}
