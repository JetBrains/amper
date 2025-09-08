/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.wasm

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.tasks.ProjectTasksBuilder

fun ProjectTasksBuilder.setupWasmWasiTasks() {
    setupWasmTasks(
        Platform.WASM_WASI,
        ::WasmWasiCompileKlibTask,
        ::WasmWasiLinkTask,
    )
}