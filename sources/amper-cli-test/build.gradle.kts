/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

tasks.withType<Test> {
    maxHeapSize = "4G"

    systemProperty("amper.unpacked.dist.root", "${rootProject.projectDir}/sources/cli/build/unpackedDistribution")

    // To build&publish Android gradle plugin to mavenLocal
    for (task in rootProject.getTasksByName("publishToMavenLocal", true)) {
        dependsOn(task)
    }

    // To build CLI dist used for CLI integration tests
    val distTaskName = "unpackedDistribution"
    val distTasks = rootProject.getTasksByName(distTaskName, true).also {
        check(it.isNotEmpty()) {
            "Unable to find '$distTaskName' task by name"
        }
    }
    for (task in distTasks) {
        dependsOn(task)
    }

    systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
    systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", 4)
}
