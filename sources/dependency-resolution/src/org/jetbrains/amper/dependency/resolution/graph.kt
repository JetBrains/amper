/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import org.jetbrains.amper.dependency.resolution.diagnostics.Message
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists

@Serializable(/*with=DependencyGraphSerializer::class*/)
class DependencyGraph(
    val graphContext: DependencyGraphContext,
    val root: DependencyNodeReference,
) {
    companion object {
        fun DependencyNode.toGraph(): DependencyGraph {
            val graphContext = DependencyGraphContext()
            val serializableNode = try {
                DependencyGraphContext.currentGraphContext.set(graphContext)
                this.toSerializableReference(graphContext)
            } finally {
                DependencyGraphContext.currentGraphContext.set(null)
            }
            return DependencyGraph(graphContext, serializableNode)
        }
    }
}

private class DependencyGraphSerializer: KSerializer<DependencyGraph> {
    private val graphContextSerializer: KSerializer<DependencyGraphContext> = DependencyGraphContext.serializer()
    private val rootNodeSerializer: KSerializer<DependencyNodeReference> = DependencyNodeReference.serializer()

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(
        "org.jetbrains.amper.dependency.resolution.DependencyGraphSerializer") {
        element("graphContext", graphContextSerializer.descriptor)
        element("root", rootNodeSerializer.descriptor)
    }

    override fun serialize(encoder: Encoder, value: DependencyGraph) {
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, graphContextSerializer, value.graphContext)
            encodeSerializableElement(descriptor, 1, rootNodeSerializer, value.root)
        }
    }

    override fun deserialize(decoder: Decoder): DependencyGraph {
        return decoder.decodeStructure(descriptor) {
            var graphContext: DependencyGraphContext? = null
            var root: DependencyNodeReference? = null
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> graphContext = decodeSerializableElement(descriptor, 0, graphContextSerializer)
                    1 -> {
                        root = try {
                            DependencyGraphContext.currentGraphContext.set(graphContext)
                            decodeSerializableElement(descriptor, 1, rootNodeSerializer)
                        } finally {
                            DependencyGraphContext.currentGraphContext.set(null)
                        }
                    }
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            DependencyGraph(graphContext!!, root!!)
        }


//        return decoder.decodeStructure(descriptor) {
//            val graphContext = decodeSerializableElement(descriptor, 0, graphContextSerializer)
//
//            val root =  try {
//                DependencyGraphContext.currentGraphContext.set(graphContext)
//                decodeSerializableElement(descriptor, 1, rootNodeSerializer)
//            } finally {
//                DependencyGraphContext.currentGraphContext.set(null)
//            }
//
//            DependencyGraph(graphContext, root)
//        }
    }
}

//// todo (AB) : Serialize to Int instead of object to decrease size of serialized graph
//@Serializable(with = DependencyNodeReferenceSerializer::class)
//class DependencyNodeReference(
//    internal val index: DependencyNodeIndex,
//    @Transient
//    private val graphContext: DependencyGraphContext = emptyGraphContext
//) : DependencyNodePlain by graphContext.getDependencyNode<DependencyNodePlain>(index) {
//
//    fun toNodePlain(graphContext: DependencyGraphContext): DependencyNodePlain =
//        graphContext.getDependencyNode(index)
//}

// todo (AB) : Serialize to Int instead of object to decrease size of serialized graph
@Serializable/*(with = DependencyNodeReferenceSerializer::class)*/
class DependencyNodeReference(
    internal val index: DependencyNodeIndex,
//    @Transient
//    private val graphContext: DependencyGraphContext = emptyGraphContext
) {
    fun toNodePlain(graphContext: DependencyGraphContext): DependencyNodePlain =
        graphContext.getDependencyNode(index)
}

//private class DependencyNodeReferenceSerializer: KSerializer<DependencyNodeReference> {
//    private val indexSerializer = Int.serializer()
//
//    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("dnRef") {
//        element("i", indexSerializer.descriptor)
//    }
//
//
//    override fun serialize(encoder: Encoder, value: DependencyNodeReference) {
//        encoder.encodeStructure(descriptor) {
//            encoder.encodeSerializableValue(indexSerializer, value.index)
//        }
//    }
//
//    override fun deserialize(decoder: Decoder): DependencyNodeReference {
//        val index = decoder.decodeStructure(descriptor) {
//            decodeIntElement(descriptor, 0)
//        }
//        return DependencyNodeReference(index, DependencyGraphSerializer.graphContextHolder.get()!!) // todo (AB) : reuse reference instances
//    }
//}

