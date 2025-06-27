/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import app_jvm.generated.resources.Res
import app_jvm.generated.resources.allDrawableResources
import kotlin.test.Test
import kotlin.test.expect
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
class CollectorsTest {
    @Test
    fun test() {
        expect(1) {
            Res.allDrawableResources.size
        }
    }
}