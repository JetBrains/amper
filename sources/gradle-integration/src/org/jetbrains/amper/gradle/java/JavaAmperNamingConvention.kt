/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.java

import org.gradle.api.tasks.SourceSet
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.gradle.FragmentWrapper

object JavaAmperNamingConvention {

    context(JavaBindingPluginPart)
    private val FragmentWrapper.javaSourceSetName: String
        get() {
            return when {
                isTest -> "test"
                else -> "main"
            }
        }

    context(JavaBindingPluginPart)
    val SourceSet.amperFragment
        get(): FragmentWrapper? {
            return when (name) {
                "main" -> module.leafFragments.first { it.platform == Platform.JVM && it.isTest == false }
                "test" -> module.leafFragments.first { it.platform == Platform.JVM && it.isTest == true }
                else -> module.fragmentsByName[name]
            }
        }

    context(JavaBindingPluginPart)
    fun FragmentWrapper.maybeCreateJavaSourceSet(block: SourceSet.() -> Unit = {}) =
        javaPE.sourceSets.maybeCreate(javaSourceSetName).block()

}