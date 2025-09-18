/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
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

/**
 * A dependency node is a graph element.
 *
 * It has the following properties.
 *
 * - Could have children, the loops are prohibited.
 * - Can be compared by a [Key] that's the same for all nodes with equal coordinates but different versions.
 *
 * By the resolution process we mean finding the node's dependencies (children) according to provided context,
 * namely, a [ResolutionScope] and a platform.
 */
interface DependencyNode {
    val parents: Set<DependencyNode>
    val key: Key<*>
    val children: List<DependencyNode>
    val messages: List<Message>

    /**
     * String representation of the node used in the dependency graph output produced by the command 'show dependencies'
     */
    val graphEntryName: String

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
    fun distinctBfsSequence(
        childrenPredicate: (child: DependencyNode, parent: DependencyNode) -> Boolean = { _,_ -> true }
    ): Sequence<DependencyNode> = sequence {
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

    // todo (AB) : We should probably move serialization-related methods outside DependencyNode
    // todo (AB) : (as an extension functions written somewhere separately)
    fun toEmptyNodePlain(graphContext: DependencyGraphContext): DependencyNodePlain

    fun fillEmptyNodePlain(nodePlain: DependencyNodePlain, graphContext: DependencyGraphContext, nodeReference: DependencyNodeReference?) {
        val children = children.map { it.toSerializableReference(graphContext, nodeReference) }

        if (children.isNotEmpty()) {
            (nodePlain.childrenRefs as MutableList<DependencyNodeReference>).addAll(children)
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
        builder.append(indent).append(graphEntryName)

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

    fun dependencyPaths(nodeBlock: (DependencyNode) -> Unit = {}): List<Path> {
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

/**
 * This method creates serializable representation of the node
 * (which is presented by the type implementing [DependencyNodePlain] and annotated with [Serializable]),
 * and register it in the given context, getting [DependencyNodeReference] as a result of the registration.
 *
 * Children of the nodes in the created [DependencyNodePlain] are represented with the references [DependencyNodeReference]
 * and obtained by traversing the children of this node calling this method recursively.
 *
 * To prevent loops during serialization, the creation of the serializable object representing the node
 * is split into two steps:
 * 1. First, a plain data object is created with help of [DependencyNode.toEmptyNodePlain] with all references on other nodes left empty,
 * but all other plain data get filled.
 * This plain object is immediately registered in the given context,
 * getting [DependencyNodeReference] as a result of the registration.
 * 2. Before the method returns the resulting reference,
 * the second step is performed that populates references to other nodes: [DependencyNode.fillEmptyNodePlain].
 * First, children of the node are populated calling this method recursively.
 * Since the resolution graph doesn't contain loops, after recursion is finished,
 * all nodes of the graph will be registered in the given context.
 * After that, all other kinds of references to the nodes might be populated (in an overridden [DependencyNode.fillEmptyNodePlain])
 *
 * @context holds reusable objects that could be referenced from the node. Context is mutable.
 * If the node references some reusable object,
 * then this method should add the object into the context and use a Reference type for the returned Serializable form.
 * This way the same object won't be stored several times in JSON and will be correctly deserialized
 * from the given context
 *
 * @parent is a reference to the parent node of the node that is being serialized.
 * it is propagated to the API of [DependencyGraphContext] that registers it as a parent of the node.
 */
fun DependencyNode.toSerializableReference(graphContext: DependencyGraphContext, parent: DependencyNodeReference?): DependencyNodeReference {
    return graphContext.getDependencyNodeReferenceAndSetParent(this, parent)
        ?: run {
            // 1. Create an empty reference first (to break cycles)
            val newNodePlain = toEmptyNodePlain(graphContext)

            // 2. register empty reference (to break cycles)
            val newReference = graphContext.registerDependencyNodePlainWithParent(this, newNodePlain, parent)

            // 3. enrich it with references
            fillEmptyNodePlain(newNodePlain, graphContext, newReference)

            newReference
        }
}

@Serializable
class DependencyGraph(
    val graphContext: DependencyGraphContext,
    val root: DependencyNodeReference,
) {
    companion object {
        fun DependencyNode.toGraph(): DependencyGraph {
            val graphContext = DependencyGraphContext()
            val serializableNode = this.toSerializableReference(graphContext, null)
            return DependencyGraph(graphContext, serializableNode)
        }
    }
}

// todo (AB) : Serialize to Int instead of object to decrease size of serialized graph
@Serializable
data class DependencyNodeReference(
    internal val index: DependencyNodeIndex
) {
    fun toNodePlain(graphContext: DependencyGraphContext): DependencyNodePlain =
        graphContext.getDependencyNode(index)
}


@Serializable(with = DependencyGraphContextSerializer::class)
class DependencyGraphContext(
    val allDependencyNodes: MutableMap<DependencyNodePlain, DependencyNodeIndex> = mutableMapOf(),
    val allMavenDependencies: MutableMap<MavenDependencyPlain, MavenDependencyIndex> = mutableMapOf(),
    val allMavenDependencyConstraints: MutableMap<MavenDependencyConstraintPlain, MavenDependencyConstraintIndex> = mutableMapOf()
) {

    @Transient
    val allDependencyNodeReferences: MutableMap<DependencyNode, DependencyNodeReference> = mutableMapOf()
    @Transient
    val allMavenDependencyReferences: MutableMap<MavenDependency, MavenDependencyReference> = mutableMapOf()
    @Transient
    val allMavenDependencyConstraintReferences: MutableMap<MavenDependencyConstraint, MavenDependencyConstraintReference> = mutableMapOf()

    /**
     * The transient fields below provide instant access to cached values by index.
     * It is assumed that indexes used for serialization match the indexes of serialized Map
     */
    @Transient
    var allDependencyNodesList: List<DependencyNodePlain> = emptyList()
        get() {
            if (field.size != allDependencyNodes.size) {
                field = allDependencyNodes.keys.toList()
            }
            return field
        }
        private set

    @Transient
    var mavenDependenciesList: List<MavenDependencyPlain> = emptyList()
        get() {
            if (field.size != allMavenDependencies.size) {
                field = allMavenDependencies.keys.toList()
            }
            return field
        }
        private set

    @Transient
    var mavenDependenciesConstraintsList: List<MavenDependencyConstraint> = emptyList()
        get() {
            if (field.size != allMavenDependencyConstraints.size) {
                field = allMavenDependencyConstraints.keys.toList()
            }
            return field
        }
        private set

    fun <Node: DependencyNode, NodePlain: DependencyNodePlain> registerDependencyNodePlainWithParent(
        node: Node, nodePlain: NodePlain, parent: DependencyNodeReference?
    ): DependencyNodeReference {
        return registerDependencyNodePlain(node, nodePlain)
            .also {
                parent?.let { nodePlain.parentsRefs.add(it) }
            }
    }

    private fun <Node: DependencyNode, NodePlain: DependencyNodePlain> registerDependencyNodePlain(
        node: Node, nodePlain: NodePlain
    ): DependencyNodeReference {
        if (allDependencyNodeReferences[node] != null) error("Node plain for node $node is already registered")
        if (allDependencyNodes[nodePlain] != null) {
            error("Reference for node $node is already registered")
        }

        val refIndex = allDependencyNodeReferences.size
        allDependencyNodes[nodePlain] = refIndex

        val reference = DependencyNodeReference(refIndex)
        allDependencyNodeReferences[node] = reference

        return reference
    }

    fun registerMavenDependencyPlain(mavenDependency: MavenDependency, nodePlain: MavenDependencyPlain): MavenDependencyReference {
        if (allMavenDependencyReferences[mavenDependency] != null) error("Plain maven dependency for maven dependency $mavenDependency is already registered")
        if (allMavenDependencies[nodePlain] != null) error("Reference for node $mavenDependency is already registered")

        val refIndex = allMavenDependencyReferences.size
        allMavenDependencies[nodePlain] = refIndex

        val reference = MavenDependencyReference(refIndex)
        allMavenDependencyReferences[mavenDependency] = reference

        return reference
    }

    fun registerMavenDependencyConstraintPlain(constraint: MavenDependencyConstraint, nodePlain: MavenDependencyConstraintPlain): MavenDependencyConstraintReference {
        if (allMavenDependencyConstraintReferences[constraint] != null) error("Plain maven dependency constraint for maven dependency constraint $constraint is already registered")
        if (allMavenDependencyConstraints[nodePlain] != null) error("Reference for node $constraint is already registered")

        val refIndex = allMavenDependencyConstraintReferences.size
        allMavenDependencyConstraints[nodePlain] = refIndex

        val reference = MavenDependencyConstraintReference(refIndex)
        allMavenDependencyConstraintReferences[constraint] = reference

        return reference
    }

    inline fun <reified T: DependencyNode> getDependencyNodeReference(node: T): DependencyNodeReference? {
        return allDependencyNodeReferences[node]
    }

    inline fun <reified T: DependencyNode> getDependencyNodeReferenceAndSetParent(node: T, parent: DependencyNodeReference?): DependencyNodeReference? {
        return getDependencyNodeReference(node)
            ?.apply {
                val nodePlain = this.toNodePlain(this@DependencyGraphContext)
                parent?.let { nodePlain.parentsRefs.add(it) }
            }
    }

    fun getMavenDependencyReference(mavenDependency: MavenDependency): MavenDependencyReference? {
        return allMavenDependencyReferences[mavenDependency]
    }

    fun getMavenDependencyConstraintReference(constraint: MavenDependencyConstraint): MavenDependencyConstraintReference? {
        return allMavenDependencyConstraintReferences[constraint]
    }

    inline fun <reified T: DependencyNodePlain> getDependencyNode(index: DependencyNodeIndex): T {
        val node = allDependencyNodesList.getOrNull(index)

        node ?: run {
            error("Dependency with index $index is absent in the graph")
        }
        (node as? T) ?: error("Dependency with index $index is of type ${node::class.simpleName} while ${T::class.simpleName} is expected")

        return node
    }

    fun getMavenDependency(index: MavenDependencyIndex): MavenDependencyPlain {
        return mavenDependenciesList.getOrNull(index) ?: error("MavenDependency with index $index is absent in the graph")
    }

    fun getMavenDependencyConstraint(index: MavenDependencyConstraintIndex): MavenDependencyConstraint {
        return mavenDependenciesConstraintsList.getOrNull(index) ?: error("MavenDependencyConstraint with index $index is absent in the graph")
    }

    companion object {
        val currentGraphContext = ThreadLocal<DependencyGraphContext?>()
    }
}

private class DependencyGraphContextSerializer: KSerializer<DependencyGraphContext> {
    private val allDependencyNodesSerializer: KSerializer<Map<DependencyNodePlain, DependencyNodeIndex>> =
        MapSerializer(PolymorphicSerializer(DependencyNodePlain::class), Int.serializer())
    private val allMavenDependenciesSerializer: KSerializer<Map<MavenDependencyPlain, MavenDependencyIndex>> =
        MapSerializer(MavenDependencyPlain.serializer(), Int.serializer())
    private val allMavenDependencyConstraintsSerializer: KSerializer<Map<MavenDependencyConstraintPlain, DependencyNodeIndex>> =
        MapSerializer(MavenDependencyConstraintPlain.serializer(), Int.serializer())

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(
        "org.jetbrains.amper.dependency.resolution.DependencyGraphContextSerializer") {
        element("allDependencyNodes", allDependencyNodesSerializer.descriptor)
        element("allMavenDependencies", allMavenDependenciesSerializer.descriptor)
        element("allMavenDependencyConstraints", allMavenDependencyConstraintsSerializer.descriptor)
    }

    override fun serialize(encoder: Encoder, value: DependencyGraphContext) {
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, allDependencyNodesSerializer, value.allDependencyNodes)
            encodeSerializableElement(descriptor, 1, allMavenDependenciesSerializer, value.allMavenDependencies)
            encodeSerializableElement(descriptor, 2, allMavenDependencyConstraintsSerializer, value.allMavenDependencyConstraints)
        }
    }

    override fun deserialize(decoder: Decoder): DependencyGraphContext {
        return decoder.decodeStructure(descriptor) {
            val graphContext = DependencyGraphContext()

            var allDependencyNodes: MutableMap<DependencyNodePlain, DependencyNodeIndex>? = null
            var allMavenDependencies: MutableMap<MavenDependencyPlain, MavenDependencyIndex>? = null
            var allMavenDependencyConstraints: MutableMap<MavenDependencyConstraintPlain, MavenDependencyConstraintIndex>? = null

            try {
                DependencyGraphContext.currentGraphContext.set(graphContext)

                while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        0 -> {
                            allDependencyNodes =
                                decodeSerializableElement(descriptor, 0, allDependencyNodesSerializer).toMutableMap()
                        }
                        1 -> {
                            allMavenDependencies =
                                decodeSerializableElement(descriptor, 1, allMavenDependenciesSerializer).toMutableMap()
                        }
                        2 -> {
                            allMavenDependencyConstraints =
                                decodeSerializableElement(descriptor, 2, allMavenDependencyConstraintsSerializer).toMutableMap()
                        }
                        CompositeDecoder.DECODE_DONE -> break
                        else -> error("Unexpected index: $index")
                    }
                }
            } finally {
                DependencyGraphContext.currentGraphContext.set(null)
            }

            graphContext.allDependencyNodes.putAll(allDependencyNodes!!)
            graphContext.allMavenDependencies.putAll(allMavenDependencies!!)
            graphContext.allMavenDependencyConstraints.putAll(allMavenDependencyConstraints!!)

            graphContext
        }
    }
}

typealias DependencyNodeIndex = Int
typealias MavenDependencyIndex = Int
typealias MavenDependencyConstraintIndex = Int

fun currentGraphContext(): DependencyGraphContext = DependencyGraphContext.currentGraphContext.get()
    ?: error("Instance of DependencyGraphContext should be either explicitly passed to the constructor or presented in the dedicated ThreadLocal")

interface SerializableDependencyNode : DependencyNode {
    val parentsRefs: MutableSet<DependencyNodeReference>
    val childrenRefs: List<DependencyNodeReference>

    override val parents: MutableSet<DependencyNode>
}

@Serializable
abstract class SerializableDependencyNodeBase(
    @Transient
    private val graphContext: DependencyGraphContext = currentGraphContext()
) : SerializableDependencyNode {
    abstract override val parentsRefs: MutableSet<DependencyNodeReference>
    abstract override val childrenRefs: List<DependencyNodeReference>

    override val parents: MutableSet<DependencyNode> by lazy { parentsRefs.map { it.toNodePlain(graphContext) }.toMutableSet() }
    override val children: List<DependencyNode> by lazy { childrenRefs.map { it.toNodePlain(graphContext) } }

    override fun toString() = graphEntryName
}

/**
 * @return true if there is no path (even transitive) to the given root from the node via node's parents
 */
fun DependencyNode.isOrphan(root: DependencyNode, visited: MutableSet<DependencyNode> = mutableSetOf()): Boolean {
    if (this == root) return false // this is the root node
    if (this in visited) return true // already tried this node

    if (parents.isEmpty()) return true // no parents => orphan
    if (parents.contains(root)) return false // we reach the root

    visited.add(this)
    return parents.all { it.isOrphan(root, visited) }
}