/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.api.Default
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.plugins.schema.model.InputOutputMark

interface SchemaObjectDeclaration : SchemaTypeDeclaration {
    val properties: List<Property>

    fun getProperty(name: String): Property?

    /**
     * Returns the boolean shorthand property, if any.
     *
     * @see org.jetbrains.amper.frontend.api.Shorthand
     */
    fun getBooleanShorthand(): Property?

    /**
     * Returns the secondary shorthand property, if any.
     *
     * Can be of Enum, String, Path, List types.
     * @see org.jetbrains.amper.frontend.api.Shorthand
     */
    fun getSecondaryShorthand(): Property?

    /**
     * @see org.jetbrains.amper.frontend.api.FromKeyAndTheRestIsNested
     */
    fun getFromKeyAndTheRestNestedProperty(): Property?

    fun createInstance(): SchemaNode

    override fun toType(): SchemaType.ObjectType = SchemaType.ObjectType(this)

    data class Property(
        val name: String,
        val type: SchemaType,
        val documentation: String? = null,
        /**
         * Names that users might try when looking for this property.
         * These are not valid names for this property, and using them in config files is an error.
         * They can be used to help with completion in the IDE, or for more meaningful error messages.
         */
        val misnomers: Set<String> = emptySet(),
        /**
         * The default value for this property, if any.
         *
         * If this property is optional and defaults to null, a proper [Default] instance will be present, with the null
         * value inside. If the [default] is null itself, it means there is no default value (not even null), and thus
         * the property must be specfied explicitly (required).
         */
        val default: Default<*>?,
        val isModifierAware: Boolean = false,
        /**
         * @see org.jetbrains.amper.frontend.api.FromKeyAndTheRestIsNested
         */
        val isFromKeyAndTheRestNested: Boolean = false,
        val specificToPlatforms: Set<Platform> = emptySet(),
        val specificToProducts: Set<ProductType> = emptySet(),
        val isPlatformAgnostic: Boolean = false,
        val specificToGradleMessage: String? = null,
        val hasShorthand: Boolean = false,
        val isHiddenFromCompletion: Boolean = false,
        val inputOutputMark: InputOutputMark? = null,
        val canBeReferenced: Boolean = false,
        val isUserSettable: Boolean = true,
        val origin: SchemaOrigin,
    )
}