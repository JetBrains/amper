package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.util.getPlatformFromFragmentName
import org.yaml.snakeyaml.Yaml
import java.nio.file.Path
import java.util.*
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

const val DIAMONDS_ALLOWED = true

typealias Settings = Map<String, Any>

inline fun <reified T : Any> Settings.getValue(key: String): T? = this[key] as? T

fun Settings.getSettings(key: String): Settings? = getValue<Settings>(key)
inline fun <reified T : Any> Settings.getByPath(vararg path: String): T? {
    var settings = this
    path.forEachIndexed { index, element ->
        val isLast = index == path.size - 1
        if (isLast) {
            return settings.getValue(element)
        }
        settings = settings.getSettings(element) ?: error("There is no such key '$element'")
    }

    return null
}


interface BuildFileAware {
    val buildFile: Path
}

interface Stateful<K, V> {
    val state: MutableMap<K, V>
        get() = mutableMapOf()
}

data class MutableFragmentDependency(val target: MutableFragment, val dependencyKind: DependencyKind) {
    enum class DependencyKind {
        Friend,
        Refines
    }
}

data class MutableFragment(
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

    enum class KotlinVersion(val version: String) {
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
}

context(BuildFileAware)
fun parseModule(value: String): PotatoModule {
    val yaml = Yaml()
    val config = yaml.load<Settings>(value)
    val rawPlatforms = config.getByPath<List<String>>("product", "platforms") ?: listOf()
    val platforms = rawPlatforms.mapNotNull { getPlatformFromFragmentName(it) }
    if (platforms.isEmpty()) {
        error("You need to add up at least one platform")
    }

    val dependencySubsets = config.keys
        .asSequence()
        .map { it.split(".") }
        .filter { it.size > 1 }
        .map { it[1] }
        .map { it.split("+").toSet() }
        .toSet()

    val folderSubsets = buildFile.parent.resolve("src").listDirectoryEntries()
        .map { it.name }
        .map { it.split("+").toSet() }
        .toSet()

    val subsets = (dependencySubsets + folderSubsets)
        .map { it.mapNotNull { getPlatformFromFragmentName(it) }.toSet() }
        .toSet() + platforms.map { setOf(it) }

    // validate
    if (!DIAMONDS_ALLOWED) {
        for (subset1 in subsets) {
            for (subset2 in subsets) {
                if (subset1 != subset2) {
                    if ((subset1 intersect subset2).isNotEmpty()) {
                        if (!(subset1.containsAll(subset2) && !subset2.containsAll(subset1))) {
                            error("Wrong using + notation")
                        }
                    }
                }
            }
        }
    }

    // build tree
    val fragments = mutableListOf<MutableFragment>()
    val sortedSubsets = subsets.sortedBy { it.size }
    sortedSubsets.forEach { subset ->
        val fragment =
            MutableFragment(subset.toString(), subset)
        fragments.forEach {
            if (subset.containsAll(it.platforms)) {
                val alreadyExistsTransitively =
                    it.dependencies.any { fragment.platforms.containsAll(it.target.platforms) }
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
        fragments.add(fragment)
    }
    val platformSet = platforms.toSet()
    val fragment = MutableFragment("common", platformSet)
    fragments.forEach {
        if (platformSet.containsAll(it.platforms)) {
            val alreadyExistsTransitively =
                it.dependencies.any { fragment.platforms.containsAll(it.target.platforms) }
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
    fragments.add(fragment)


    // multiply
    val variants = config.getValue<List<Settings>>("variants") ?: listOf()
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
                val newFragmentList = buildList<MutableFragment> {
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

    config.handleFragmentSettings<List<String>>(fragments, "dependencies") {
        externalDependencies.addAll(it)
    }

    config.handleFragmentSettings<Double>(fragments, "languageVersion") {
        runCatching { MutableFragment.KotlinVersion.valueOf(it.toString()) }
            .getOrNull()?.let { languageVersion = it }
    }

    config.handleFragmentSettings<Double>(fragments, "apiVersion") {
        runCatching { MutableFragment.KotlinVersion.valueOf(it.toString()) }
            .getOrNull()?.let { apiVersion = it }
    }

    config.handleFragmentSettings<Boolean>(fragments, "apiVersion") {
        progressiveMode = it
    }

    config.handleFragmentSettings<List<String>>(fragments, "languageFeatures") {
        languageFeatures.addAll(it)
    }
    config.handleFragmentSettings<List<String>>(fragments, "optIns") {
        optIns.addAll(it)
    }

    val mutableState = object : Stateful<MutableFragment, Fragment> {}
    val immutableFragments = fragments.map { with(mutableState) { it.immutable() } }
    return object : PotatoModule {
        override val userReadableName: String
            get() = buildFile.parent.name
        override val type: PotatoModuleType
            get() = when (config.getByPath<String>("product", "type") ?: error("Product type is required")) {
                "app" -> PotatoModuleType.APPLICATION
                "lib" -> PotatoModuleType.LIBRARY
                else -> error("Unsupported product type")
            }
        override val source: PotatoModuleSource
            get() = PotatoModuleFileSource(buildFile)
        override val fragments: List<Fragment>
            get() = immutableFragments
        override val artifacts: List<Artifact>
            get() = when (config.getByPath<String>("product", "type") ?: error("Product type is required")) {
                "app" -> {
                    val options = buildList {
                        for (variant in variants) {
                            val options = variant.getValue<List<Settings>>("options") ?: listOf()
                            add(buildList {
                                for (option in options) {
                                    add(
                                        option.getValue<String>("name")
                                            ?: error("Name is required for variant option")
                                    )
                                }
                            })
                        }
                    }

                    val cartesian = cartesian(*options.toTypedArray())

                    buildList {
                        for (platform in platforms) {
                            for (element in cartesian) {
                                add(object : Artifact {
                                    override val fragments: List<Fragment>
                                        get() = with(mutableState) {
                                            listOf(fragments.filter { it.platforms == setOf(platform) }
                                                .firstOrNull { it.variants == element.toSet() }
                                                ?: error("Something went wrong")).map { it.immutable() }
                                        }
                                    override val platforms: Set<Platform>
                                        get() = setOf(platform)
                                    override val parts: ClassBasedSet<ArtifactPart<*>>
                                        get() = setOf()
                                })
                            }
                        }
                    }
                }

                "lib" -> {
                    listOf(object : Artifact {
                        override val fragments: List<Fragment>
                            get() = with(mutableState) { fragments.map { it.immutable() } }
                        override val platforms: Set<Platform>
                            get() = platformSet
                        override val parts: ClassBasedSet<ArtifactPart<*>>
                            get() = setOf()
                    })
                }

                else -> error("Unsupported product type")
            }
    }
}

inline fun <reified T> Settings.handleFragmentSettings(
    fragments: List<MutableFragment>,
    key: String,
    init: MutableFragment.(T) -> Unit
) {
    val rawPlatforms = getByPath<List<String>>("product", "platforms") ?: listOf()
    val platforms = rawPlatforms.mapNotNull { getPlatformFromFragmentName(it) }
    val variants = getValue<List<Settings>>("variants") ?: listOf()
    // add external dependencies, compiler flags, etc
    val optionMap = buildMap {
        for (variant in variants) {
            for (option in (variant.getValue<List<Settings>>("options")
                ?: listOf()).mapNotNull { it.getValue<String>("name") }) {
                put("option", variant)
            }
        }
    }

    val variantSet = variants.toMutableSet()

    val defaultOptionMap = buildMap {
        for (variant in variants) {
            val option = (variant.getValue<List<Settings>>("options")
                ?: listOf()).firstOrNull { it.getValue<Boolean>("default") ?: false }
                ?: error("Something went wrong")
            put(variant, option.getValue<String>("name") ?: error("Something went wrong"))
        }
    }

    for ((settingsKey, settingsValue) in filterKeys { it.startsWith(key) }) {
        val split = settingsKey.split(".")
        val specialization = if (split.size > 1) split[1].split("+") else listOf()
        val options = specialization.filter { getPlatformFromFragmentName(it) == null }.toSet()
        for (option in options) {
            val variant = optionMap[option] ?: error("There is no such variant option $option")
            variantSet.remove(variant)
        }

        val normalizedPlatforms =
            specialization.mapNotNull { getPlatformFromFragmentName(it) }.ifEmpty { platforms }.toSet()
        val normalizedOptions = options + variantSet.mapNotNull { defaultOptionMap[it] }

        val targetFragment = fragments
            .filter { it.platforms == normalizedPlatforms }
            .firstOrNull { it.variants == normalizedOptions }
            ?: error("Can't find a variant with platforms $normalizedPlatforms and variant options $normalizedOptions")

        targetFragment.init(settingsValue as T)
    }
}

context (Stateful<MutableFragment, Fragment>)
fun MutableFragment.immutable(): Fragment {
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

fun <T> cartesian(vararg lists: List<T>): List<List<T>> {
    var res = listOf<List<T>>()
    for (list in lists) {
        res = cartesian(res, list.map { listOf(it) })
    }

    return res
}

fun <T> cartesian(list1: List<List<T>>, list2: List<List<T>>): List<List<T>> = buildList {
    if (list1.isEmpty()) {
        return list2
    }
    for (t1 in list1) {
        for (t2 in list2) {
            add(t1 + t2)
        }
    }
}




