/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import com.intellij.lang.ASTNode
import com.intellij.mock.MockApplication
import com.intellij.mock.MockProject
import com.intellij.pom.PomModel
import com.intellij.pom.core.impl.PomModelImpl
import com.intellij.pom.tree.TreeAspect
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.codeStyle.IndentHelper
import org.jetbrains.amper.frontend.IntelliJApplicationConfigurator

/**
 * Registers extensions needed for PSI modification.
 */
object ModifiablePsiIntelliJApplicationConfigurator : IntelliJApplicationConfigurator() {
    override fun registerApplicationExtensions(application: MockApplication) {
        application.registerService(IndentHelper::class.java, object : IndentHelper() {
            override fun getIndent(file: PsiFile, element: ASTNode): Int = 0
            override fun getIndent(file: PsiFile, element: ASTNode, includeNonSpace: Boolean): Int = 0
        })
    }

    override fun registerProjectExtensions(project: MockProject) {
        project.registerService(TreeAspect::class.java)
        project.registerService(PomModel::class.java, PomModelImpl::class.java)
    }
}