//interface DependencyNodePlain : DependencyNode {
//    override val parents: List<DependencyNodeReference>
//    override val children: List<DependencyNodeReference>
//
//    fun toDependencyNode(): DependencyNode
//
//    override fun toSerializableReference(graphContext: DependencyGraphContext): DependencyNodeReference = plainNodeSerializationError()
//}

interface DependencyNodePlain : DependencyNode {
    val parentsRefs: List<DependencyNodeReference>
    val childrenRefs: List<DependencyNodeReference>

    override val parents: MutableList<DependencyNode>

    override fun toSerializableReference(graphContext: DependencyGraphContext): DependencyNodeReference = plainNodeSerializationError()
}

internal fun plainNodeSerializationError(): Nothing {
    error("Plain nodes are not intended to be serialized")
}

/**
 * A dependency node is a graph element.
 *
 * It has the following properties.
 *
 * - Holds a context relevant for it and its children.
 * - Can be compared by a [Key] that's the same for all nodes with equal coordinates but different versions.
 * - Has mutable state, children, and messages that could change as a result of the resolution process.
 *
 * By the resolution process we mean finding the node's dependencies (children) according to provided context,
 * namely, a [ResolutionScope] and a platform.
 */
// todo (AB) : Most of usages of children with resolution context should be replaced with interfaces
interface DependencyNode {
    val parents: List<DependencyNode>
    val key: Key<*>
    val children: List<DependencyNode>
    val messages: List<Message>

    /**
     * Every node should provide a Serializable representation of itself that will be used during graph serialization
     * and deserialization.
     *
     * @context holds reusable objects that could be referenced from the node. Context is mutable.
     * If the node references some reusable object,
     * then this method should add the object into the context and use a Reference type for the returned Serializable form.
     * This way the same object won't be stored several times in JSON and will be correctly deserialized from the given context
    */
    // todo (AB) : It should be reference here. Also, to prevent loops, created type should be immediately registered,
    // todo (AB) : before running into recursion for references.
    fun toSerializableReference(graphContext: DependencyGraphContext): DependencyNodeReference

    /**
     * Returns a sequence of distinct nodes using BFS starting at (and including) this node.
     *
     * The given [childrenPredicate] can be used to skip parts of the graph.
     * If [childrenPredicate] is false for a node, the node is skipped and will not appear in the sequence.
     * The subgraph of the skipped node is also not traversed, so these descendant nodes won't be in the sequence unless
     * they are reached via some other node.
     * Using [childrenPredicate] is, therefore, different from filtering the resulting sequence after the fact.
     *
     * The nodes are distinct in terms of referential identity, which is enough to eliminate duplicate "requested"
     * dependency triplets. This does NOT eliminate nodes that requested the same dependency in different versions,
     * even though conflict resolution should make them point to the same dependency version internally eventually.
     *
     * The returned sequence is guaranteed to be finite, as it prunes the graph when encountering duplicates (and thus cycles).
     */
    fun distinctBfsSequence(childrenPredicate: (DependencyNode, DependencyNode) -> Boolean = { _,_ -> true }): Sequence<DependencyNode> = sequence {
        val queue = LinkedList(listOf(this@DependencyNode))
        val visited = mutableSetOf<DependencyNode>()
        while (queue.isNotEmpty()) {
            val node = queue.remove()
            if (visited.add(node)) {
                yield(node)
                queue.addAll(node.children.filter { it !in visited && childrenPredicate(it, node) })
            }
        }
    }

    /**
     * Prints the graph below the node using a Gradle-like output style.
     */
    fun prettyPrint(forMavenNode: MavenCoordinates? = null): String = buildString {
        val allMavenDepsKeys = distinctBfsSequence()
            .map { it.unwrap() }
            .filterIsInstance<MavenDependencyNode>()
            .groupBy { it.key }
        prettyPrint(this, allMavenDepsKeys,forMavenNode = forMavenNode)
    }

