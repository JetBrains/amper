plugins {
    `maven-publish`
}

val unpackedDistribution by tasks.creating(Sync::class) {
    val distZip = tasks.getByName("distZip")

    inputs.property("up-to-date", "11")

    dependsOn(distZip)

    from(provider { zipTree(distZip.outputs.files.singleFile) }) {
        include("*/lib/**")
        eachFile {
            relativePath = RelativePath(true, "lib", relativePath.lastName)
        }
    }

    includeEmptyDirs = false

    destinationDir = file("build/unpackedDistribution")
}

val zipDistribution by tasks.creating(Zip::class) {
    dependsOn(unpackedDistribution)

    from(unpackedDistribution.destinationDir)
    archiveClassifier = "dist"
}

configurations {
    create("dist")
}

val distArtifact = artifacts.add("dist", provider { zipDistribution.outputs.files.singleFile }) {
    type = "zip"
    classifier = "dist"
    builtBy(zipDistribution)
}

publishing {
    publications.getByName<MavenPublication>("kotlinMultiplatform").artifact(distArtifact)
}
