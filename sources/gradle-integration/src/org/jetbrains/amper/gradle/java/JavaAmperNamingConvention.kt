/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
                "main" -> module.sharedPlatformFragment(Platform.JVM, false)
                "test" -> module.sharedPlatformFragment(Platform.JVM, true)
                else -> module.fragmentsByName[name]
            }
        }

    context(JavaBindingPluginPart)
    fun FragmentWrapper.maybeCreateJavaSourceSet(block: SourceSet.() -> Unit = {}) =
        javaPE.sourceSets.maybeCreate(javaSourceSetName).block()

}