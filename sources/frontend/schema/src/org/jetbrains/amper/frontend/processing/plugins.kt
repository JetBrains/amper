/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import com.intellij.psi.PsiDirectory
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.ResolvedReferenceTrace
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.TransformedValueTrace
import org.jetbrains.amper.frontend.api.asTrace
import org.jetbrains.amper.frontend.plugins.PluginDeclarationSchema
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.ModuleProduct
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.asMapLike
import org.jetbrains.amper.frontend.tree.mergeTreesNotNull
import org.jetbrains.amper.frontend.tree.syntheticBuilder

context(buildCtx: BuildCtx)
internal fun MapLikeValue<*>.configurePluginDefaults(moduleDir: PsiDirectory, product: ModuleProduct): MapLikeValue<*> =
    if (product.type == ProductType.JVM_AMPER_PLUGIN) {
        mergeTreesNotNull(
            asMapLike,
            buildCtx.pluginIdDefaultsTree(
                moduleName = moduleDir.name,
                trace = TransformedValueTrace(
                    description = "default plugin module structure",
                    sourceValue = product,
                ),
                idTrace = ResolvedReferenceTrace(
                    description = "default, from the module name",
                    referenceTrace = DefaultTrace,
                    resolvedValue = TraceableString(moduleDir.name, moduleDir.asTrace()),
                ),
            ),
        )
    } else {
        this
    }

private fun BuildCtx.pluginIdDefaultsTree(moduleName: String, trace: Trace, idTrace: Trace) =
    syntheticBuilder(types, trace) {
        `object`<Module> {
            Module::pluginInfo {
                PluginDeclarationSchema::id setTo scalar(TraceableString(moduleName, idTrace), idTrace)
            }
        }
    }
