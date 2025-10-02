/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.helpers.FrontendTestCaseBase
import org.jetbrains.amper.frontend.helpers.aomTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.io.path.Path
import kotlin.io.path.div

class DefaultsTest : FrontendTestCaseBase(Path("testResources") / "parser" / "defaults") {
    @ParameterizedTest(name = "Test defaults for {0}")
    @EnumSource(ProductType::class, mode = EnumSource.Mode.EXCLUDE)
    fun `test defaults for product type`(productType: ProductType) {
        val testDataFile = productType.value.replace('/', '-')
        aomTest(testDataFile, printDefaults = true)
    }
}