/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.intrumentation

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias

internal fun KSType.declarationTypeAliasAware(): KSClassDeclaration {
    return when (val declaration = declaration) {
        is KSClassDeclaration -> declaration
        is KSTypeAlias -> declaration.type.resolve().declarationTypeAliasAware()
        else -> error("Unexpected declaration: $declaration")
    }
}

internal fun parseErrorType(type: KSType): String {
    return KspErrorTypeRegex.matchEntire(type.toString())?.groupValues[1] ?: type.toString()
}

private val KspErrorTypeRegex = "<ERROR TYPE: (.*)>".toRegex()
