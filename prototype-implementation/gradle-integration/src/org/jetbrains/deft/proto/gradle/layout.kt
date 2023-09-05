package org.jetbrains.deft.proto.gradle

import org.gradle.api.file.SourceDirectorySet
import org.jetbrains.deft.proto.frontend.Layout
import org.jetbrains.deft.proto.frontend.LeafFragment
import org.jetbrains.deft.proto.frontend.MetaModulePart
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.gradle.base.BindingPluginPart
import org.jetbrains.deft.proto.gradle.kmpp.KMPEAware
import org.jetbrains.deft.proto.gradle.kmpp.KotlinDeftNamingConvention.kotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import java.io.File


/**
 * For now there is a convention - null return means "do not touch
 * existing layout", else "overwrite it with return value".
 *
 */
sealed interface LayoutMode {
    context(KMPEAware)
    fun FragmentWrapper.modifyManagedSources(): List<File>?

    context(KMPEAware)
    fun FragmentWrapper.modifyManagedResources(): List<File>?

    context(KMPEAware)
    fun KotlinSourceSet.modifyUnmanagedSources(): List<File>?

    context(KMPEAware)
    fun KotlinSourceSet.modifyUnmanagedResources(): List<File>?
}

/**
 * Layout mode that leaves all source sets untouched, except `common` and `commonMain` source sets.
 * `commonMain` is hidden and "replaced" by `common`.
 */
data object GradleLayoutMode : LayoutMode {
    context(KMPEAware)
    private fun FragmentWrapper.modifyManaged(
        getter: KotlinSourceSet.() -> SourceDirectorySet
    ): List<File>? {
        if (name != "common") return null
        val commonMainSources = kotlinMPE.sourceSets.findByName("commonMain")?.getter() ?: return null
        val commonMainSrcDirs = commonMainSources.srcDirs
        commonMainSources.setSrcDirs(emptyList<File>())
        return commonMainSrcDirs.toList()
    }

    // Replace "common" source set directories by "commonMain".
    context(KMPEAware)
    override fun FragmentWrapper.modifyManagedSources() =
        modifyManaged(KotlinSourceSet::kotlin)

    context(KMPEAware)
    override fun FragmentWrapper.modifyManagedResources() =
        modifyManaged(KotlinSourceSet::resources)


    // Clear all directories for non managed common main source set.
    // (since it is replaced by common source set)
    context(KMPEAware)
    private fun KotlinSourceSet.modifyNonManaged() =
        if (name == "commonMain") emptyList<File>() else null

    context(KMPEAware)
    override fun KotlinSourceSet.modifyUnmanagedSources() = modifyNonManaged()

    context(KMPEAware)
    override fun KotlinSourceSet.modifyUnmanagedResources() = modifyNonManaged()
}

/**
 * Layout mode that works like [GradleLayoutMode], but also, renames "jvm" source set directories
 * by "main"/"test" maven like to make it kotlin("jvm") compatible.
 */
data object GradleJvmLayoutMode : LayoutMode by GradleLayoutMode {
    context(KMPEAware)
    private fun FragmentWrapper.modifyManaged(
        getter: KotlinSourceSet.() -> SourceDirectorySet,
    ) = if (this is LeafFragment && platform == Platform.JVM) {
        val newName = if (isTest) "test" else "main"
        kotlinSourceSet?.getter()?.srcDirs?.map { it.replaceLast(newName) { it == name } }
    } else null

    // TODO Add check, that there is only one platform (JVM) and JVM like Deft setup.
    context(KMPEAware)
    override fun FragmentWrapper.modifyManagedSources() =
        modifyManaged(KotlinSourceSet::kotlin)
    context(KMPEAware)
    override fun FragmentWrapper.modifyManagedResources() =
        modifyManaged(KotlinSourceSet::resources)

    // TODO Nullify all leaf KMPP created source sets.
}

data object DeftLayoutMode : LayoutMode {
    context(KMPEAware)
    override fun FragmentWrapper.modifyManagedSources() = listOf(src.toFile())
    context(KMPEAware)
    override fun FragmentWrapper.modifyManagedResources() = listOf(resourcesPath.toFile())
    context(KMPEAware)
    override fun KotlinSourceSet.modifyUnmanagedSources() = emptyList<File>()
    context(KMPEAware)
    override fun KotlinSourceSet.modifyUnmanagedResources() = emptyList<File>()
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