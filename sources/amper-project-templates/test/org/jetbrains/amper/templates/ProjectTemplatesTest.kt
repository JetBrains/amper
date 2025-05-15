/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.templates

import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectTemplatesTest {

    @Test
    fun `all templates are listed and can be instantiated`() {
        val templatesDirChildren = Path("resources/templates").listDirectoryEntries().map { it.name }.toSet()
        assertEquals(templatesDirChildren, AmperProjectTemplates.availableTemplates.map { it.id }.toSet())
    }
}
