package org.example

import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.internal.dsl.DefaultConfig
import org.example.api.Model
import org.gradle.api.Project

/**
 * Plugin logic, bind to specific module, when only default target is available.
 */
class AndroidModulePlugin(
    ctx: ModulePluginCtx,
) : ModulePlugin by ctx {

    private val androidPE: CommonExtension<*, *, DefaultConfig, *> =
        project.extensions.getByType(CommonExtension::class.java) as CommonExtension<*, *, DefaultConfig, *>

    fun apply() {
        androidPE.apply {
            allCollapsed["target.android.compileSkdVersion"]?.first()?.let { compileSdkVersion(it) }
            defaultConfig {
                allCollapsed["target.android.minSdkVersion"]?.first()?.let { minSdkVersion(it) }
                allCollapsed["target.android.targetSdkVersion"]?.first()?.let { targetSdkVersion(it) }
                allCollapsed["target.android.versionCode"]?.first()?.let { versionCode(it.toInt()) }
                allCollapsed["target.android.versionName"]?.first()?.let { versionName(it) }
                allCollapsed["target.android.applicationId"]?.first()?.let { applicationId(it) }
            }
            compileOptions {
                allCollapsed["target.android.sourceCompatibility"]?.first()?.let { sourceCompatibility(it) }
                allCollapsed["target.android.targetCompatibility"]?.first()?.let { targetCompatibility(it) }
            }
        }
    }
}