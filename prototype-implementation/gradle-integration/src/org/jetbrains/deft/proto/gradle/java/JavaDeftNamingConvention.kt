package org.jetbrains.deft.proto.gradle.java

import org.gradle.api.tasks.SourceSet
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.gradle.FragmentWrapper

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