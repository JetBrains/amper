/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

subprojects {
    afterEvaluate {
        configurations.all {
            resolutionStrategy.eachDependency {
                if (requested.group == "com.intellij.platform") {
                    if (requested.name.startsWith("kotlinx-coroutines-")) {
                        val version = requested.version?.substringBeforeLast("-intellij")
                        useTarget("org.jetbrains.kotlinx:${requested.name}:$version")
                        because("it conflicts with original kotlinx-coroutines")
                    }
                }
            }
        }
    }
}