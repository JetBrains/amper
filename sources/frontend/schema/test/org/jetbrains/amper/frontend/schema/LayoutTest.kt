/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.helpers.FrontendTestCaseBase
import org.jetbrains.amper.frontend.helpers.aomTest
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.div

internal class LayoutTest: FrontendTestCaseBase(Path("testResources") / "layout") {

    @Test
    fun `jvm app maven-like layout`() {
        aomTest("jvm-app-maven-like-layout")
    }

    @Test
    fun `jvm lib maven-like layout`() {
        aomTest("jvm-lib-maven-like-layout")
    }

}
