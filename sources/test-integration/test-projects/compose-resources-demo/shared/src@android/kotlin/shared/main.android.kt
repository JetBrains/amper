/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package shared

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.PreviewContextConfigurationEffect

@Composable
fun MainView() {
    UseResources()
}

@Composable
fun ImagesResPreview() {
    ImagesRes(PaddingValues())
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun FileResPreview() {
    PreviewContextConfigurationEffect()
    FileRes(PaddingValues())
}
