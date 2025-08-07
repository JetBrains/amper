/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtProperty

internal fun KtClassOrObject.isInterface() =
    getDeclarationKeyword()?.text == KtTokens.INTERFACE_KEYWORD.value

internal fun KtProperty.overrideModifier(): PsiElement? =
    modifierList?.getModifier(KtTokens.OVERRIDE_KEYWORD)

internal fun KtCallableDeclaration.extensionReceiver(): PsiElement? =
    receiverTypeReference

internal fun KtDeclaration.getDefaultDocString() = docComment?.getDefaultSection()?.getContent()
