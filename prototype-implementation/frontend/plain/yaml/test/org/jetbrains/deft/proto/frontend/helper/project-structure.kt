package org.jetbrains.deft.proto.frontend.helper

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
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

    inline fun file(name: String, block: TestFile.() -> Unit = {}) {
        val testFile = TestFile().apply(block)
        val file = File(dir, name)
        FileWriter(file).use { fileWriter ->
            BufferedWriter(fileWriter).use {
                it.write(testFile.content)
            }
        }
    }
}

internal inline fun project(projectDir: File, block: TestDirectory.() -> Unit): TestDirectory {
    return TestDirectory(projectDir).apply(block)
}