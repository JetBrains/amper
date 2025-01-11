/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.metadata.json.projectStructure

import org.jetbrains.amper.dependency.resolution.metadata.json.JsonTestBase
import org.jetbrains.amper.test.Dirs
import org.junit.jupiter.api.TestInfo
import java.nio.file.Path
import kotlin.test.Test

internal class KotlinProjectStructureJsonTest : JsonTestBase<KotlinProjectStructureMetadata>() {

    override fun getTestDataPath(name: String): Path =
        Dirs.amperSourcesRoot.resolve("dependency-resolution/testData/metadata/json/projectStructure/${name}/kotlin-project-structure-metadata.json")

    override fun String.parse(): KotlinProjectStructureMetadata = parseKmpLibraryMetadata()

    override fun serialize(model: KotlinProjectStructureMetadata): String = model.serialize()

    @Test
    fun `kotlinx-coroutines-core-metadata-1_7_3`(testInfo: TestInfo) = doTest(testInfo)

    @Test
    fun `kotlinx-datetime-0_4_0-all`(testInfo: TestInfo) = doTest(testInfo)
}
