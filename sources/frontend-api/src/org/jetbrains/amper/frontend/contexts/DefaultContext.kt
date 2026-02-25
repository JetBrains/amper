/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.contexts

import org.jetbrains.amper.frontend.api.Trace

/**
 * Context for the values that are set internally by Amper logic.
 */
sealed class DefaultContext(
    val priority: Int,
) : Context {
    override val trace: Trace? = null
    override fun withoutTrace() = this

    /**
     * Default which is set at the type-level. Has the lowest priority.
     *
     * NOTE: Such values are not normally participating in context-aware
     * [refining][org.jetbrains.amper.frontend.tree.TreeRefiner],
     * because the refiner naturally requests them *only* when there is no other value for the property.
     * However, theoretically, if the refined tree is merged again with some other ones and refined again,
     * then the priority *may be used* to correctly resolve the "more explicit" value over the type-level default one.
     */
    object TypeLevel : DefaultContext(priority = 0) {
        override fun toString() = "DefaultContext.TypeLevel"
    }

    /**
     * Default which is set in *reaction* to some feature/sub-system/... being enabled.
     * Has higher priority than [TypeLevel] default.
     */
    object ReactivelySet : DefaultContext(priority = 1) {
        override fun toString() = "DefaultContext.ReactivelySet"
    }
}