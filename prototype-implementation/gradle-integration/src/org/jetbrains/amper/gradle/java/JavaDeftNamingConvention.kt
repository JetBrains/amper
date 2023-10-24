package org.jetbrains.amper.gradle.java

import org.gradle.api.tasks.SourceSet
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.gradle.FragmentWrapper

object JavaDeftNamingConvention {

    context(JavaBindingPluginPart)
    private val FragmentWrapper.javaSourceSetName: String
        get() {
            return when {
                isTest -> "test"
                else -> "main"
            }
        }

    context(JavaBindingPluginPart)
    val SourceSet.deftFragment
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