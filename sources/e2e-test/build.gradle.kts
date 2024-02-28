/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

tasks.withType<Test> {
    systemProperties["junit.jupiter.execution.parallel.enabled"] = true
    systemProperties["junit.jupiter.execution.parallel.mode.default"] = "concurrent"

    for (task in rootProject.getTasksByName("publishToMavenLocal", true)) {
        dependsOn(task)
    }

    filter {
        excludeTestsMatching(".*EmulatorTests.*")
    }

    useJUnitPlatform()
    val inBootstrapMode: String? by project
    inBootstrapMode?.let {
        if (it == "true") {
            filter {
                excludeTestsMatching("*BootstrapTest*")
            }
        }
    }
    maxHeapSize = "4g"
}

tasks.register("runEmulatorTests", Test::class) {
    group = "verification"
    description = "Runs special tests from EmulatorTests.kt"
    useJUnitPlatform()

    filter {
        includeTestsMatching(".*EmulatorTests.*")
    }
    maxHeapSize = "4g"
}
