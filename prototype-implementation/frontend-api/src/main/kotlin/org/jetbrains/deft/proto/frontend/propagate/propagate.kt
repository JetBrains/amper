package org.jetbrains.deft.proto.frontend.propagate

import org.jetbrains.deft.proto.frontend.*

typealias Parts = Set<ByClassWrapper<FragmentPart<*>>>

val Model.propagatedFragments: Model
    get() = ResolvedModel(modules.map {
        ResolvedPotatoModule(it.userReadableName, it.type, it.source, it.fragments.map {
            ResolvedFragment(
                it.name,
                it.fragmentDependencies,
                it.fragmentDependants,
                it.externalDependencies,
                it.resolvedParts,
                it.src,
                it.platforms
            )
        }, it.artifacts.map {
            it
        })
    })


val Fragment.resolvedParts: Parts
    get() = parts.map { ByClassWrapper(it.value.propagate()) }.toSet()