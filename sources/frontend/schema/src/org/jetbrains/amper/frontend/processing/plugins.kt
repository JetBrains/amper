/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import com.intellij.psi.PsiDirectory
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
import org.jetbrains.amper.frontend.tree.MappingNode
import org.jetbrains.amper.frontend.tree.mergeTrees
import org.jetbrains.amper.frontend.tree.syntheticBuilder
import org.jetbrains.amper.frontend.types.SchemaTypingContext

context(_: SchemaTypingContext)
internal fun MappingNode.configurePluginDefaults(moduleDir: PsiDirectory, product: ModuleProduct): MappingNode =
    if (product.type == ProductType.JVM_AMPER_PLUGIN) {
        mergeTrees(
            this,
            pluginIdDefaultsTree(
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

context(_: SchemaTypingContext)
private fun pluginIdDefaultsTree(moduleName: String, trace: Trace, idTrace: Trace) =
    syntheticBuilder(trace) {
        `object`<Module> {
            Module::pluginInfo {
                PluginDeclarationSchema::id setTo traceableScalar(moduleName, idTrace)
            }
        }
    }
