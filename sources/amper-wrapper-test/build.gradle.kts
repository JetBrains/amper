/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


tasks.withType<Test> {
    maxHeapSize = "4G"

    // To build&publish Android gradle plugin to mavenLocal
    // To build&publish cli dist required for wrapper tests
    for (task in rootProject.getTasksByName("publishToMavenLocal", true)) {
        dependsOn(task)
    }
}
