package org.jetbrains.deft.proto.gradle.base

import org.jetbrains.deft.proto.gradle.FragmentWrapper

/**
 * Basic deft layout naming conventions.
 */
// TODO Think of more elegant way of introducing immutable
// TODO contracts as receivers.
// For now, having `object` means multiple receivers, and `interface` ends up with
// `Inherited platform declarations clash` unfortunately.
interface DeftNamingConventions : BindingPluginPart {

    // TODO Replace by Path, but evade recursion in AGP.
    val FragmentWrapper.sourcePath
        get() = src ?: path.resolve("src").toString()

    val FragmentWrapper.sourcePaths get() = listOf(sourcePath)

    // TODO Replace by Path, but evade recursion in AGP.
    private val FragmentWrapper.resourcePath
        get() = src?.resolve("resources")
            ?: path.resolve("resources").toString()

    val FragmentWrapper.resourcePaths get() = listOf(resourcePath)

}