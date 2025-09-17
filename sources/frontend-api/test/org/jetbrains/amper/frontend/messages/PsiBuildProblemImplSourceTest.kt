/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.messages

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.amper.intellij.IntelliJApplicationConfigurator
import org.jetbrains.amper.intellij.MockProjectInitializer
import org.jetbrains.amper.problems.reporting.LineAndColumn
import org.jetbrains.amper.problems.reporting.LineAndColumnRange
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PsiBuildProblemImplSourceTest {
    private lateinit var project: Project

    @BeforeEach
    fun setUp() {
        project = MockProjectInitializer.initMockProject(IntelliJApplicationConfigurator.EMPTY)
    }

    @Test
    fun `test line and column calculation`() {
        val testFile = LightVirtualFile("module.yaml", """product: jvm/app
            |
            |dependencies:
            |  - ./bad-dependency
        """.trimMargin())
        val psiFile = PsiManager.getInstance(project).findFile(testFile)
        assertNotNull(psiFile)
        val dependenciesBlock = PsiTreeUtil
            .findChildrenOfType(psiFile, YAMLKeyValue::class.java)
            .find { it.keyText == "dependencies" }
            ?.createSmartPointer()
        assertNotNull(dependenciesBlock)
        val source = PsiBuildProblemSource(dependenciesBlock) as PsiBuildProblemSource.Element
        assertEquals(
            source.range,
            LineAndColumnRange(
                LineAndColumn(3, 1, "dependencies:"),
                LineAndColumn(4, 21, "  - ./bad-dependency"),
            )
        )
    }
}