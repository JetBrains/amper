package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.core.Result
import org.jetbrains.deft.proto.core.deftFailure
import org.jetbrains.deft.proto.core.getOrElse
import org.jetbrains.deft.proto.core.messages.ProblemReporterContext
import org.jetbrains.deft.proto.frontend.model.PlainArtifact
import org.jetbrains.deft.proto.frontend.model.PlainFragment
import org.jetbrains.deft.proto.frontend.model.PlainLeafFragment
import org.jetbrains.deft.proto.frontend.model.TestPlainArtifact
import org.jetbrains.deft.proto.frontend.nodes.*
import java.nio.file.Path
import java.util.*
import kotlin.collections.ArrayDeque

internal data class MutableFragmentDependency(val target: FragmentBuilder, val dependencyKind: DependencyKind) {
    enum class DependencyKind {
        Friend, Refines
    }
}

internal data class FragmentBuilder(
    val name: String,
    val platforms: Set<Platform>,
    val dependencies: MutableSet<MutableFragmentDependency> = mutableSetOf(),
    val dependants: MutableSet<MutableFragmentDependency> = mutableSetOf(),
    val externalDependencies: MutableSet<Notation> = mutableSetOf(),

    var isTest: Boolean = false,

    var isDefault: Boolean = true,

    var isLeaf: Boolean = false,

    /**
     * These are all variants, that this fragment should be included in.
     * Thus, "common" fragment will contain all variants.
     */
    val variants: MutableSet<String> = mutableSetOf(),
    var alias: String? = null,

    // parts
    var kotlin: KotlinPartBuilder? = KotlinPartBuilder {},
    var junit: JunitPartBuilder? = JunitPartBuilder {},

    // Leaf parts.
    var android: AndroidPartBuilder? = AndroidPartBuilder {},
    var ios: IosPartBuilder? = IosPartBuilder {},
    var native: NativePartBuilder? = NativePartBuilder {},
    var java: JavaPartBuilder? = JavaPartBuilder {},
    var jvm: JvmPartBuilder? = JvmPartBuilder {},
    var publishing: PublishingPartBuilder? = PublishingPartBuilder {},
    var compose: ComposePartBuilder? = ComposePartBuilder {},
) {

    lateinit var src: Path
    lateinit var resourcesPath: Path

    /**
     * Simple copy ctor.
     */
    constructor(
        name: String,
        copyFrom: FragmentBuilder,
    ) : this(name, copyFrom.platforms) {
        variants.addAll(copyFrom.variants)
        isTest = copyFrom.isTest
        dependencies.addAll(copyFrom.dependencies)
        alias = copyFrom.alias
        isDefault = copyFrom.isDefault
    }

    fun addDependency(mutableFragmentDependency: MutableFragmentDependency) {
        dependencies.add(mutableFragmentDependency)
        mutableFragmentDependency.target.dependants.add(
            MutableFragmentDependency(
                this, mutableFragmentDependency.dependencyKind
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

    context (Stateful<FragmentBuilder, Fragment>, TypesafeVariants)
    fun build(): Fragment = state.computeIfAbsent(this) {
        if (it.isLeaf) PlainLeafFragment(it) else PlainFragment(it)
    }

    context (Stateful<FragmentBuilder, Fragment>, TypesafeVariants)
    fun buildLeaf(): LeafFragment = build() as PlainLeafFragment

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
        return junit == other.junit
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + platforms.hashCode()
        result = 31 * result + externalDependencies.hashCode()
        result = 31 * result + variants.hashCode()
        result = 31 * result + (alias?.hashCode() ?: 0)
        result = 31 * result + (kotlin?.hashCode() ?: 0)
        result = 31 * result + (junit?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "FragmentBuilder(name='$name', platforms=$platforms, variants=$variants, alias=$alias, src=$src)"
    }
}

internal data class ArtifactBuilder(
    val name: String,
    val platforms: Set<Platform>,
    val variants: MutableSet<String> = mutableSetOf(),
    val fragments: MutableList<FragmentBuilder> = mutableListOf(),
) {
    context (Stateful<FragmentBuilder, Fragment>, TypesafeVariants)
    fun build(): Artifact {
        if (variants.contains("test")) {
            return TestPlainArtifact(this)
        }
        return PlainArtifact(this)
    }
}

internal fun List<FragmentBuilder>.multiplyFragments(variants: List<Variant>): List<FragmentBuilder> {
    val fragments = this.toMutableList()
    // multiply
    for (variant in variants) {
        val options = variant.options
        assert(options.isNotEmpty()) { "Options are required" }
        assert(options.count { it.isDefaultOption } == 1) { "Default value is required and must be single" }

        // copy fragments
        val fragmentMap = buildMap {
            for (option in options) {
                val optionName = option.name
                val newFragmentList = buildList {
                    for (element in fragments) {
                        val newFragmentName = if (option.isDefaultOption)
                            element.name
                        else
                            element.name.camelMerge(optionName)

                        val newFragment = FragmentBuilder(newFragmentName, element).apply {
                            // Adjust testing sign
                            if (optionName == "test") isTest = true

                            // Place a default flag for fragments that should be built by default and
                            // for their direct test descendants.
                            isDefault = element.isDefault && option.isDefaultFragment
                                    || isTest && element.isDefault

                        }

                        // Add new variant to fragment.
                        newFragment.variants.add(optionName)
                        add(newFragment)
                    }
                }
                put(optionName, newFragmentList)
            }
        }

        // set dependencies between modules
        for (option in options) {
            val dependencies = option.dependsOn
            val name = option.name

            val sourceFragments = fragmentMap[name] ?: error("Something went wrong")
            // correct previous old dependencies references after copying
            sourceFragments.forEach { fragment ->
                val dependenciesToRemove = mutableSetOf<MutableFragmentDependency>()
                val dependenciesToAdd = mutableSetOf<MutableFragmentDependency>()
                fragment.dependencies.forEach { sourceDependency ->
                    val targetFragment = sourceFragments.filter { it !== fragment }
                        .sortedByDescending { (sourceDependency.target.variants intersect it.variants).size }
                        .firstOrNull { it.platforms == sourceDependency.target.platforms }
                        ?: error("Something went wrong")

                    val targetDependency =
                        MutableFragmentDependency(targetFragment, sourceDependency.dependencyKind)
                    dependenciesToRemove.add(sourceDependency)
                    dependenciesToAdd.add(targetDependency)
                }
                fragment.removeDependencies(dependenciesToRemove)
                fragment.addDependencies(dependenciesToAdd)
            }

            for (dependency in dependencies) {
                val dependencyTarget = dependency.target
                // add new dependencies related to copying
                fragmentMap[dependencyTarget]?.let { targetFragments ->
                    for (i in sourceFragments.indices) {
                        val kind = when {
                            dependency.isFriend -> MutableFragmentDependency.DependencyKind.Friend
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

context (ParsingContext)
internal val Set<Set<Platform>>.basicFragments: List<FragmentBuilder>
    get() {
        val platforms = this
        return buildList {
            val sortedPlatformSubsets = platforms.sortedBy { it.size }
            sortedPlatformSubsets.forEach { platformSet ->
                val (name, alias) = platformSet.toCamelCaseString()
                val fragment = FragmentBuilder(name, platformSet, alias = alias)
                addFragment(fragment, platformSet)
            }

            if (isMultipleRoots()) {
                val reducedPlatformSet = platforms.reduce { acc, set -> acc + set }
                val fragment = FragmentBuilder("common", reducedPlatformSet)
                addFragment(fragment, reducedPlatformSet)
            }
        }
    }

context(BuildFileAware)
internal fun List<FragmentBuilder>.artifacts(
    variants: List<Variant>,
    productType: ProductType,
    platforms: Set<Platform>,
): List<ArtifactBuilder> {
    fun joinToCamelCase(strings: Set<String>): String {
        val list = strings.toList()
        val capitalizedStrings = list.mapIndexed { index, str ->
            if (index == 0) str else str.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
        val joinedString = capitalizedStrings.joinToString("")
        return joinedString.replaceFirstChar { it.lowercase(Locale.ROOT) }
    }

    val options = buildList {
        for (variant in variants) {
            val options = variant.options
            add(buildList {
                var default: String? = null
                var addDefault = true
                for (option in options) {
                    // add default if all dependencies are friends
                    if (option.isDefaultOption) {
                        default = option.name
                    } else {
                        add(
                            option.name
                        )

                        if (option.dependsOn.any { !it.isFriend }) {
                            addDefault = false
                        }
                    }
                }

                if (addDefault) {
                    add(default!!)
                }
            })
        }
    }

    val cartesian = cartesianSets(options)

    val fragmentBuilderList = this
    buildList {
        for (cartesianElement in cartesian) {
            for (platform in platforms) {
                fragmentBuilderList
                    .filter { it.variants == cartesianElement }
                    .firstOrNull { it.platforms == setOf(platform) }
                    ?.let {
                        add(it)
                    }
            }
        }
    }


    val leafFragments =
        cartesian.flatMap { cartesianElement -> filter { it.variants == cartesianElement }.filter { it.platforms.size == 1 } }

    return when {
        !productType.isLibrary() -> leafFragments
            .map { fragment ->
                fragment.isLeaf = true
                ArtifactBuilder(
                    fragment.name,
                    fragment.platforms,
                    fragment.variants,
                    mutableListOf(fragment)
                )
            }

        else -> {
            leafFragments
                .groupBy { it.variants }
                .entries
                .map {
                    val groupVariants = it.key
                    val fragments = it.value
                    fragments.forEach { fragment -> fragment.isLeaf = true }
                    ArtifactBuilder(
                        joinToCamelCase(groupVariants.toSet()),
                        fragments.flatMap { it.platforms }.toSet(),
                        groupVariants,
                        fragments.toMutableList()
                    )
                }
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
                        fragment, MutableFragmentDependency.DependencyKind.Refines
                    )
                )
            }
        }
    }
    add(fragment)
}

context (BuildFileAware, ProblemReporterContext, ParsingContext)
internal fun List<FragmentBuilder>.handleSettings(config: YamlNode.Mapping): Result<Unit> =
    config.handleFragmentSettings<YamlNode.Mapping>(this, "settings") {
        var hasErrors = false
        kotlin = KotlinPartBuilder {
            it.getMappingValue("kotlin")?.let { kotlinSettings ->
                // Special
                kotlinSettings.getStringValue("languageVersion") {
                    languageVersion = KotlinVersion.requireFromString(it)
                }
                kotlinSettings.getStringValue("apiVersion") {
                    apiVersion = KotlinVersion.requireFromString(it)
                }

                // Boolean
                kotlinSettings.getBooleanValue("allWarningsAsErrors") { allWarningsAsErrors = it }
                kotlinSettings.getBooleanValue("suppressWarnings") { suppressWarnings = it }
                kotlinSettings.getBooleanValue("verbose") { verbose = it }
                kotlinSettings.getBooleanValue("debug") { debug = it }
                kotlinSettings.getBooleanValue("progressiveMode") { progressiveMode = it }

                // Lists
                kotlinSettings.getSequenceValue("languageFeatures") {
                    languageFeatures.addAll(
                        it.elements.filterIsInstance<YamlNode.Scalar>().map { it.value })
                }
                kotlinSettings.getSequenceValue("optIns") {
                    optIns.addAll(
                        it.elements.filterIsInstance<YamlNode.Scalar>().map { it.value })
                }
                kotlinSettings.getSequenceValue("freeCompilerArgs") {
                    freeCompilerArgs.addAll(
                        it.elements.filterIsInstance<YamlNode.Scalar>().map { it.value })
                }

                kotlinSettings["serialization"]?.let { kSerialization ->
                    fun reportFormatError(message: String) {
                        problemReporter.reportNodeError(message, kSerialization, kSerialization.originalFile)
                        hasErrors = true
                    }

                    when (kSerialization) {
                        is YamlNode.Mapping -> {
                            (kSerialization.getMapping("format")?.second as? YamlNode.Scalar)?.value?.let { v ->
                                KotlinSerialization.fromString(v)?.let {
                                    serialization = it
                                    it.changeDependencies(externalDependencies)
                                }
                            } ?: {
                                reportFormatError(FrontendYamlBundle.message("wrong.kotlin.serialization.format"))
                            }
                        }

                        is YamlNode.Scalar -> {
                            KotlinSerialization.fromString(kSerialization.value)?.let {
                                serialization = it
                                it.changeDependencies(externalDependencies)
                            } ?: reportFormatError(FrontendYamlBundle.message("wrong.kotlin.serialization.format"))
                        }

                        else -> reportFormatError(FrontendYamlBundle.message("wrong.kotlin.serialization.notation"))
                    }
                }
            }
        }

        junit = JunitPartBuilder {
            val junitStringValue = it.getStringValue("junit")
            jUnitVersion = JUnitVersion.Index.resultFromString(junitStringValue).getOrElse { _ ->
                problemReporter.reportNodeError(
                    FrontendYamlBundle.message(
                        "wrong.enum.type",
                        JUnitVersion.Index.keys.joinToString { it },
                    ),
                    it["junit"],
                )
                hasErrors = true
                null
            }
        }

        if (hasErrors) deftFailure() else Result.success(Unit)
    }

context (BuildFileAware, ProblemReporterContext, ParsingContext)
internal fun List<ArtifactBuilder>.handleSettings(
    config: YamlNode.Mapping,
    fragments: List<FragmentBuilder>,
): Result<Unit> =
    config.handleFragmentSettings<YamlNode.Mapping>(fragments, "settings") {
        android = AndroidPartBuilder {
            it.getMappingValue("android")?.let { androidSettings ->
                compileSdkVersion = AndroidSdkVersion.fromString(androidSettings.getStringValue("compileSdkVersion"))
                minSdk = AndroidSdkVersion.fromString(androidSettings.getStringValue("minSdk"))
                minSdkPreview = AndroidSdkVersion.fromString(androidSettings.getStringValue("minSdkPreview"))
                maxSdk = AndroidSdkVersion.fromString(androidSettings.getStringValue("maxSdk"))
                targetSdk = AndroidSdkVersion.fromString(androidSettings.getStringValue("targetSdk"))
                applicationId = androidSettings.getStringValue("applicationId")
                namespace = androidSettings.getStringValue("namespace")
            }
        }

        ios = IosPartBuilder {
            it.getMappingValue("ios")?.let { iosSettings ->
                teamId = iosSettings.getStringValue("teamId")
            }
        }

        if (platforms.any { it.isParent(Platform.IOS) }) {
            native = NativePartBuilder {
                it.getMappingValue("ios")?.let { iosSettings ->
                    iosSettings.getMappingValue("framework")?.let { declaredFramework ->
                        this.frameworkSettings = IosFrameworkSettings(
                            declaredFramework.getStringValue("basename"),
                            declaredFramework.mappings.mapNotNull { (k, v) ->
                                val key = (k as? YamlNode.Scalar)?.value
                                val value = (v as? YamlNode.Scalar)?.value

                                if (key != null && value != null && key != "basename") Pair(key, value) else null
                            }
                        )
                    }
                }
            }
        }

        publishing = PublishingPartBuilder {
            it.getMappingValue("publishing")?.let { publishSettings ->
                group = publishSettings.getStringValue("group")
                version = publishSettings.getStringValue("version")
            }
        }

        java = JavaPartBuilder {
            it.getMappingValue("java")?.let { javaSettings ->
                source = javaSettings.getStringValue("source")
            }
        }

        jvm = JvmPartBuilder {
            it.getMappingValue("jvm")?.let { javaSettings ->
                mainClass = javaSettings.getStringValue("mainClass")
                target = javaSettings.getStringValue("target")
            }
        }

        compose = ComposePartBuilder {
            // inline form
            it.getStringValue("compose")?.let { composeValue ->
                enabled = when (composeValue) {
                    "enabled" -> true
                    else -> false
                }
            }
            // full form
            it.getMappingValue("compose")?.let { composeSettings ->
                enabled = composeSettings.getBooleanValue("enabled")
            }
        }

        Result.success(Unit)
    }

context (BuildFileAware, ParsingContext)
internal fun List<FragmentBuilder>.calculateSrcDir() {
    val defaultOptions = config.defaultOptionMap.values.toSet()
    val nonStdOptions = config.optionMap.filter { it.value.dimension != "mode" }.keys

    for (fragment in this) {
        val options = fragment.variants.filter { it in nonStdOptions }.toSet()
        val postfix = buildString {
            val optionsWithoutDefault = options.filter { it !in defaultOptions }

            if (fragment.platforms != platforms || optionsWithoutDefault.isNotEmpty()) {
                append("@")
            }

            if (fragment.platforms != platforms) {
                if (fragment.alias != null) {
                    append("${fragment.alias}")
                } else {
                    append(fragment.platforms.joinToString("+") { setOf(it).toCamelCaseString().first })
                }
            }

            if (optionsWithoutDefault.isNotEmpty()) {
                if (fragment.platforms != platforms) {
                    append("+")
                }
                append(optionsWithoutDefault.joinToString("+"))
            }
        }

        val srcDir = buildFile.parent
        if (fragment.isTest) {
            fragment.src = srcDir.resolve("test$postfix")
            fragment.resourcesPath = srcDir.resolve("testResources$postfix")
        } else {
            fragment.src = srcDir.resolve("src$postfix")
            fragment.resourcesPath = srcDir.resolve("resources$postfix")
        }
    }
}

private fun MutableList<FragmentBuilder>.isMultipleRoots() = getCommonRoot() == null

private fun MutableList<FragmentBuilder>.getCommonRoot(): FragmentBuilder? {
    var root: FragmentBuilder? = null

    val deque = ArrayDeque<FragmentBuilder>()

    for (fragment in this) {
        deque.add(fragment)
    }

    while (deque.isNotEmpty()) {
        val fragment = deque.removeFirst()
        if (fragment.dependencies.isEmpty()) {
            if (root == null) {
                root = fragment
            } else {
                if (root != fragment) {
                    root = null
                    break
                }
            }
        }
        for (dependency in fragment.dependencies) {
            deque.add(dependency.target)
        }
    }
    return root
}
