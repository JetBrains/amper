/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics.helpers

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.ModuleProduct
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.tree.ListValue
import org.jetbrains.amper.frontend.tree.Merged
import org.jetbrains.amper.frontend.tree.MergedTree
import org.jetbrains.amper.frontend.tree.asList
import org.jetbrains.amper.frontend.tree.get
import org.jetbrains.amper.frontend.tree.scalarValue


data class VisitedProduct(
    val typeValue: MergedTree,
    val type: ProductType,
    val platformsValue: ListValue<Merged>?,
    val platformsRaw: List<MergedTree>?,
    val platforms: List<TraceableEnum<Platform>>?,
)

/**
 * Do visit product in the raw [org.jetbrains.amper.frontend.tree.TreeValue].
 */
fun MergedTree.visitProduct(block: (VisitedProduct) -> Unit) =
    visitMapLikeProperties(Module::product) out@{ _, pValue ->
        val typeValue = pValue[ModuleProduct::type].singleOrNull()?.value
        val platformsValue = pValue[ModuleProduct::platforms].singleOrNull()?.value?.asList
        VisitedProduct(
            typeValue ?: return@out,
            typeValue.scalarValue<ProductType>() ?: return@out,
            platformsValue,
            platformsValue?.children,
            platformsValue?.children?.mapNotNull { it.scalarValue<TraceableEnum<Platform>>() },
        ).let(block)
    }