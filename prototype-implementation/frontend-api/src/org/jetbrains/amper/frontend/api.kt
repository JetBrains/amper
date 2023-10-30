/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend


interface Model {
    val modules: List<PotatoModule>
}

class ModelImpl(override val modules: List<PotatoModule>) : Model {
    constructor(vararg modules: PotatoModule) : this(modules.toList())
}
