/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

tasks.withType<Test>().configureEach {
    if (name != "runEmulatorTests") {
        systemProperties["junit.jupiter.execution.parallel.enabled"] = true
        systemProperties["junit.jupiter.execution.parallel.mode.default"] = "concurrent"

        for (task in rootProject.getTasksByName("publishToMavenLocal", true)) {
            dependsOn(task)
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

        exclude { include ->
            include.file.name.contains("EmulatorTests")
        }
    }
}

val runEmulatorTests by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs special tests from EmulatorTests.kt"

    useJUnitPlatform()
    include { include ->
        include.file.name.contains("EmulatorTests")
    }
    maxHeapSize = "4g"
}

