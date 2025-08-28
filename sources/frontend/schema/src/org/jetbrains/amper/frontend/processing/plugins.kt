/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.plugins.PluginDeclarationSchema
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.ModuleProduct
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.tree.Merged
import org.jetbrains.amper.frontend.tree.asMapLike
import org.jetbrains.amper.frontend.tree.syntheticBuilder

context(buildCtx: BuildCtx)
internal fun Merged.configurePluginDefaults(moduleName: String, product: ModuleProduct): Merged =
    if (product.type == ProductType.JVM_AMPER_PLUGIN) {
        buildCtx.treeMerger.mergeTrees(
            listOfNotNull(
                asMapLike,
                buildCtx.pluginIdDefaultsTree(
                    moduleName = moduleName,
                    // TODO: Make a trace that points to a directory?
                    trace = DefaultTrace(computedValueTrace = product),
                ),
            )
        )
    } else {
        this
    }

private fun BuildCtx.pluginIdDefaultsTree(moduleName: String, trace: Trace) =
    syntheticBuilder(types, trace) {
        `object`<Module> {
            Module::plugin {
                PluginDeclarationSchema::id setTo scalar(TraceableString(moduleName, trace))
            }
        }
    }
