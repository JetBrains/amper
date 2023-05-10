package org.jetbrains.deft.proto.gradle.android

import com.android.build.gradle.BaseExtension
import org.jetbrains.deft.proto.frontend.AndroidArtifactPart
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.gradle.base.DeftNamingConventions
import org.jetbrains.deft.proto.gradle.base.PluginPartCtx
import org.jetbrains.deft.proto.gradle.base.SpecificPlatformPluginPart
import org.jetbrains.deft.proto.gradle.part

fun applyAndroidAttributes(ctx: PluginPartCtx) = AndroidBindingPluginPart(ctx).apply()

@Suppress("LeakingThis")
open class AndroidAwarePart(
        ctx: PluginPartCtx,
) : SpecificPlatformPluginPart(ctx, Platform.ANDROID), DeftNamingConventions {

    internal val androidPE = project.extensions.findByName("android") as BaseExtension?

    internal val androidSourceSets get() = androidPE?.sourceSets

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

    @Suppress("UnstableApiUsage")
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
                it.res.srcDir(fragment.androidResPath)
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
                compileSdkVersion(part.compileSdkVersion)
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