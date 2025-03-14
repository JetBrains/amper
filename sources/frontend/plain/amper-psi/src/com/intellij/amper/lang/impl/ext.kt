package com.intellij.amper.lang.impl

import com.intellij.amper.lang.AmperContextName
import com.intellij.openapi.util.NlsSafe

val AmperContextName.name get(): @NlsSafe String? = identifier?.text