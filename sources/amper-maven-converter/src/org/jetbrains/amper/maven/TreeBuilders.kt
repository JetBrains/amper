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

internal typealias MapBlock = SyntheticBuilder.() -> MappingNode
internal typealias YamlKeyPath = List<String>
internal typealias YamlComments = Map<YamlKeyPath, YamlComment>

internal data class YamlComment(
    val path: List<String>,
    val beforeKeyComment: String?,
    val afterValueComment: String?,
)

internal data class ModuleWithComments(
    val tree: MappingNode,
    val comments: YamlComments,
)

internal data class AmperForest(
    val projectPath: Path,
    val projectTree: MappingNode,
    val modules: Map<Path, ModuleWithComments>,
)

internal fun amperProjectTreeBuilder(
    projectPath: Path,
    mavenPlugins: List<MavenPluginXml> = emptyList(),
    block: ProjectTreeBuilder.() -> Unit,
): ProjectTreeBuilder =
    ProjectTreeBuilder(projectPath, mavenPlugins).also { block(it) }

internal class ProjectTreeBuilder(val projectPath: Path, mavenPlugins: List<MavenPluginXml> = emptyList()) {

    // pure DefaultTrace can't be used here because default trace can't override anything
    private val dummyTransformedTrace = TransformedValueTrace(
        "dummy",
        TraceableString("dummy", DefaultTrace),
    )

    private val syntheticBuilderDefault = SyntheticBuilder(
        SchemaTypingContext(emptyList(), mavenPlugins),
        dummyTransformedTrace,
        listOf(DefaultContext.ReactivelySet)
    )
    private val syntheticBuilderTest = SyntheticBuilder(
        SchemaTypingContext(emptyList(), mavenPlugins),
        dummyTransformedTrace,
        listOf(TestCtx)
    )

    private var projectTree: MappingNode? = null

    private val modules: MutableMap<Path, ModuleTreeBuilder> = mutableMapOf()

    internal inner class ModuleTreeBuilder {
        private var defaultTree: MappingNode? = null
        private var testTree: MappingNode? = null
        private val yamlComments: MutableMap<YamlKeyPath, YamlComment> = mutableMapOf()

        fun withDefaultContext(block: MapBlock) {
            val newTree = syntheticBuilderDefault.block()
            defaultTree = defaultTree?.let { mergeTrees(listOf(it, newTree)) } ?: newTree
        }

        fun withTestContext(block: MapBlock) {
            val newTree = syntheticBuilderTest.block()
            testTree = testTree?.let { mergeTrees(listOf(it, newTree)) } ?: newTree
        }

        fun addYamlComment(comment: YamlComment) {
            yamlComments[comment.path] = comment
        }

        fun merge(other: ModuleTreeBuilder) {
            other.defaultTree?.let { otherDefaultTree ->
                defaultTree = defaultTree?.let { mergeTrees(listOf(it, otherDefaultTree)) } ?: otherDefaultTree
            }
            other.testTree?.let { otherTestTree ->
                testTree = testTree?.let { mergeTrees(listOf(it, otherTestTree)) } ?: otherTestTree
            }
            yamlComments.putAll(other.yamlComments)
        }

        fun build(): ModuleWithComments = ModuleWithComments(
            tree = mergeTrees(
                listOf(
                    defaultTree ?: syntheticBuilderDefault.`object`<Module> { },
                    testTree ?: syntheticBuilderTest.`object`<Module> { }
                )
            ),
            comments = yamlComments.toMap()
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

    fun merge(other: ProjectTreeBuilder): ProjectTreeBuilder {
        other.projectTree?.let { otherProjectTree ->
            projectTree = projectTree?.let { mergeTrees(listOf(it, otherProjectTree)) } ?: otherProjectTree
        }

        for ((path, otherModuleBuilder) in other.modules) {
            val existingModuleBuilder = modules[path]
            if (existingModuleBuilder != null) {
                existingModuleBuilder.merge(otherModuleBuilder)
            } else {
                val newBuilder = ModuleTreeBuilder()
                newBuilder.merge(otherModuleBuilder)
                modules[path] = newBuilder
            }
        }

        return this
    }

    fun build(): AmperForest = AmperForest(
        projectPath = projectPath,
        projectTree = projectTree ?: syntheticBuilderDefault.`object`<Project> { },
        modules = modules.mapValues { (_, builder) ->
            builder.build()
        }
    )
}
