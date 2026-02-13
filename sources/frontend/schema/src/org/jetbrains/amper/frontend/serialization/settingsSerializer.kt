/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.serialization

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.api.BuiltinCatalogTrace
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.DerivedValueTrace
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.isDefault
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.tree.CompletePropertyKeyValue

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

    override fun shouldInclude(keyValue: CompletePropertyKeyValue): Boolean {
        val productTypes = keyValue.propertyDeclaration.specificToProducts
        if (productTypes.isNotEmpty() && productType !in productTypes) {
            return false
        }
        val specificToPlatforms = keyValue.propertyDeclaration.specificToPlatforms
        if (specificToPlatforms.isNotEmpty() && contexts.intersect(specificToPlatforms).isEmpty()) {
            return false
        }
        val valueTrace = keyValue.value.trace
        val isUnset = valueTrace.isDefault
        if (isUnset) {
            return when (valueTrace) {
                is DerivedValueTrace -> printDerivedDefaults
                DefaultTrace -> printStaticDefaults
                is BuiltinCatalogTrace, // should never happen, because we should only see the reference in this case
                is PsiTrace -> true
            }
        }
        return true
    }
}
