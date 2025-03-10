/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle

import org.gradle.api.file.SourceDirectorySet
import java.io.File
import java.util.*
import java.util.function.Consumer

/**
 * Class to access the private "sources" field, to be able to replace them without changing
 * (For example, do not changing provider).
 */
class ReflectionSourceDirectorySet private constructor(
    private val delegate: SourceDirectorySet
) : SourceDirectorySet by delegate {
    companion object {
        private const val SOURCE_DIRECTORY_SET_FQN = "org.gradle.api.internal.file.DefaultSourceDirectorySet"

        private val defaultSDSClass by lazy {
            this::class.java.classLoader.loadClass(SOURCE_DIRECTORY_SET_FQN)
        }

        private val sourcesField by lazy {
            defaultSDSClass
                .getDeclaredField("source")
                .apply { isAccessible = true }
        }

        @Suppress("UNCHECKED_CAST")
        private val sourcesGetter: (Any) -> MutableList<Any> = {
            sourcesField.get(it) as MutableList<Any>
        }

        /**
         * Try to wrap [org.gradle.api.internal.file.DefaultSourceDirectorySet] with reflection adapter.
         */
        fun tryWrap(toWrap: SourceDirectorySet): ReflectionSourceDirectorySet? =
            if (defaultSDSClass.isInstance(toWrap))
                ReflectionSourceDirectorySet(toWrap)
            else null
    }

    init {
        assert(defaultSDSClass.isInstance(delegate))
    }

    val mutableSources get() = sourcesGetter(delegate)

    override fun forEach(action: Consumer<in File>?) {
        delegate.forEach(action)
    }

    override fun spliterator(): Spliterator<File?> {
        return delegate.spliterator()
    }
}
