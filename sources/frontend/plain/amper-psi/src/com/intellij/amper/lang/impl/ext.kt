package com.intellij.amper.lang.impl

import com.intellij.amper.lang.AmperContextBlock
import com.intellij.amper.lang.AmperContextName
import com.intellij.amper.lang.AmperContextualElement
import com.intellij.amper.lang.AmperContextualStatement
import com.intellij.openapi.util.NlsSafe

val AmperContextName.name get(): @NlsSafe String? = identifier?.text

val AmperContextualElement.contextNames get() = ((this as? AmperContextBlock)?.contextNameList?.mapNotNull { it.name }
                                                ?: (this as? AmperContextualStatement)?.contextNameList?.mapNotNull { it.name }).orEmpty()