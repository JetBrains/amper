/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.serialization

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.api.BuiltinCatalogTrace
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.DerivedValueTrace
import org.jetbrains.amper.frontend.api.PlatformSpecific
import org.jetbrains.amper.frontend.api.ProductTypeSpecific
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.SchemaValueDelegate
import org.jetbrains.amper.frontend.api.isDefault
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.schema.Settings
import kotlin.reflect.full.findAnnotation

/**
 * Serializes these [Settings] to a YAML string that looks like how it would appear in Amper files.
 */
fun Settings.serializeAsAmperYaml(
    productType: ProductType,
    contexts: Set<Platform>,
    printStaticDefaults: Boolean = true,
    printDerivedDefaults: Boolean = true,
    indent: String = "  ",
    theme: YamlTheme = YamlTheme.NoColor,
): String = serializeAsAmperYaml(
    indent = indent,
    filter = SettingsFilter(productType, contexts, printStaticDefaults, printDerivedDefaults),
    theme = theme,
)

private class SettingsFilter(
    private val productType: ProductType,
    private val contexts: Set<Platform>,
    private val printStaticDefaults: Boolean = true,
    private val printDerivedDefaults: Boolean = true,
) : SchemaValueFilter {

    override fun shouldInclude(valueDelegate: SchemaValueDelegate<*>): Boolean {
        val productTypeSpecific = valueDelegate.property.findAnnotation<ProductTypeSpecific>()
        if (productTypeSpecific != null && productType !in productTypeSpecific.productTypes.toSet()) {
            return false
        }
        val platformSpecific = valueDelegate.property.findAnnotation<PlatformSpecific>()
        if (platformSpecific != null && contexts.intersect(platformSpecific.platforms.toSet()).isEmpty()) {
            return false
        }
        val isUnset = valueDelegate.trace.isDefault
        if (isUnset) {
            return when (valueDelegate.trace) {
                is DerivedValueTrace -> printDerivedDefaults
                DefaultTrace -> printStaticDefaults
                is BuiltinCatalogTrace, // should never happen, because we should only see the reference in this case
                is PsiTrace -> true
            }
        }
        return true
    }
}
