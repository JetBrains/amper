package org.jetbrains.deft.proto.frontend

import java.util.*

internal data class MutableFragmentDependency(val target: MutableFragment, val dependencyKind: DependencyKind) {
    enum class DependencyKind {
        Friend,
        Refines
    }
}

internal data class MutableFragment(
    val name: String,
    val platforms: Set<Platform>,
    val dependencies: MutableSet<MutableFragmentDependency> = mutableSetOf(),
    val externalDependencies: MutableSet<String> = mutableSetOf(),
    val variants: MutableSet<String> = mutableSetOf(),
    var languageVersion: KotlinVersion = KotlinVersion.Kotlin19,
    var apiVersion: KotlinVersion = KotlinVersion.Kotlin19,
    var progressiveMode: Boolean = false,
    val languageFeatures: MutableList<String> = mutableListOf(),
    val optIns: MutableList<String> = mutableListOf()
) {
    enum class KotlinVersion(private val version: String) {
        Kotlin19("1.9"),
        Kotlin18("1.8"),
        Kotlin17("1.7"),
        Kotlin16("1.6"),
        Kotlin15("1.5"),
        Kotlin14("1.4"),
        Kotlin13("1.3"),
        Kotlin12("1.2"),
        Kotlin11("1.1"),
        Kotlin10("1.0");

        override fun toString(): String = version
    }

    context (Stateful<MutableFragment, Fragment>)
    fun immutable(): Fragment {
        return state[this] ?: run {
            val mutableFragment = this
            val fragment = object : Fragment {
                override val name: String
                    get() = mutableFragment.name
                override val fragmentDependencies: List<FragmentDependency>
                    get() = dependencies.map {
                        object : FragmentDependency {
                            override val target: Fragment
                                get() = it.target.immutable()
                            override val type: FragmentDependencyType
                                get() = when (it.dependencyKind) {
                                    MutableFragmentDependency.DependencyKind.Friend -> FragmentDependencyType.FRIEND
                                    MutableFragmentDependency.DependencyKind.Refines -> FragmentDependencyType.REFINE
                                }
                        }
                    }
                override val externalDependencies: List<Notation>
                    get() = mutableFragment.externalDependencies.toList()
                override val parts: ClassBasedSet<FragmentPart<*>>
                    get() = setOf(
                        ByClassWrapper(
                            KotlinFragmentPart(
                                languageVersion.toString(),
                                apiVersion.toString(),
                                progressiveMode,
                                languageFeatures,
                                optIns
                            )
                        )
                    )
            }
            state[mutableFragment] = fragment
            fragment
        }
    }
}

internal fun List<MutableFragment>.multiplyFragments(variants: List<Settings>): List<MutableFragment> {
    val fragments = this.toMutableList()
    // multiply
    for (variant in variants) {
        val options = variant.getValue<List<Settings>>("options") ?: listOf()
        if (options.isEmpty()) {
            error("Options are required")
        }

        val single = options.count { it.getValue<Boolean>("default") ?: false } == 1
        if (!single) {
            error("Default value is required and must be single")
        }

        // copy fragments
        val fragmentMap = buildMap {
            for (option in options) {
                val name = option.getValue<String>("name") ?: error("Name is required for option")
                val newFragmentList = buildList {
                    for (element in fragments) {
                        val newFragment = if (option.getValue<Boolean>("default") == true) {
                            val newFragment = MutableFragment(element.name, element.platforms)
                            newFragment.variants.addAll(element.variants)
                            newFragment.dependencies.addAll(element.dependencies)
                            newFragment
                        } else {
                            val newFragment = MutableFragment(
                                "${element.name}${
                                    name.replaceFirstChar {
                                        if (it.isLowerCase()) {
                                            it.titlecase(Locale.getDefault())
                                        } else {
                                            it.toString()
                                        }
                                    }
                                }",
                                element.platforms
                            )
                            newFragment.variants.addAll(element.variants)
                            newFragment.dependencies.addAll(element.dependencies)
                            newFragment
                        }
                        newFragment.variants.add(name)
                        add(newFragment)
                    }
                }
                put(name, newFragmentList)
            }
        }

        // set dependencies
        for (option in options) {
            val dependencies = option.getValue<List<Settings>>("dependsOn") ?: listOf()
            val name = option.getValue<String>("name") ?: error("Name is required for option")
            for (dependency in dependencies) {
                val dependencyTarget = dependency.getValue<String>("target")
                val sourceFragments = fragmentMap[name] ?: error("Something went wrong")
                fragmentMap[dependencyTarget]?.let { targetFragments ->
                    for (i in sourceFragments.indices) {
                        val kind = when (dependency.getValue<String>("kind")) {
                            "friend" -> MutableFragmentDependency.DependencyKind.Friend
                            else -> MutableFragmentDependency.DependencyKind.Refines
                        }
                        sourceFragments[i].dependencies.add(MutableFragmentDependency(targetFragments[i], kind))
                    }
                }
            }
        }


        fragments.clear()
        for (fragmentSkeletonNodes in fragmentMap.values) {
            fragments.addAll(fragmentSkeletonNodes)
        }
    }
    return fragments
}

context (Map<String, Set<Platform>>)
internal val Set<Set<Platform>>.basicFragments: List<MutableFragment>
    get() {
        val platforms = this
        return buildList {
            val sortedPlatformSubsets = platforms.sortedBy { it.size }
            sortedPlatformSubsets.forEach { platformSet ->
                val fragment = MutableFragment(platformSet.toCamelCaseString(), platformSet)
                addFragment(fragment, platformSet)
            }
            val platformSet = platforms.reduce { acc, set -> acc + set }
            val fragment = MutableFragment("common", platformSet)
            addFragment(fragment, platformSet)
        }
    }

private fun MutableList<MutableFragment>.addFragment(fragment: MutableFragment, platforms: Set<Platform>) {
    forEach {
        if (platforms.containsAll(it.platforms)) {
            val alreadyExistsTransitively = it.dependencies.any {
                fragment.platforms.containsAll(it.target.platforms)
            }
            if (!alreadyExistsTransitively) {
                it.dependencies.add(
                    MutableFragmentDependency(
                        fragment,
                        MutableFragmentDependency.DependencyKind.Refines
                    )
                )
            }
        }
    }
    add(fragment)
}

context (Map<String, Set<Platform>>)
internal fun List<MutableFragment>.handleAdditionalKeys(config: Settings) {
    config.handleFragmentSettings<List<String>>(this, "dependencies") {
        externalDependencies.addAll(it)
    }

    config.handleFragmentSettings<Double>(this, "languageVersion") {
        runCatching { MutableFragment.KotlinVersion.valueOf(it.toString()) }
            .getOrNull()?.let { languageVersion = it }
    }

    config.handleFragmentSettings<Double>(this, "apiVersion") {
        runCatching { MutableFragment.KotlinVersion.valueOf(it.toString()) }
            .getOrNull()?.let { apiVersion = it }
    }

    config.handleFragmentSettings<Boolean>(this, "apiVersion") {
        progressiveMode = it
    }

    config.handleFragmentSettings<List<String>>(this, "languageFeatures") {
        languageFeatures.addAll(it)
    }
    config.handleFragmentSettings<List<String>>(this, "optIns") {
        optIns.addAll(it)
    }
}
