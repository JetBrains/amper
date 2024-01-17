/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.kover

import kotlinx.kover.gradle.plugin.dsl.KoverReportExtension
import org.jetbrains.amper.gradle.adjustXmlFactories
import org.jetbrains.amper.gradle.base.BindingPluginPart
import org.jetbrains.amper.gradle.base.PluginPartCtx

class KoverPluginPart(ctx: PluginPartCtx): BindingPluginPart by ctx {
    private val koverRE get() = project.extensions.findByName("koverReport") as KoverReportExtension

    override val needToApply: Boolean by lazy {
        module.leafFragments.any { it.settings.kover?.enabled == true }
    }

    override fun applyBeforeEvaluate() {
        project.plugins.apply("org.jetbrains.kotlinx.kover")
        applySettings()
    }

    fun applySettings() {
        val koverSettings = module.leafFragments.map { it.settings.kover }.firstOrNull()
        val htmlPart = koverSettings?.html
        val xmlPart = koverSettings?.xml

        koverRE.defaults {
            if(htmlPart != null) {
                it.html { html ->
                    html.title = htmlPart.title
                    html.charset = htmlPart.charset
                    if(htmlPart.onCheck != null) {
                        html.onCheck = htmlPart.onCheck ?: false
                    }

                    htmlPart.reportDir?.toFile()?.let { html.setReportDir(it) }
                }
            }

            if(xmlPart != null) {
                it.xml { xml ->
                    xml.onCheck = xmlPart.onCheck ?: false
                    xmlPart.reportFile?.toFile()?.let { xml.setReportFile(it) }
                }

                adjustXmlFactories()
            }
        }
    }
}
