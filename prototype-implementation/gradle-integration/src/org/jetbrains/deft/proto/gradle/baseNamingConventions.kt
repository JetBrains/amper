package org.jetbrains.deft.proto.gradle

import org.jetbrains.deft.proto.gradle.base.BindingPluginPart

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
        get() = src?.toFile() ?: path.resolve("src").toFile()

    // TODO Replace by Path, but evade recursion in AGP.
    val FragmentWrapper.resourcePath
        get() = src?.resolve("resources")?.toFile() ?: path.resolve("resources").toFile()

}