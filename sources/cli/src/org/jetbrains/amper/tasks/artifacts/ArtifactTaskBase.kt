/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.artifacts

import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.artifacts.api.Artifact
import org.jetbrains.amper.tasks.artifacts.api.ArtifactSelector
import org.jetbrains.amper.tasks.artifacts.api.ArtifactTask
import org.jetbrains.amper.tasks.artifacts.api.Quantifier
import kotlin.reflect.cast

/**
 * A base class for a typical [ArtifactTask] implementation.
 *
 * Use delegated properties for co
 *
 * To write a pure artifact-based task, use [PureArtifactTaskBase].
 * To write a hybrid task that still has to deal with unmanaged dependencies via [TaskResult]s subclass this class.
 */
abstract class ArtifactTaskBase : ArtifactTask {
    @Transient
    private val _consumes = mutableListOf<ArtifactSelector<*, *>>()
    @Transient
    private val _produces = mutableListOf<Artifact>()
    @Transient
    private var configurationCompleted = false
    @Transient
    private var inputs: Map<ArtifactSelector<*, *>, List<Artifact>>? = null

    final override val consumes: List<ArtifactSelector<*, *>>
        get() = _consumes.also { configurationCompleted = true }

    final override val produces: List<Artifact>
        get() = _produces.also { configurationCompleted = true }

    override fun injectConsumes(artifacts: Map<ArtifactSelector<*, *>, List<Artifact>>) {
        inputs = artifacts
    }

    /**
     * Adds the selector to [consumes]. Not allowed to call after the configuration is finalized.
     */
    fun addConsumes(selector: ArtifactSelector<*, *>) {
        _consumes += selector
    }

    /**
     * Adds the artifact to [produces]. Not allowed to call after the configuration is finalized.
     */
    fun <T : Artifact> addProduces(artifact: T) = artifact.also {
        _produces += artifact
    }

    internal fun rawInputs() = checkNotNull(inputs) {
        "Input artifacts are not yet injected"
    }

    // DSL

    /**
     * Records the consumption of the artifact inside the [ArtifactTaskBase] using [ArtifactTaskBase.addConsumes]
     */
    operator fun <T : Artifact, Q : Quantifier> ArtifactSelector<T, Q>.provideDelegate(
        thisRef: ArtifactTaskBase, @Suppress("unused") property: Any,
    ) = this.also {
        require(thisRef === this@ArtifactTaskBase)
        thisRef.addConsumes(it)
    }

    /**
     * Gets the single resolved artifact for the selector. Use only in [ArtifactTaskBase.run].
     */
    operator fun <T : Artifact> ArtifactSelector<T, Quantifier.Single>.getValue(
        thisRef: ArtifactTaskBase, @Suppress("unused") property: Any,
    ): T {
        require(thisRef === this@ArtifactTaskBase)
        return type.clazz.cast(thisRef.rawInputs()[this]!!.single())
    }

    /**
     * Gets the list of resolved artifacts for the selector. Use only in [ArtifactTaskBase.run].
     */
    operator fun <T : Artifact> ArtifactSelector<T, Quantifier.Multiple>.getValue(
        thisRef: ArtifactTaskBase, @Suppress("unused") property: Any,
    ): List<T> {
        require(thisRef === this@ArtifactTaskBase)
        return thisRef.rawInputs()[this]!!.map(type.clazz::cast)
    }

    /**
     * Equivalent of the [ArtifactTaskBase.addProduces]
     */
    operator fun <T : Artifact> T.provideDelegate(thisRef: ArtifactTaskBase, @Suppress("unused") property: Any) = apply {
        require(thisRef === this@ArtifactTaskBase)
        thisRef.addProduces(this)
    }

    /**
     * Compatibility for the [Artifact] to be used with `by` delegation.
     */
    operator fun <T : Artifact> T.getValue(
        @Suppress("unused") thisRef: ArtifactTaskBase, @Suppress("unused") property: Any
    ): T {
        require(thisRef === this@ArtifactTaskBase)
        return this
    }
}