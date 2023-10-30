/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.helper

import java.io.File
import java.nio.file.Files


internal class TestFile {
    var content = ""
}


internal class TestDirectory(val dir: File) {

    init {
        try {
            Files.createDirectories(dir.toPath())
        } catch (e: FileAlreadyExistsException) {
            // do nothing
        }
    }

    inline fun directory(name: String, block: TestDirectory.() -> Unit = {}) {
        val newDir = File(dir, name)
        TestDirectory(newDir).apply(block)
    }

    inline fun file(name: String, block: File.() -> Unit = {}) {
        File(dir, name).apply { createNewFile() }.block()
    }

    fun copyLocal(localName: String, newName: String = localName) {
        val localFile = File(".").resolve("testResources/$localName").normalize().takeIf(File::exists)
        localFile?.copyTo(File(dir, newName), overwrite = true)
    }
}

internal inline fun project(projectDir: File, block: TestDirectory.() -> Unit): TestDirectory {
    return TestDirectory(projectDir).apply(block)
}