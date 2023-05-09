package org.jetbrains.deft.proto.gradle.android

import com.android.build.gradle.BaseExtension
import org.jetbrains.deft.proto.frontend.AndroidArtifactPart
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.gradle.*
import org.jetbrains.deft.proto.gradle.part

fun applyAndroidAttributes(ctx: PluginPartCtx) = AndroidBindingPluginPart(ctx).apply()

@Suppress("LeakingThis")
open class AndroidAwarePart(
    ctx: PluginPartCtx,
) : BindingPluginPart by ctx, DeftNamingConventions {

    data class BindFragment(
        internal val fragment: FragmentWrapper,
        internal val artifact: ArtifactWrapper,
    )

    private val androidArtifacts = module.artifacts.filterAndroidArtifacts()

    private val androidNonTestArtifacts = androidArtifacts.filter { !it.isTest }

    private val androidTestArtifacts = androidArtifacts.filter { it.isTest }

    // Here we rely on fact, that only one android target can be declared in KMPP,
    // so we cannot support multiple android artifacts.
    internal val leafNonTestAndroidFragment = if (androidArtifacts.isEmpty()) null else {
        val androidArtifact = androidNonTestArtifacts.singleOrNull()
            ?: error("There must be exactly one non test android artifact!")
        val leaf = androidArtifact.fragments.filterAndroidFragments().singleOrNull()
            ?: error("There must be only one non test android leaf fragment!")
        BindFragment(leaf, androidArtifact)
    }

    // Here we rely on fact, that only one android target can be declared in KMPP,
    // so we cannot support multiple android artifacts.
    internal val leafTestAndroidFragment = if (androidArtifacts.isEmpty()) null else {
        val androidArtifact = androidTestArtifacts
            .singleOrZero { error("There must be one or none test android artifact!") }
        val leaf = androidArtifact?.fragments?.filterAndroidFragments()
            ?.singleOrZero { error("There must be one or none test android leaf fragment!") }
        if (leaf != null) BindFragment(leaf, androidArtifact) else null
    }

    internal val androidPE = project.extensions.findByName("android") as BaseExtension?

    internal val androidSourceSets get() = androidPE?.sourceSets

    private fun Collection<FragmentWrapper>.filterAndroidFragments() =
        filter { it.platforms.contains(Platform.ANDROID) }

    private fun Collection<ArtifactWrapper>.filterAndroidArtifacts() =
        filter { it.platforms.contains(Platform.ANDROID) }

}

/**
 * Plugin logic, bind to specific module, when only default target is available.
 */
class AndroidBindingPluginPart(
    ctx: PluginPartCtx,
) : AndroidAwarePart(ctx) {

    /**
     * Entry point for this plugin part.
     */
    fun apply() {
        adjustAndroidSourceSets()
        applySettings()
    }

    private fun adjustAndroidSourceSets() = with(AndroidDeftNamingConvention) {
        // Clear android source sets that are not created by us.
        // Adjust that source sets whose matching kotlin source sets are created by us.
        // Can be called after project evaluation.
        androidSourceSets?.all {
            val fragment = it.deftFragment
            if (fragment != null) {
                it.kotlin.srcDir(fragment.sourcePath)
                it.java.srcDir(fragment.sourcePath)
                it.resources.srcDir(fragment.resourcePath)
                it.res.srcDir(fragment.resPath)
                it.manifest.srcFile("${fragment.sourcePath}/Manifest.xml")
            } else {
                it.kotlin.setSrcDirs(emptyList<Any>())
                it.java.setSrcDirs(emptyList<Any>())
                it.resources.setSrcDirs(emptyList<Any>())
            }
        }
    }

    private fun applySettings() {
        module.artifacts.forEach { artifact ->
            artifact.platforms.find { it == Platform.ANDROID } ?: return@forEach
            val part =
                artifact.part<AndroidArtifactPart>() ?: error("No android properties for an artifact ${artifact.name}")
            androidPE?.apply {
                part.compileSdkVersion?.let {
                    compileSdkVersion(it)
                }
                defaultConfig {
                    it.minSdkVersion(part.minSdkVersion ?: 24)
                }
                compileOptions {
                }
            }
        }

        //        androidPE.apply {
//            allCollapsed["target.android.compileSdkVersion"]?.first()?.let { compileSdkVersion(it.toInt()) }
//            defaultConfig {
//                allCollapsed["target.android.minSdkVersion"]?.first()?.let { minSdkVersion(it) }
//                allCollapsed["target.android.targetSdkVersion"]?.first()?.let { targetSdkVersion(it) }
//                allCollapsed["target.android.versionCode"]?.first()?.let { versionCode(it.toInt()) }
//                allCollapsed["target.android.versionName"]?.first()?.let { versionName(it) }
//                allCollapsed["target.android.applicationId"]?.first()?.let { applicationId(it) }
//            }
//            compileOptions {
//                allCollapsed["target.android.sourceCompatibility"]?.first()?.let { sourceCompatibility(it) }
//                allCollapsed["target.android.targetCompatibility"]?.first()?.let { targetCompatibility(it) }
//            }
//        }
    }
}