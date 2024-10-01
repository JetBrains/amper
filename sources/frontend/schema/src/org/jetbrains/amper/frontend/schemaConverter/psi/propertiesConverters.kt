/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.Traceable

class TraceableMap<K, V> : HashMap<K, V>(), Traceable {
    private val traces = mutableListOf<Trace>()

    override var trace: Trace?
        get() = traces.filterIsInstance<PsiTrace>().distinctBy { it.psiElement }.singleOrNull()
        set(value) { if (value is PsiTrace) { traces.clear(); traces.add(value) } }

    override fun put(key: K, value: V): V? {
        if (value is Traceable) value.trace?.let { traces.add(it) }
        return super.put(key, value)
    }
}