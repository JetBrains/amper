package org.jetbrains.deft.proto.gradle.base

import org.jetbrains.deft.proto.frontend.KotlinFragmentPart
import org.jetbrains.deft.proto.gradle.FragmentWrapper
import org.jetbrains.deft.proto.gradle.part

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
        get() = part<KotlinFragmentPart>()?.srcFolderName
            ?: path.resolve("src").toString()

    // TODO Replace by Path, but evade recursion in AGP.
    val FragmentWrapper.resourcePath
        get() = part<KotlinFragmentPart>()?.srcFolderName?.let { "$it/resources" }
            ?: path.resolve("resource").toString()

}