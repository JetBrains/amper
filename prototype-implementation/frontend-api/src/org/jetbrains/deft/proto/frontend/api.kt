package org.jetbrains.deft.proto.frontend


interface Model {
    val modules: List<PotatoModule>
}

class ModelImpl(override val modules: List<PotatoModule>) : Model {
    constructor(vararg modules: PotatoModule) : this(modules.toList())
}
