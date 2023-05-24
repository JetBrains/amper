package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.model.PlainFragment
import java.nio.file.Path
import java.util.*

internal data class MutableFragmentDependency(val target: FragmentBuilder, val dependencyKind: DependencyKind) {
    enum class DependencyKind {
        Friend,
        Refines
    }
}

internal data class FragmentBuilder(
    val name: String,
    val platforms: Set<Platform>,
    val dependencies: MutableSet<MutableFragmentDependency> = mutableSetOf(),
    val dependants: MutableSet<MutableFragmentDependency> = mutableSetOf(),
    val externalDependencies: MutableSet<Notation> = mutableSetOf(),
    /**
     * These are all variants, that this fragment should be included in.
     * Thus, "common" fragment will contain all variants.
     */
    val variants: MutableSet<String> = mutableSetOf(),
    var alias: String? = null,
    var src: Path? = null,

    // parts
    var kotlin: KotlinFragmentBuilder? = null,
    var java: JavaFragmentBuilder? = null,
    var native: NativeFragmentBuilder? = null,
    var android: AndroidFragmentBuilder? = null,
    var publish: PublishingFragmentBuilder? = null,
    var junit: JunitFragmentBuilder? = null,
) {


    fun addDependency(mutableFragmentDependency: MutableFragmentDependency) {
        dependencies.add(mutableFragmentDependency)
        mutableFragmentDependency.target.dependants.add(
            MutableFragmentDependency(
                this,
                mutableFragmentDependency.dependencyKind
            )
        )
    }

    fun removeDependency(mutableFragmentDependency: MutableFragmentDependency) {
        dependencies.remove(mutableFragmentDependency)
        mutableFragmentDependency.target.dependants.removeIf { it.target == this }
    }

    fun removeDependencies(mutableFragmentDependencies: Set<MutableFragmentDependency>) {
        mutableFragmentDependencies.forEach { removeDependency(it) }
    }

    fun addDependencies(mutableFragmentDependencies: Set<MutableFragmentDependency>) {
        mutableFragmentDependencies.forEach { addDependency(it) }
    }

    data class KotlinFragmentBuilder(
        var languageVersion: KotlinVersion? = null,
        var apiVersion: KotlinVersion? = null,
        var sdkVersion: String? = null,
        var progressiveMode: Boolean? = null,
        val languageFeatures: MutableList<String> = mutableListOf(),
        val optIns: MutableList<String> = mutableListOf(),

        ) {
        companion object {
            operator fun invoke(block: KotlinFragmentBuilder.() -> Unit): KotlinFragmentBuilder {
                val builder = KotlinFragmentBuilder()
                builder.block()
                return builder
            }
        }
    }

    data class JavaFragmentBuilder(
        var mainClass: String? = null,
        var packagePrefix: String? = null,
    ) {
        companion object {
            operator fun invoke(block: JavaFragmentBuilder.() -> Unit): JavaFragmentBuilder {
                val builder = JavaFragmentBuilder()
                builder.block()
                return builder
            }
        }
    }

    data class NativeFragmentBuilder(
        var entryPoint: String? = null,
    ) {
        companion object {
            operator fun invoke(block: NativeFragmentBuilder.() -> Unit): NativeFragmentBuilder {
                val builder = NativeFragmentBuilder()
                builder.block()
                return builder
            }
        }
    }

    data class AndroidFragmentBuilder(
        var compileSdkVersion: String? = null,
        var androidMinSdkVersion: Int? = null,
    ) {
        companion object {
            operator fun invoke(block: AndroidFragmentBuilder.() -> Unit): AndroidFragmentBuilder {
                val builder = AndroidFragmentBuilder()
                builder.block()
                return builder
            }
        }
    }

    data class PublishingFragmentBuilder(
        var group: String? = null,
        var version: String? = null,
        var androidMinSdkVersion: Int? = null,
    ) {
        companion object {
            operator fun invoke(block: PublishingFragmentBuilder.() -> Unit): PublishingFragmentBuilder {
                val builder = PublishingFragmentBuilder()
                builder.block()
                return builder
            }
        }
    }

    data class JunitFragmentBuilder(
        var platformEnabled: Boolean? = null,
    ) {
        companion object {
            operator fun invoke(block: JunitFragmentBuilder.() -> Unit): JunitFragmentBuilder {
                val builder = JunitFragmentBuilder()
                builder.block()
                return builder
            }
        }
    }

    @Suppress("unused")
    enum class KotlinVersion(private val version: String) {
        Kotlin20("2.0"),
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

        companion object {

            private val versionMap: Map<String, KotlinVersion> = buildMap {
                KotlinVersion.values().forEach {
                    put(it.version, it)
                }
            }

            fun fromString(value: String): KotlinVersion? = versionMap[value]
        }
    }

    context (Stateful<FragmentBuilder, Fragment>)
    fun build(): Fragment = state.computeIfAbsent(this) { PlainFragment(it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FragmentBuilder

        if (name != other.name) return false
        if (platforms != other.platforms) return false
        if (externalDependencies != other.externalDependencies) return false
        if (variants != other.variants) return false
        if (alias != other.alias) return false
        if (kotlin != other.kotlin) return false
        if (java != other.java) return false
        if (native != other.native) return false
        if (android != other.android) return false
        if (publish != other.publish) return false
        return junit == other.junit
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + platforms.hashCode()
        result = 31 * result + externalDependencies.hashCode()
        result = 31 * result + variants.hashCode()
        result = 31 * result + (alias?.hashCode() ?: 0)
        result = 31 * result + (kotlin?.hashCode() ?: 0)
        result = 31 * result + (java?.hashCode() ?: 0)
        result = 31 * result + (native?.hashCode() ?: 0)
        result = 31 * result + (android?.hashCode() ?: 0)
        result = 31 * result + (publish?.hashCode() ?: 0)
        result = 31 * result + (junit?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "FragmentBuilder(name='$name', platforms=$platforms, variants=$variants, alias=$alias, src=$src)"
    }
}

internal fun List<FragmentBuilder>.multiplyFragments(variants: List<Settings>): List<FragmentBuilder> {
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
                            val newFragment = FragmentBuilder(element.name, element.platforms)
                            copyFields(newFragment, element)
                            newFragment
                        } else {
                            val newFragment = FragmentBuilder(
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

            val sourceFragments = fragmentMap[name] ?: error("Something went wrong")
            // correct previous old dependencies references after copying
            sourceFragments.forEach { fragment ->
                val dependenciesToRemove = mutableSetOf<MutableFragmentDependency>()
                val dependenciesToAdd = mutableSetOf<MutableFragmentDependency>()
                fragment.dependencies.forEach { sourceDependency ->
                    val targetFragment =
                        sourceFragments
                            .filter { it !== fragment }
                            .sortedByDescending { (sourceDependency.target.variants intersect it.variants).size }
                            .firstOrNull { it.platforms == sourceDependency.target.platforms }
                            ?: error("Something went wrong")

                    val targetDependency = MutableFragmentDependency(targetFragment, sourceDependency.dependencyKind)
                    dependenciesToRemove.add(sourceDependency)
                    dependenciesToAdd.add(targetDependency)

//                    targetFragment.dependants.map { it.target }.firstOrNull { it.name == fragment.name }?.removeDependency(targetDependency)

                }
                fragment.removeDependencies(dependenciesToRemove)
                fragment.addDependencies(dependenciesToAdd)
            }

            for (dependency in dependencies) {
                val dependencyTarget = dependency.getValue<String>("target")
                // add new dependencies related to copying
                fragmentMap[dependencyTarget]?.let { targetFragments ->
                    for (i in sourceFragments.indices) {
                        val kind = when (dependency.getValue<String>("kind")) {
                            "friend" -> MutableFragmentDependency.DependencyKind.Friend
                            else -> MutableFragmentDependency.DependencyKind.Refines
                        }
                        sourceFragments[i].addDependency(MutableFragmentDependency(targetFragments[i], kind))
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

private fun copyFields(new: FragmentBuilder, old: FragmentBuilder) {
    new.variants.addAll(old.variants)
    new.dependencies.addAll(old.dependencies)
//    new.dependants.addAll(old.dependants)
    new.alias = old.alias
}

context (Map<String, Set<Platform>>)
internal val Set<Set<Platform>>.basicFragments: List<FragmentBuilder>
    get() {
        val platforms = this
        return buildList {
            val sortedPlatformSubsets = platforms.sortedBy { it.size }
            val reducedPlatformSet = platforms.reduce { acc, set -> acc + set }
            sortedPlatformSubsets.forEach { platformSet ->
                val (name, alias) = platformSet.toCamelCaseString()
                val fragment = FragmentBuilder(name, platformSet, alias = alias)
                addFragment(fragment, platformSet)
            }

            if (reducedPlatformSet.size > 1) {
                val fragment = FragmentBuilder("common", reducedPlatformSet)
                addFragment(fragment, reducedPlatformSet)
            }
        }
    }

private fun MutableList<FragmentBuilder>.addFragment(fragment: FragmentBuilder, platforms: Set<Platform>) {
    forEach {
        if (platforms.containsAll(it.platforms)) {
            val alreadyExistsTransitively = it.dependencies.any {
                fragment.platforms.containsAll(it.target.platforms)
            }
            if (!alreadyExistsTransitively) {
                it.addDependency(
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

context (Map<String, Set<Platform>>, BuildFileAware)
internal fun List<FragmentBuilder>.handleAdditionalKeys(config: Settings) {
    config.handleFragmentSettings<List<String>>(this, "dependencies") { depList ->
        val resolved = depList.map { dep ->
            if (dep.startsWith(".")) {
                object : PotatoModuleDependency {
                    override val Model.module: PotatoModule
                        get() = modules.find {
                            if (it.source is PotatoModuleFileSource) {
                                val targetModulePotFilePath = (it.source as PotatoModuleFileSource)
                                    .buildFile
                                    .toAbsolutePath()
                                val sourceModulePotFilePath = buildFile.parent
                                    .resolve("$dep/Pot.yaml")
                                    .normalize()
                                    .toAbsolutePath()

                                val sourceModuleGradleFilePath = buildFile.parent
                                    .resolve("$dep/build.gradle.kts")
                                    .normalize()
                                    .toAbsolutePath()

                                targetModulePotFilePath == sourceModulePotFilePath ||
                                        targetModulePotFilePath == sourceModuleGradleFilePath
                            } else {
                                false
                            }
                        }
                            ?: error("No module $dep found")

                    override fun toString(): String {
                        return "InternalDependency(module=$dep)"
                    }
                }
            } else {
                MavenDependency(dep)
            }
        }
        externalDependencies.addAll(resolved)
    }

    config.handleFragmentSettings<Settings>(this, "settings") {
        it.getValue<Settings>("kotlin")?.let { kotlinSettings ->
            kotlin = FragmentBuilder.KotlinFragmentBuilder {
                kotlinSettings.getValue<Double>("languageVersion")?.let { kotlinVersion ->
                    languageVersion = FragmentBuilder.KotlinVersion.fromString(kotlinVersion.toString())
                }

                kotlinSettings.getValue<Double>("apiVersion")?.let { kotlinVersion ->
                    apiVersion = FragmentBuilder.KotlinVersion.fromString(kotlinVersion.toString())
                }

                sdkVersion = kotlinSettings.getByPath<String>("sdk", "version")

                kotlinSettings.getValue<List<String>>("features")?.let { features ->
                    languageFeatures.addAll(features)
                }
            }
        }
        it.getValue<Settings>("java")?.let { javaSettings ->
            java = FragmentBuilder.JavaFragmentBuilder {
                mainClass = javaSettings.getValue<String>("mainClass")
                packagePrefix = javaSettings.getValue<String>("packagePrefix")
            }
        }
        it.getValue<Settings>("publish")?.let { publishSettings ->
            publish = FragmentBuilder.PublishingFragmentBuilder {
                group = publishSettings.getValue<String>("group")
                version = publishSettings.getValue<String>("version")
            }
        }
        it.getValue<Settings>("junit")?.let { testSettings ->
            junit = FragmentBuilder.JunitFragmentBuilder {
                platformEnabled = testSettings.getValue<Boolean>("platformEnabled")
            }
        }
        it.getValue<Settings>("android")?.let { androidSettings ->
            android = FragmentBuilder.AndroidFragmentBuilder {
                compileSdkVersion = androidSettings.getValue<String>("compileSdkVersion")
                androidMinSdkVersion = androidSettings.getValue<Int>("minSdkVersion")
            }
        }
    }
}

context (BuildFileAware, Settings)
internal fun List<FragmentBuilder>.calculateSrcDir(platforms: Set<Platform>) {

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

        fragment.src = buildFile.parent.resolve(dir)
    }
}