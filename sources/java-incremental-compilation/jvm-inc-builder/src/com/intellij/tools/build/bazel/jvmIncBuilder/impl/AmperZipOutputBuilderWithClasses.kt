/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package com.intellij.tools.build.bazel.jvmIncBuilder.impl

import org.jetbrains.intellij.build.io.INDEX_FILENAME
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.writeBytes

class AmperZipOutputBuilderWithClasses(
  dataSwap: MutableMap<String?, ByteArray?>?,
  readZipPath: Path,
  writeZipPath: Path,
  private val classesOutputDirectory: Path,
) : ZipOutputBuilderImpl(dataSwap, readZipPath, writeZipPath, true) {

  override fun writeClassFilesIfNeeded(entries: Map<String, EntryData>) {
    writeClassFiles(entries, classesOutputDirectory)
  }

  override fun deleteClassFileIfNeeded(entry: EntryData) {
    val zipEntry = entry.zipEntry
    if (zipEntry.isDirectory) {
      classesOutputDirectory.resolve(zipEntry.getName()).deleteRecursively()
    }
    else {
      classesOutputDirectory.resolve(zipEntry.getName()).deleteIfExists()
    }
  }

  override fun cleanBuildStateOnFullRebuild() {
    classesOutputDirectory.deleteRecursively()
    classesOutputDirectory.createDirectories()
  }

  private fun writeClassFiles(entries: Map<String, EntryData>, rootDir: Path) {
    for (entry in entries.entries) {
      val data = entry.value
      val zipEntry = data.zipEntry
      val name = zipEntry.getName()
      if (name == INDEX_FILENAME) {
        continue
      }

      val pathToCreate = rootDir / name
      if (zipEntry.isDirectory) {
        pathToCreate.createDirectories()
      } else {
        pathToCreate.createParentDirectories()
        pathToCreate.writeBytes(data.getContent())
      }
    }
  }
}
