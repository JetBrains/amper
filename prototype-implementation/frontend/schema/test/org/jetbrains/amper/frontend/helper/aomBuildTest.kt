/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.helper

import org.jetbrains.amper.frontend.ReaderCtx
import org.jetbrains.amper.frontend.aomBuilder.buildAom
import org.jetbrains.amper.frontend.old.helper.BuildFileAware
import org.jetbrains.amper.frontend.old.helper.TestWithBuildFile
import org.jetbrains.amper.frontend.processing.readTemplatesAndMerge
import org.jetbrains.amper.frontend.processing.replaceCatalogDependencies
import org.jetbrains.amper.frontend.schemaConverter.ConvertCtx
import org.jetbrains.amper.frontend.schemaConverter.convertModule
import java.io.File
import java.io.StringReader
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.reader


context(TestWithBuildFile)
fun aomTest(caseName: String) = BuildAomTestRun(caseName).doTest()

class BuildAomTestRun(caseName: String) : BaseTestRun(caseName) {

    context(BuildFileAware, TestProblemReporterContext)
    override fun getInputContent(path: Path): String {
        // Fix paths, so they will point to resources.
        val processPath = Path(".").absolute().normalize()
        val testResourcesPath = processPath / "testResources"
        val ctx = ReaderCtx {
            if (it.startsWith(testResourcesPath)) it.reader()
            else {
                val relative = processPath.relativize(it)
                val resolved = testResourcesPath.resolve(relative)
                resolved.takeIf { resolved.exists() }?.reader()
            }
        }

        // Read module.
        val cleared = path.readText().removeDiagnosticsAnnotations()
        val schemaModule = with(ctx) {
            with(ConvertCtx(path.parent)) {
                convertModule { StringReader(cleared) }
                    .readTemplatesAndMerge()
                    .replaceCatalogDependencies()
            }
        }

        // Build AOM.
        val module = mapOf(path to schemaModule).buildAom().first()

        // Return module's textual representation.
        return module.prettyPrint()
    }

    context(BuildFileAware, TestProblemReporterContext)
    override fun getExpectContent(path: Path): String {
        val buildDir = buildFile.normalize().toString()
        val potDir = path.toAbsolutePath().normalize().parent.toString()
        val testProcessDir = File(".").absoluteFile.normalize().toString()
        val testResources = File(".").resolve("testResources").absoluteFile.normalize().toString()

        // This is actual check.
        val resourceFileText = path.readText()
        return resourceFileText
            .replace("{{ buildDir }}", buildDir)
            .replace("{{ potDir }}", buildFile.parent.relativize(Path.of(potDir)).toString())
            .replace("{{ testProcessDir }}", testProcessDir)
            .replace("{{ testResources }}", testResources)
            .replace("{{ fileSeparator }}", File.separator)
    }
}