/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.java

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet

/**
 * The Gradle extension provided by the Java plugin.
 */
internal val Project.javaPluginExtension: JavaPluginExtension
    get() = extensions.getByType(JavaPluginExtension::class.java)

/**
 * The Java source set for main sources.
 * This is automatically created by the Kotlin Multiplatform plugin.
 */
internal val Project.javaMainSourceSet: SourceSet?
    get() = javaPluginExtension.sourceSets.findByName("jvmMain")

/**
 * The Java source set for test sources.
 * This is automatically created by the Kotlin Multiplatform plugin.
 */
internal val Project.javaTestSourceSet: SourceSet?
    get() = javaPluginExtension.sourceSets.findByName("jvmTest")
