/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.helper

import org.jetbrains.amper.frontend.aomBuilder.buildAom
import org.jetbrains.amper.frontend.old.helper.BuildFileAware
import org.jetbrains.amper.frontend.old.helper.TestWithBuildFile
import org.jetbrains.amper.frontend.processing.replaceCatalogDependencies
import org.jetbrains.amper.frontend.schemaConverter.convertModule
import java.io.File
import java.io.StringReader
import java.nio.file.Path
import kotlin.io.path.readText


context(TestWithBuildFile)
fun aomTest(caseName: String) = BuildAomTestRun(caseName).doTest()

class BuildAomTestRun(caseName: String) : BaseTestRun(caseName) {

    context(BuildFileAware, TestProblemReporterContext)
    override fun getInputContent(path: Path): String {
        val text = path.readText().removeDiagnosticsAnnotations()
        val schemaModule = convertModule { StringReader(text) }.replaceCatalogDependencies()
        val module = mapOf(path to schemaModule).buildAom().first()
        return module.prettyPrint()
    }

    context(BuildFileAware, TestProblemReporterContext)
    override fun getExpectContent(path: Path): String {
        val buildDir = buildFile.normalize().toString()
        val potDir = path.toAbsolutePath().normalize().parent.toString()
        val testProcessDir = File(".").absoluteFile.normalize().toString()

        // This is actual check.
        val resourceFileText = path.readText()
        return resourceFileText
            .replace("{{ buildDir }}", buildDir)
            .replace("{{ potDir }}", buildFile.parent.relativize(Path.of(potDir)).toString())
            .replace("{{ testProcessDir }}", testProcessDir)
            .replace("{{ fileSeparator }}", File.separator)
    }
}