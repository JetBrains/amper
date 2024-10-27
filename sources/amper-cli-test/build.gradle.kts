/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

tasks.withType<Test> {
    maxHeapSize = "4G"

    // To build&publish the :android-integration:gradle-plugin and deps for gradle-backed Android builds
    // To build&publish the CLI distribution and scripts required for wrapper-based tests
    for (task in rootProject.getTasksByName("publishToMavenLocal", true)) {
        dependsOn(task)
    }

    systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
    systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", 4)
}
