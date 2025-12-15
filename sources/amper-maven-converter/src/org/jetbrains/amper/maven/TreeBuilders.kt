/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven

import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.TransformedValueTrace
import org.jetbrains.amper.frontend.contexts.DefaultContext
import org.jetbrains.amper.frontend.contexts.TestCtx
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Project
import org.jetbrains.amper.frontend.tree.MappingNode
import org.jetbrains.amper.frontend.tree.SyntheticBuilder
import org.jetbrains.amper.frontend.tree.mergeTrees
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import java.nio.file.Path

typealias MapBlock = SyntheticBuilder.() -> MappingNode

internal data class AmperForest(
    val projectPath: Path,
    val projectTree: MappingNode,
    val modules: Map<Path, MappingNode>,
)

internal fun amperProjectTreeBuilder(projectPath: Path, block: ProjectTreeBuilder.() -> Unit): ProjectTreeBuilder =
    ProjectTreeBuilder(projectPath).apply(block)

internal class ProjectTreeBuilder(val projectPath: Path) {

    // pure DefaultTrace can't be used here because default trace can't override anything
    private val dummyTransformedTrace = TransformedValueTrace(
        "dummy",
        TraceableString("dummy", DefaultTrace),
    )

    private val syntheticBuilderDefault = SyntheticBuilder(
        SchemaTypingContext(emptyList(), emptyList()),
        dummyTransformedTrace,
        listOf(DefaultContext.ReactivelySet)
    )
    private val syntheticBuilderTest = SyntheticBuilder(
        SchemaTypingContext(emptyList(), emptyList()),
        dummyTransformedTrace,
        listOf(TestCtx)
    )

    private var projectTree: MappingNode? = null

    private val modules: MutableMap<Path, ModuleTreeBuilder> = mutableMapOf()

    internal inner class ModuleTreeBuilder {
        private var defaultTree: MappingNode? = null
        private var testTree: MappingNode? = null

        fun withDefaultContext(block: MapBlock) {
            val newTree = syntheticBuilderDefault.block()
            defaultTree = defaultTree?.let { mergeTrees(listOf(it, newTree)) } ?: newTree
        }

        fun withTestContext(block: MapBlock) {
            val newTree = syntheticBuilderTest.block()
            testTree = testTree?.let { mergeTrees(listOf(it, newTree)) } ?: newTree
        }

        fun build(): MappingNode = mergeTrees(
            listOf(
                defaultTree ?: syntheticBuilderDefault.`object`<Module> { },
                testTree ?: syntheticBuilderTest.`object`<Module> { }
            )
        )
    }

    fun module(modulePath: Path, block: ModuleTreeBuilder.() -> Unit) {
        val existing = modules[modulePath]
        if (existing != null) {
            existing.apply(block)
        } else {
            modules[modulePath] = ModuleTreeBuilder().apply(block)
        }
    }

    fun project(block: MapBlock) {
        val newTree = syntheticBuilderDefault.block()
        projectTree = projectTree?.let { mergeTrees(listOf(it, newTree)) } ?: newTree
    }

    fun build(): AmperForest = AmperForest(
        projectPath = projectPath,
        projectTree = projectTree ?: syntheticBuilderDefault.`object`<Project> { },
        modules = modules.mapValues { (_, builder) ->
            builder.build()
        }
    )
}
