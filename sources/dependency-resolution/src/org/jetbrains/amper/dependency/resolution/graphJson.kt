/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import org.jetbrains.amper.dependency.resolution.DependencyGraph.Companion.toSerializableReference
import org.jetbrains.amper.dependency.resolution.diagnostics.registerSerializableMessages
import kotlin.reflect.KClass

object GraphJson {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        allowStructuredMapKeys = true

        serializersModule = SerializersModule {
            DependencyGraph.providers.forEach {
                with (it) {
                    registerPolymorphic()
                }
            }
        }
    }
}

interface GraphSerializableTypesProvider {
    fun getSerializableConverters(): List<SerializableDependencyNodeConverter<out DependencyNode, out SerializableDependencyNode>>
    fun SerializersModuleBuilder.registerPolymorphic()
}

internal class DefaultSerializableTypesProvider: GraphSerializableTypesProvider {
    override fun getSerializableConverters(): List<SerializableDependencyNodeConverter<out DependencyNode, out SerializableDependencyNode>> =
        MavenDependencyNodeConverter.converters() +
            RootDependencyNodeConverter.converters() +
            MavenDependencyConstraintNodeConverter.converters()

    override fun SerializersModuleBuilder.registerPolymorphic() {
        moduleForSerializableDependencyNodeHierarchy()
        moduleForDependencyNodeHierarchy()
        moduleMessageHierarchy()
    }

    fun SerializersModuleBuilder.moduleForSerializableDependencyNodeHierarchy() =
        moduleForDependencyNodeHierarchy(SerializableDependencyNode::class as KClass<DependencyNode>)

    fun SerializersModuleBuilder.moduleForDependencyNodeHierarchy() =
        moduleForDependencyNodeHierarchy(DependencyNode::class)

    fun SerializersModuleBuilder.moduleForDependencyNodeHierarchy(kClass: KClass<DependencyNode>) {
        polymorphic(kClass, SerializableMavenDependencyNode::class, SerializableMavenDependencyNode.serializer())
        polymorphic(kClass, SerializableRootDependencyNode::class, SerializableRootDependencyNode.serializer())
        polymorphic(kClass,SerializableMavenDependencyConstraintNode::class,SerializableMavenDependencyConstraintNode.serializer()        )
    }

    fun SerializersModuleBuilder.moduleMessageHierarchy() =
        registerSerializableMessages()
}

/**
 * Stateless converter of the [DependencyNode] to the [SerializableDependencyNode].
 * State is provided by the calling side as an instance of [DependencyGraphContext]
 */
interface SerializableDependencyNodeConverter<T: DependencyNode, P: SerializableDependencyNode> {
    fun applicableTo(): KClass<T>
    fun toEmptyNodePlain(node: T, graphContext: DependencyGraphContext): P
    fun fillEmptyNodePlain(nodePlain: P, node: T, graphContext: DependencyGraphContext, nodeReference: DependencyNodeReference?) {
        val children = node.children.map { it.toSerializableReference(graphContext, nodeReference) }
        if (children.isNotEmpty()) {
            (nodePlain.childrenRefs as MutableList<DependencyNodeReference>).addAll(children)
        }
    }
}

private sealed class MavenDependencyNodeConverter<T: MavenDependencyNode>(): SerializableDependencyNodeConverter<T, SerializableMavenDependencyNode>  {
    object Input: MavenDependencyNodeConverter<MavenDependencyNodeWithContext>() {
        override fun applicableTo() = MavenDependencyNodeWithContext::class
    }
    object Plain: MavenDependencyNodeConverter<SerializableMavenDependencyNode>() {
        override fun applicableTo() = SerializableMavenDependencyNode::class
    }

    override fun toEmptyNodePlain(node: T, graphContext: DependencyGraphContext): SerializableMavenDependencyNode =
        SerializableMavenDependencyNode(
            node.originalVersion, node.versionFromBom, node.isBom, node.messages,
            dependencyRef = node.dependency.toSerializableReference(graphContext),
            coordinatesForPublishing = node.getMavenCoordinatesForPublishing(),
            parentKmpLibraryCoordinates = node.getParentKmpLibraryCoordinates(),
            graphContext = graphContext
        )

    override fun fillEmptyNodePlain(nodePlain: SerializableMavenDependencyNode, node: T, graphContext: DependencyGraphContext, nodeReference: DependencyNodeReference?) {
        super.fillEmptyNodePlain(nodePlain, node, graphContext, nodeReference)
        val overriddenBy = node.overriddenBy
            .filter { !it.isOrphan(root = graphContext.allDependencyNodeReferences.entries.first().key) }
            .map { it.toSerializableReference(graphContext, null) }
        nodePlain.overriddenByRefs.addAll(overriddenBy)
    }

    companion object {
        fun converters(): List<SerializableDependencyNodeConverter<out DependencyNode, SerializableMavenDependencyNode>> = listOf(Input, Plain)
    }
}

private sealed class MavenDependencyConstraintNodeConverter<T: MavenDependencyConstraintNode>(): SerializableDependencyNodeConverter<T, SerializableMavenDependencyConstraintNode>  {
    object Input: MavenDependencyConstraintNodeConverter<MavenDependencyConstraintNodeWithContext>() {
        override fun applicableTo() = MavenDependencyConstraintNodeWithContext::class
    }
    object Plain: MavenDependencyConstraintNodeConverter<SerializableMavenDependencyConstraintNode>() {
        override fun applicableTo() = SerializableMavenDependencyConstraintNode::class
    }

    override fun toEmptyNodePlain(node: T, graphContext: DependencyGraphContext): SerializableMavenDependencyConstraintNode =
        SerializableMavenDependencyConstraintNode(
            node.group, node.module, node.version,
            dependencyConstraintRef = node.dependencyConstraint.toSerializableReference(graphContext),
            messages = node.messages,
            graphContext = graphContext,
        )

    override fun fillEmptyNodePlain(nodePlain: SerializableMavenDependencyConstraintNode, node: T, graphContext: DependencyGraphContext, nodeReference: DependencyNodeReference?) {
        super.fillEmptyNodePlain(nodePlain, node, graphContext, nodeReference)
        val overriddenBy = node.overriddenBy
            .filter { !it.isOrphan(root = graphContext.allDependencyNodeReferences.entries.first().key) }
            .map { it.toSerializableReference(graphContext, null) }
        nodePlain.overriddenByRefs.addAll(overriddenBy)
    }

    companion object {
        fun converters(): List<SerializableDependencyNodeConverter<out DependencyNode, SerializableMavenDependencyConstraintNode>> = listOf(Input, Plain)
    }
}

private sealed class RootDependencyNodeConverter<T: RootDependencyNode>(): SerializableDependencyNodeConverter<T, SerializableRootDependencyNode>  {
    object Input: RootDependencyNodeConverter<RootDependencyNodeWithContext>() {
        override fun applicableTo() = RootDependencyNodeWithContext::class
    }
    object Plain: RootDependencyNodeConverter<SerializableRootDependencyNode>() {
        override fun applicableTo() = SerializableRootDependencyNode::class
    }
    object Stub: RootDependencyNodeConverter<RootDependencyNodeStub>() {
        override fun applicableTo() = RootDependencyNodeStub::class
    }

    override fun toEmptyNodePlain(node: T, graphContext: DependencyGraphContext): SerializableRootDependencyNode =
        SerializableRootDependencyNode(node.graphEntryName, graphContext = graphContext)

    companion object {
        fun converters(): List<SerializableDependencyNodeConverter<out DependencyNode, SerializableRootDependencyNode>> = listOf(Input, Plain, Stub)
    }
}