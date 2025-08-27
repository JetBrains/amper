/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtContextReceiverList
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtTreeVisitor
import org.jetbrains.kotlin.psi.KtTypeParameterList

context(_: KaSession, _: DiagnosticsReporter, _: SymbolsCollector)
internal fun parseSchemaDeclaration(
    schemaDeclaration: KtClassOrObject,
    primarySchemaFqnString: PluginData.SchemaName?,
): PluginData.ClassData? {
    if (!schemaDeclaration.isInterface()) {
        reportError(schemaDeclaration.getDeclarationKeyword() ?: schemaDeclaration, "schema.not.interface")
        return null  // fatal - no need to parse further
    }
    val name = schemaDeclaration.fqName?.asString() ?: return null // invalid Kotlin
    val isPrimarySchema = name == primarySchemaFqnString?.qualifiedName
    val properties = buildList {
        val visitor = object : KtTreeVisitor<Nothing?>() {
            override fun visitClassOrObject(classOrObject: KtClassOrObject, data: Nothing?): Void? {
                return null  // Stop here to not go into unrelated nested classes
            }

            override fun visitNamedFunction(function: KtNamedFunction, data: Nothing?) = null.also {
                reportError(function, "schema.forbidden.function")
            }

            override fun visitSuperTypeListEntry(specifier: KtSuperTypeListEntry, data: Nothing?): Void? {
                reportError(specifier, "schema.forbidden.mixins")
                return super.visitSuperTypeListEntry(specifier, data)
            }

            override fun visitTypeParameterList(list: KtTypeParameterList, data: Nothing?) = null.also {
                reportError(list, "schema.forbidden.generics")
            }

            override fun visitContextReceiverList(contextReceiverList: KtContextReceiverList, data: Nothing?) =
                null.also {
                    reportError(contextReceiverList, "schema.forbidden.context.receivers")
                }

            override fun visitProperty(property: KtProperty, data: Nothing?): Void? {
                if (isPrimarySchema && property.name == "enabled") {
                    reportError(property.nameIdentifier ?: property, "schema.forbidden.property.enabled")
                } else {
                    parseProperty(property)?.let(::add)
                }
                return super.visitProperty(property, data)
            }
        }
        schemaDeclaration.acceptChildren(visitor)
    }
    return PluginData.ClassData(
        name = PluginData.SchemaName(name),
        properties = properties,
        doc = schemaDeclaration.getDefaultDocString(),
    )
}