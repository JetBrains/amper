package org.jetbrains.deft.proto.gradle

import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.SourceSet
import org.jetbrains.deft.proto.frontend.Layout
import org.jetbrains.deft.proto.frontend.MetaModulePart
import org.jetbrains.deft.proto.gradle.base.BindingPluginPart
import org.jetbrains.deft.proto.gradle.kmpp.KMPEAware
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import java.io.File


/**
 * For now there is a convention - null return means "do not touch
 * existing layout", else "overwrite it with return value".
 *
 */
// TODO Rewrite the whole approach when we migrate to external target API.
sealed interface LayoutMode {
    /**
     * Is called upon any source set, that has bind fragment.
     */
    context(KMPEAware) fun FragmentWrapper.modifyManagedSources(
        sourceSet: Any,
        oldSources: Collection<File>?
    ): List<File>?

    /**
     * Is called upon any source set, that has bind fragment.
     */
    context(KMPEAware) fun FragmentWrapper.modifyManagedResources(
        sourceSet: Any,
        oldSources: Collection<File>?
    ): List<File>?

    /**
     * Is called upon kotlin source sets that are not bind to fragment.
     */
    context(KMPEAware) fun modifyUnmanagedSources(sourceSet: KotlinSourceSet): List<File>?

    /**
     * Is called upon kotlin source sets that are not bind to fragment.
     */
    context(KMPEAware) fun modifyUnmanagedResources(sourceSet: KotlinSourceSet): List<File>?
}

/**
 * Layout mode that leaves all source sets untouched, except `common` and `commonMain` source sets.
 * `commonMain` is hidden and "replaced" by `common`.
 */
data object GradleLayoutMode : LayoutMode {
    context(KMPEAware) private fun FragmentWrapper.modifyManaged(
        getter: KotlinSourceSet.() -> SourceDirectorySet
    ): List<File>? {
        if (name != "common") return null
        val commonMainSources = kotlinMPE.sourceSets.findByName("commonMain")?.getter() ?: return null
        val commonMainSrcDirs = commonMainSources.srcDirs
        return commonMainSrcDirs.toList()
    }

    // Replace "common" source set directories by "commonMain".
    context(KMPEAware) override fun FragmentWrapper.modifyManagedSources(
        sourceSet: Any,
        oldSources: Collection<File>?
    ) = modifyManaged(KotlinSourceSet::kotlin)

    context(KMPEAware) override fun FragmentWrapper.modifyManagedResources(
        sourceSet: Any,
        oldSources: Collection<File>?
    ) = modifyManaged(KotlinSourceSet::resources)


    // Clear all directories for non managed common main source set.
    // (since it is replaced by common source set)
    context(KMPEAware) private fun modifyNonManaged(sourceSet: KotlinSourceSet) =
        if (sourceSet.name == "commonMain") emptyList<File>() else null

    context(KMPEAware) override fun modifyUnmanagedSources(sourceSet: KotlinSourceSet) = modifyNonManaged(sourceSet)
    context(KMPEAware) override fun modifyUnmanagedResources(sourceSet: KotlinSourceSet) = modifyNonManaged(sourceSet)
}

/**
 * Layout mode that works like [GradleLayoutMode], but also, renames "jvmMain" source set directories
 * by "main"/"test" maven like to make it kotlin("jvm") compatible.
 */
data object GradleJvmLayoutMode : LayoutMode by GradleLayoutMode {
    context(KMPEAware) private fun FragmentWrapper.modifyManaged(
        sourceSet: Any,
        oldSources: Collection<File>?,
    ) = if (sourceSet is SourceSet && (sourceSet.name == "main" || sourceSet.name == "test")) {
        val newName = if (isTest) "test" else "main"
        oldSources?.map { it.replacePenultimate(newName) }
    } else null

    context(KMPEAware) override fun FragmentWrapper.modifyManagedSources(
        sourceSet: Any,
        oldSources: Collection<File>?
    ) = modifyManaged(sourceSet, oldSources)

    context(KMPEAware) override fun FragmentWrapper.modifyManagedResources(
        sourceSet: Any,
        oldSources: Collection<File>?
    ) = modifyManaged(sourceSet, oldSources)

    context(KMPEAware) private fun modifyUnmanaged(
        sourceSet: KotlinSourceSet,
        getter: KotlinSourceSet.() -> SourceDirectorySet,
    ): List<File>? {
        return when {
            sourceSet.name == "jvmMain" -> sourceSet.getter().srcDirs.map { it.replacePenultimate("main") }
            sourceSet.name == "jvmTest" -> sourceSet.getter().srcDirs.map { it.replacePenultimate("test") }
            else -> null
        }
    }

    context(KMPEAware) override fun modifyUnmanagedSources(sourceSet: KotlinSourceSet) =
        modifyUnmanaged(sourceSet, KotlinSourceSet::kotlin)
    context(KMPEAware) override fun modifyUnmanagedResources(sourceSet: KotlinSourceSet) =
        modifyUnmanaged(sourceSet, KotlinSourceSet::resources)
}

data object DeftLayoutMode : LayoutMode {
    context(KMPEAware)
    override fun FragmentWrapper.modifyManagedSources(sourceSet: Any, oldSources: Collection<File>?) =
        listOf(src.toFile())

    context(KMPEAware)
    override fun FragmentWrapper.modifyManagedResources(sourceSet: Any, oldSources: Collection<File>?) =
        listOf(resourcesPath.toFile())

    context(KMPEAware) override fun modifyUnmanagedSources(sourceSet: KotlinSourceSet) =
        emptyList<File>()
    context(KMPEAware) override fun modifyUnmanagedResources(sourceSet: KotlinSourceSet) =
        emptyList<File>()
}

private val BindingPluginPart.layout
    get() = (module.parts.find<MetaModulePart>()
        ?: error("No mandatory MetaModulePart in the module ${module.userReadableName}"))
        .layout

val layoutToMode = mapOf(
    Layout.DEFT to DeftLayoutMode,
    Layout.GRADLE to GradleLayoutMode,
    Layout.GRADLE_JVM to GradleJvmLayoutMode,
)

val BindingPluginPart.layoutMode
    get() = layoutToMode[layout]!!