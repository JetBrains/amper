/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.templates

import io.github.classgraph.ClassGraph
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectTemplatesTest {

    @Test
    fun `all templates are listed`() {
        val templatesDirChildren = ClassGraph().acceptPaths("templates").scan().use { scanResult ->
            scanResult.allResources.paths
                .map { resPath -> resPath.removePrefix("templates/").substringBefore("/") }
                .toSet()
        }
        assertEquals(AmperProjectTemplates.availableTemplates.map { it.name }.toSet(), templatesDirChildren - "list.txt")
    }
}
