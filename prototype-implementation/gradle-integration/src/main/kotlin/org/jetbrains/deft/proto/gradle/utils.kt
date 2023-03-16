package org.jetbrains.deft.proto.gradle

import org.jetbrains.deft.proto.frontend.PotatoModule
import org.jetbrains.deft.proto.frontend.PotatoModuleFileSource

val PotatoModule.buildFile get() = (source as PotatoModuleFileSource).buildFile

val PotatoModule.buildDir get() = buildFile.parent