    private fun DependencyNode.unwrap(): DependencyNode =
        when (this) {
            is DependencyNodeWithChildren -> node.unwrap()
            else -> this
        }

    private fun prettyPrint(
        builder: StringBuilder,
        allMavenDepsKeys: Map<Key<MavenDependency>, List<MavenDependencyNode>>,
        indent: StringBuilder = StringBuilder(),
        visited: MutableSet<Pair<Key<*>, Any?>> = mutableSetOf(),
        addLevel: Boolean = false,
        forMavenNode: MavenCoordinates? = null
    ) {
        val thisUnwrapped = unwrap()
        builder.append(indent).append(toString())

        // key doesn't include a version on purpose,
        // but different nodes referencing the same MavenDependency result in the same dependencies
        // => add no need to distinguish those while pretty printing
        val seen = !visited.add(key to ((thisUnwrapped as? MavenDependencyNode)?.dependency ?: (thisUnwrapped as? MavenDependencyConstraintNode)?.dependencyConstraint))
        if (seen && children.any { it.shouldBePrinted(allMavenDepsKeys, forMavenNode) }) {
            builder.append(" (*)")
        } else if (thisUnwrapped is MavenDependencyConstraintNode) {
            builder.append(" (c)")
        }
        builder.append('\n')
        if (seen || children.isEmpty()) {
            return
        }

        if (indent.isNotEmpty()) {
            indent.setLength(indent.length - 5)
            if (addLevel) {
                indent.append("│    ")
            } else {
                indent.append("     ")
            }
        }

        children
            .filter { it.shouldBePrinted(allMavenDepsKeys, forMavenNode) }
            .let { filteredNodes ->
                filteredNodes.forEachIndexed { i, it ->
                    val addAnotherLevel = i < filteredNodes.size - 1
                    if (addAnotherLevel) {
                        indent.append("├─── ")
                    } else {
                        indent.append("╰─── ")
                    }
                    it.prettyPrint(builder, allMavenDepsKeys, indent, visited, addAnotherLevel, forMavenNode)
                    indent.setLength(indent.length - 5)
                }
            }
    }

    fun DependencyNode.shouldBePrinted(
        allMavenDepsKeys: Map<Key<MavenDependency>, List<MavenDependencyNode>>,
        forMavenNode: MavenCoordinates? = null
    ): Boolean = unwrap().let {
        it !is MavenDependencyConstraintNode || it.isConstraintAffectingTheGraph(
            allMavenDepsKeys,
            forMavenNode
        )
    }

    fun MavenDependencyConstraintNode.isConstraintAffectingTheGraph(
        allMavenDepsKeys: Map<Key<MavenDependency>, List<MavenDependencyNode>>,
        forMavenNode: MavenCoordinates?
    ): Boolean =
        allMavenDepsKeys[this.key]?.let { affectedNode ->
            // If there is a dependency with the original version equal to the constraint version, then constraint is noop.
            affectedNode.none { dep ->
                dep.originalVersion == dep.dependency.version && dep.originalVersion == this.version.resolve()
            }
                    &&
                    // The constraint version is the same as the resulted dependency version,
                    // and the original dependency version is different (⇒ constraint might have affected resolution)
                    affectedNode.any { dep ->
                        this.version.resolve() == dep.dependency.version && dep.originalVersion != dep.dependency.version
                    }
        } ?: forMavenNode?.let { group == it.groupId && module == it.artifactId }
        ?: false

    suspend fun dependencyPaths(nodeBlock: (DependencyNode) -> Unit = {}): List<Path> {
        val files = mutableSetOf<Path>()
        for (node in distinctBfsSequence()) {
            if (node is MavenDependencyNode) {
                node.dependency
                    .files()
                    .mapNotNull { it.path }
                    .forEach { file ->
                        check(file.exists()) {
                            "File '$file' was returned from dependency resolution, but is missing on disk"
                        }
                        files.add(file)
                    }
            }
            nodeBlock(node)
        }
        return files.toList()
    }
}