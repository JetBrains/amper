/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.aomBuilder

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.messages.GlobalBuildProblemSource
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.NonIdealDiagnostic
import org.jetbrains.amper.frontend.Artifact
import org.jetbrains.amper.frontend.ClassBasedSet
import org.jetbrains.amper.frontend.CompositeStringPart
import org.jetbrains.amper.frontend.CompositeStringPart.Literal
import org.jetbrains.amper.frontend.CustomTaskDescription
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.ModulePart
import org.jetbrains.amper.frontend.KnownModuleProperty
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.AmperModuleSource
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.aomBuilder.parseStringWithReferences
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.test.golden.TestProblemReporterContext
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class BuildTest {
    @Test
    fun parseStringWithReferences_valid() {
        checkParse("some \\\$text", Literal("some \$text"))
        checkParse("some \\\\\\\$text", Literal("some \\\$text"))

        checkParse(
            "\${module(.).version}",
            CompositeStringPart.ModulePropertyReference(module1, KnownModuleProperty.VERSION, "\${module(.).version}")
        )

        checkParse(
            "\${module(.).version}\${module(../xxx/yyy).name}",
            CompositeStringPart.ModulePropertyReference(module1, KnownModuleProperty.VERSION, "\${module(.).version}"),
            CompositeStringPart.ModulePropertyReference(module2, KnownModuleProperty.NAME, "\${module(../xxx/yyy).name}")
        )

        checkParse(
            "text1\${module(.).version}text2\${module(../xxx/yyy).name}text3",
            Literal("text1"),
            CompositeStringPart.ModulePropertyReference(module1, KnownModuleProperty.VERSION, "\${module(.).version}"),
            Literal("text2"),
            CompositeStringPart.ModulePropertyReference(module2, KnownModuleProperty.NAME, "\${module(../xxx/yyy).name}"),
            Literal("text3"),
        )
    }

    @Test
    fun parseStringWithReferences_errors() {
        checkParseErrors("ref \$ref", "Contains unresolved reference: ref \$ref")
        checkParseErrors("ref \${module(.).some}", "Unknown property name 'some': \${module(.).some}")
        checkParseErrors("\${module(abc).version}", "Unknown module 'abc' referenced from '\${module(abc).version}'")
    }

    @OptIn(NonIdealDiagnostic::class)
    private fun checkParse(value: String, vararg expectedParts: CompositeStringPart) {
        val reporter = TestProblemReporterContext()
        with(reporter) {
            val actual = parseStringWithReferences(value, GlobalBuildProblemSource, moduleResolver)
            val problems = reporter.problemReporter.getDiagnostics(Level.Warning)
            if (problems.isNotEmpty()) {
                fail("Parsing of '$value' failed:\n" +
                        problems.joinToString("\n") { it.message })
            }

            assertEquals(
                expectedParts.toList(), actual.parts,
                message = "Parse assertion for string: $value",
            )
        }
    }

    @OptIn(NonIdealDiagnostic::class)
    private fun checkParseErrors(value: String, vararg message: String) {
        require(message.isNotEmpty())

        val reporter = TestProblemReporterContext()
        with(reporter) {
            parseStringWithReferences(value, GlobalBuildProblemSource, moduleResolver)
            val problems = reporter.problemReporter.getDiagnostics(*Level.entries.toTypedArray()).map { it.message }
            assertEquals(message.toList(), problems)
        }
    }

    private class MockModule() : AmperModule {
        override val userReadableName: String
            get() = throw UnsupportedOperationException()
        override val type: ProductType
            get() = throw UnsupportedOperationException()
        override val source: AmperModuleSource
            get() = throw UnsupportedOperationException()
        override val fragments: List<Fragment>
            get() = throw UnsupportedOperationException()
        override val artifacts: List<Artifact>
            get() = throw UnsupportedOperationException()
        override val parts: ClassBasedSet<ModulePart<*>>
            get() = throw UnsupportedOperationException()
        override val usedCatalog: VersionCatalog
            get() = throw UnsupportedOperationException()
        override val usedTemplates: List<VirtualFile>
            get() = throw UnsupportedOperationException()
        override val customTasks: List<CustomTaskDescription>
            get() = throw UnsupportedOperationException()
    }

    private val module1 = MockModule()
    private val module2 = MockModule()
    private val moduleResolver = { path: String ->
        when (path) {
            "." -> module1
            "../xxx/yyy" -> module2
            else -> null
        }
    }
}
