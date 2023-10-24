package org.jetbrains.amper.gradle.kmpp

import org.gradle.api.file.SourceDirectorySet

/**
 * Class to access private "sources" field, to be able to replace them without changing
 * (For example, do not changing provider).
 */
class ReflectionSourceDirectorySet private constructor(
    private val delegate: SourceDirectorySet
) : SourceDirectorySet by delegate {
    companion object {

        private const val sdsFqn = "org.gradle.api.internal.file.DefaultSourceDirectorySet"

        private val defaultSDSClass by lazy {
            this::class.java
                .classLoader.loadClass(sdsFqn)
        }

        private val sourcesField by lazy {
            defaultSDSClass
                .getDeclaredField("source")
                .apply { isAccessible = true }
        }

        private val sourcesGetter: (Any) -> MutableList<Any> = {
            sourcesField.get(it) as MutableList<Any>
        }

        /**
         * Try to wrap [DefaultSourceDirectorySet] with reflection adapter.
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
}