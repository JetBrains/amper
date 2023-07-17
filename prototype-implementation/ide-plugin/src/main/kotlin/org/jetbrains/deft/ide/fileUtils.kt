package org.jetbrains.deft.ide

import com.intellij.openapi.vfs.VirtualFile

internal fun VirtualFile.isPot(): Boolean = name == "Pot.yaml"

internal fun VirtualFile.isPotTemplate(): Boolean = name.endsWith("Pot-template.yaml")
