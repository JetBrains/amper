package org.jetbrains.deft.proto.gradle.java

import org.jetbrains.deft.proto.frontend.*

object JavaDeftNamingConvention {

    context(JavaBindingPluginPart)
    val Platform.targetName: String
        get() = name.doCamelCase()

    context(JavaBindingPluginPart)
    val Platform.target get() = kotlinMPE.targets.findByName(targetName)

}