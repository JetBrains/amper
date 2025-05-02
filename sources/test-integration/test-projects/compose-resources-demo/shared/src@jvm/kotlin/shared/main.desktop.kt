/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package shared

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

@Composable
fun MainView() {
    UseResources()
}

@Preview
@Composable
fun MainViewPreview() {
    MainView()
}

@Preview
@Composable
fun ImagesResPreview() {
    ImagesRes(PaddingValues())
}

@Preview
@Composable
fun FileResPreview() {
    FileRes(PaddingValues())
}
