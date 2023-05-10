package org.jetbrains.deft.proto.gradle.java

import org.gradle.api.tasks.SourceSet
import org.jetbrains.deft.proto.gradle.FragmentWrapper

@Suppress("UnstableApiUsage")
object JavaDeftNamingConvention {

    context(JavaBindingPluginPart)
    private val FragmentWrapper.javaSourceSetName: String
        get() = when (name) {
            leafNonTestFragment?.name -> "main"
            leafTestFragment?.name -> "test"
            else -> name
        }

    context(JavaBindingPluginPart)
    val SourceSet.deftFragment
        get(): FragmentWrapper? = when (this.name) {
            "main" -> leafNonTestFragment
            "test" -> leafTestFragment
            else -> module.fragmentsByName[name]
        }

    context(JavaBindingPluginPart)
    fun FragmentWrapper.maybeCreateJavaSourceSet(block: SourceSet.() -> Unit) =
            javaPE.sourceSets.maybeCreate(javaSourceSetName).block()

}