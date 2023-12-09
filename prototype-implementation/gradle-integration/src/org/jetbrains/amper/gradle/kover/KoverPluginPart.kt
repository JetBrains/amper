/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.kover

import kotlinx.kover.gradle.plugin.dsl.KoverReportExtension
import org.jetbrains.amper.frontend.KoverHtmlPart
import org.jetbrains.amper.frontend.KoverPart
import org.jetbrains.amper.frontend.KoverXmlPart
import org.jetbrains.amper.gradle.adjustXmlFactories
import org.jetbrains.amper.gradle.base.BindingPluginPart
import org.jetbrains.amper.gradle.base.PluginPartCtx
import java.io.File

class KoverPluginPart(ctx: PluginPartCtx): BindingPluginPart by ctx {
    private val koverRE get() = project.extensions.findByName("koverReport") as KoverReportExtension

    override val needToApply: Boolean by lazy {
        module.leafFragments.any { it.parts.find<KoverPart>()?.enabled == true }
    }

    override fun applyBeforeEvaluate() {
        project.plugins.apply("org.jetbrains.kotlinx.kover")

        applySettings()
    }

    fun applySettings() {
        val koverPart = module.leafFragments.map { it.parts.find<KoverPart>() }.firstOrNull()
        val htmlPart = koverPart?.html
        val xmlPart = koverPart?.xml

        koverRE.defaults {
            if(htmlPart != null) {
                it.html { html ->
                    html.title = htmlPart.title
                    html.charset = htmlPart.charset
                    if(htmlPart.onCheck != null) {
                        html.onCheck = htmlPart.onCheck ?: false
                    }

                    if(htmlPart.reportDir != null) {
                        html.setReportDir(File(htmlPart.reportDir!!))
                    }
                }
            }

            if(xmlPart != null) {
                it.xml { xml ->
                    xml.onCheck = xmlPart.onCheck ?: false
                    if(xmlPart.reportFile != null) {
                        xml.setReportFile(File(xmlPart.reportFile!!))
                    }
                }

                adjustXmlFactories()
            }
        }
    }
}
