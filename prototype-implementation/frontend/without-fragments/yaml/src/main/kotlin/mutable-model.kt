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
    val externalDependencies: MutableSet<Notation> = mutableSetOf(),
    val variants: MutableSet<String> = mutableSetOf(),
    var languageVersion: KotlinVersion = KotlinVersion.Kotlin18,
    var apiVersion: KotlinVersion = KotlinVersion.Kotlin18,
    var progressiveMode: Boolean = false,
    val languageFeatures: MutableList<String> = mutableListOf(),
    val optIns: MutableList<String> = mutableListOf(),
    var mainClass: String? = null,
    var entryPoint: String? = null,
    var srcFolderName: String? = null,
    var alias: String? = null,
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
                                optIns,
                                srcFolderName
                            )
                        )
                    )
            }
            state[mutableFragment] = fragment
            fragment
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MutableFragment

        if (name != other.name) return false
        if (platforms != other.platforms) return false
        if (externalDependencies != other.externalDependencies) return false
        if (variants != other.variants) return false
        if (languageVersion != other.languageVersion) return false
        if (apiVersion != other.apiVersion) return false
        if (progressiveMode != other.progressiveMode) return false
        if (languageFeatures != other.languageFeatures) return false
        return optIns == other.optIns
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + platforms.hashCode()
        result = 31 * result + externalDependencies.hashCode()
        result = 31 * result + variants.hashCode()
        result = 31 * result + languageVersion.hashCode()
        result = 31 * result + apiVersion.hashCode()
        result = 31 * result + progressiveMode.hashCode()
        result = 31 * result + languageFeatures.hashCode()
        result = 31 * result + optIns.hashCode()
        return result
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
                            copyFields(newFragment, element)
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
                            copyFields(newFragment, element)
                            newFragment
                        }
                        newFragment.variants.add(name)
                        add(newFragment)
                    }
                }
                put(name, newFragmentList)
            }
        }

        // set dependencies between potatoes
        for (option in options) {
            val dependencies = option.getValue<List<Settings>>("dependsOn") ?: listOf()
            val name = option.getValue<String>("name") ?: error("Name is required for option")
            for (dependency in dependencies) {
                val dependencyTarget = dependency.getValue<String>("target")
                val sourceFragments = fragmentMap[name] ?: error("Something went wrong")

                // correct previous old dependencies references after copying
                sourceFragments.forEach { fragment ->
                    val dependenciesToRemove = mutableSetOf<MutableFragmentDependency>()
                    val dependenciesToAdd = mutableSetOf<MutableFragmentDependency>()
                    fragment.dependencies.forEach { dependency ->
                        val targetFragment =
                            sourceFragments.firstOrNull { it.platforms == dependency.target.platforms && it !== fragment }
                                ?: error("Something went wrong")

                        dependenciesToRemove.add(dependency)
                        dependenciesToAdd.add(MutableFragmentDependency(targetFragment, dependency.dependencyKind))
                    }
                    fragment.dependencies.removeAll(dependenciesToRemove)
                    fragment.dependencies.addAll(dependenciesToAdd)
                }

                // add new dependencies related to copying
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

private fun copyFields(new: MutableFragment, old: MutableFragment) {
    new.variants.addAll(old.variants)
    new.dependencies.addAll(old.dependencies)
    new.alias = old.alias
}

context (Map<String, Set<Platform>>)
internal val Set<Set<Platform>>.basicFragments: List<MutableFragment>
    get() {
        val platforms = this
        return buildList {
            val sortedPlatformSubsets = platforms.sortedBy { it.size }
            val reducedPlatformSet = platforms.reduce { acc, set -> acc + set }
            sortedPlatformSubsets.forEach { platformSet ->
                val (name, alias) = platformSet.toCamelCaseString()
                val fragment = MutableFragment(name, platformSet, alias = alias)
                addFragment(fragment, platformSet)
            }

            if (reducedPlatformSet.size > 1) {
                val fragment = MutableFragment("common", reducedPlatformSet)
                addFragment(fragment, reducedPlatformSet)
            }
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
    config.handleFragmentSettings<List<String>>(this, "dependencies") { depList ->
        val resolved = depList.map { dep ->
            if (dep.startsWith(":")) object : PotatoModuleDependency {
                override val Model.module: PotatoModule
                    get() = modules.find { it.userReadableName == dep.removePrefix(":") }
                        ?: error("No module $dep found")
            }
            else MavenDependency(dep)
        }
        externalDependencies.addAll(resolved)
    }

    config.handleFragmentSettings<Double>(this, "languageVersion") {
        runCatching { MutableFragment.KotlinVersion.valueOf(it.toString()) }
            .getOrNull()?.let { languageVersion = it }
    }

    config.handleFragmentSettings<Double>(this, "apiVersion") {
        runCatching { MutableFragment.KotlinVersion.valueOf(it.toString()) }
            .getOrNull()?.let { apiVersion = it }
    }

    config.handleFragmentSettings<Boolean>(this, "progressiveMode") {
        progressiveMode = it
    }

    config.handleFragmentSettings<List<String>>(this, "languageFeatures") {
        languageFeatures.addAll(it)
    }
    config.handleFragmentSettings<List<String>>(this, "optIns") {
        optIns.addAll(it)
    }

    config.handleFragmentSettings<String>(this, "mainClass") {
        mainClass = it
    }
    config.handleFragmentSettings<String>(this, "entryPoint") {
        entryPoint = it
    }
}

context (Settings)
internal fun List<MutableFragment>.calculateSrcDir(platforms: Set<Platform>) {

    val defaultOptions = defaultOptionMap.values.toSet()
    val nonStdOptions = optionMap.filter { it.value.getValue<String>("dimension") != "mode" }.keys

    val testOption = "test"

    for (fragment in this) {
        val options = fragment.variants.filter { nonStdOptions.contains(it) }.toSet()
        val dir = buildString {
            if (fragment.variants.contains(testOption)) {
                append("test")
            } else {
                append("src")
            }

            val optionsWithoutDefault = options.filter { !defaultOptions.contains(it) }

            if (fragment.platforms != platforms || optionsWithoutDefault.isNotEmpty()) {
                append("@")
            }

            if (fragment.platforms != platforms) {
                if (fragment.alias != null) {
                    append("${fragment.alias}")
                } else {
                    append(fragment.platforms.map { with(mapOf<String, Set<Platform>>()) { setOf(it).toCamelCaseString().first } }
                        .joinToString("+"))
                }
            }

            if (optionsWithoutDefault.isNotEmpty()) {
                if (fragment.platforms != platforms) {
                    append("+")
                }
                append(optionsWithoutDefault.joinToString("+"))
            }
        }

        fragment.srcFolderName = dir
    }
}