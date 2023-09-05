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

    private val FragmentWrapper.resourcePath
        get() = src.resolve("resources")

    val FragmentWrapper.resourcePaths get() = listOf(resourcePath)

}