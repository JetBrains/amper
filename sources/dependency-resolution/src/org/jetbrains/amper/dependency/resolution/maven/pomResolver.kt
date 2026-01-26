package org.jetbrains.amper.dependency.resolution.maven

import org.codehaus.plexus.util.Os
import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.DependencyResolutionBundle
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.dependency.resolution.MavenDependency
import org.jetbrains.amper.dependency.resolution.MavenDependencyImpl
import org.jetbrains.amper.dependency.resolution.ResolutionLevel
import org.jetbrains.amper.dependency.resolution.Settings
import org.jetbrains.amper.dependency.resolution.coordinates
import org.jetbrains.amper.dependency.resolution.createOrReuseDependency
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.ProjectHasMoreThanTenAncestors
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.UnableToParsePom
import org.jetbrains.amper.dependency.resolution.diagnostics.DiagnosticReporter
import org.jetbrains.amper.dependency.resolution.diagnostics.asMessage
import org.jetbrains.amper.dependency.resolution.mavenCoordinatesTrimmed
import org.jetbrains.amper.dependency.resolution.metadata.xml.ActivationFile
import org.jetbrains.amper.dependency.resolution.metadata.xml.ActivationOS
import org.jetbrains.amper.dependency.resolution.metadata.xml.ActivationProperty
import org.jetbrains.amper.dependency.resolution.metadata.xml.Dependencies
import org.jetbrains.amper.dependency.resolution.metadata.xml.Dependency
import org.jetbrains.amper.dependency.resolution.metadata.xml.DependencyManagement
import org.jetbrains.amper.dependency.resolution.metadata.xml.Parent
import org.jetbrains.amper.dependency.resolution.metadata.xml.Profile
import org.jetbrains.amper.dependency.resolution.metadata.xml.ProfileActivation
import org.jetbrains.amper.dependency.resolution.metadata.xml.Project
import org.jetbrains.amper.dependency.resolution.metadata.xml.Properties
import org.jetbrains.amper.dependency.resolution.metadata.xml.expandTemplates
import org.jetbrains.amper.dependency.resolution.metadata.xml.parsePom
import org.jetbrains.amper.dependency.resolution.metadata.xml.plus
import org.jetbrains.amper.dependency.resolution.resolveSingleVersion
import org.jetbrains.amper.incrementalcache.DynamicInputs
import org.jetbrains.amper.incrementalcache.getDynamicInputs
import org.jetbrains.amper.system.info.OsFamily
import org.slf4j.LoggerFactory
import java.util.concurrent.CancellationException
import java.util.regex.Pattern
import kotlin.io.path.Path
import kotlin.text.replace

private val logger = LoggerFactory.getLogger("dr/maven/pomResolver.kt")

/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

/**
 * This method resolves effective Project based on library pom.xml.
 * It takes text of pom.xml related to the maven dependency and resolve all its elements taken data
 * defined in parents, profiles, BOMs, properties into account.
 */
internal suspend fun MavenDependencyImpl.resolvePom(
    text: String, context: Context, level: ResolutionLevel, diagnosticsReporter: DiagnosticReporter,
): Project? {
    return try {
        parsePom(text).resolve(context, level, diagnosticsReporter)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val message = UnableToParsePom.asMessage(
            pom,
            extra = DependencyResolutionBundle.message("extra.exception", e),
            exception = e,
        )
        logger.warn(message.message, e)
        diagnosticsReporter.addMessage(message)
        return null
    }
}

/**
 * Resolves a Maven project by recursively substituting references to parent projects and templates
 * with actual values.
 * Additionally, dependency versions are defined using dependency management.
 */
private suspend fun Project.resolve(
    context: Context,
    resolutionLevel: ResolutionLevel,
    diagnosticsReporter: DiagnosticReporter,
    depth: Int = 0,
    origin: Project = this,
): Project {
    if (depth > 10) {
        diagnosticsReporter.addMessage(ProjectHasMoreThanTenAncestors.asMessage(origin))
        return this
    }
    val parentNode = parent?.let { context.createOrReuseDependency(it.coordinates, isBom = false) }

    val activeProfiles = this.activeProfiles(context.settings)

    val project = if (parentNode != null
        && parentNode.pom.isDownloadedOrDownload(resolutionLevel, context, diagnosticsReporter)
    ) {
        val text = parentNode.pom.readText()
        val parentProject =
            parentNode.parsePom(text).resolve(context, resolutionLevel, diagnosticsReporter, depth + 1, origin)
        copy(
            groupId = groupId ?: parentProject.groupId,
            artifactId = artifactId ?: parentProject.artifactId,
            version = version ?: parentProject.version,
            dependencies = activeProfiles.dependencies() + dependencies + parentProject.dependencies,
            properties = parentProject.properties + properties + activeProfiles.properties(),
        ).let {
            val importedDependencyManagement =
                it.resolveImportedDependencyManagement(context, resolutionLevel, diagnosticsReporter, depth)
            it.copy(
                // Dependencies declared directly in pom.xml dependencyManagement section take precedence over directly imported dependencies,
                // both in turn take precedence over parent dependencyManagement
                dependencyManagement = activeProfiles.dependencyManagement() + dependencyManagement +
                        importedDependencyManagement + parentProject.dependencyManagement,
            )
        }
    } else if (parent != null && (groupId == null || artifactId == null || version == null)) {
        val importedDependencyManagement =
            resolveImportedDependencyManagement(context, resolutionLevel, diagnosticsReporter, depth)
        copy(
            groupId = groupId ?: parent.groupId,
            artifactId = artifactId ?: parent.artifactId,
            version = version ?: parent.version,
            dependencies = activeProfiles.dependencies() + dependencies,
            dependencyManagement = activeProfiles.dependencyManagement() + dependencyManagement + importedDependencyManagement,
            properties = properties + activeProfiles.properties(),
        )
    } else {
        val importedDependencyManagement =
            resolveImportedDependencyManagement(context, resolutionLevel, diagnosticsReporter, depth)
        copy(
            dependencies = activeProfiles.dependencies() + dependencies,
            dependencyManagement = activeProfiles.dependencyManagement() + dependencyManagement + importedDependencyManagement,
            properties = properties + activeProfiles.properties(),
        )
    }

    val dependencyManagement = project.dependencyManagement?.copy(
        dependencies = project.dependencyManagement.dependencies?.copy(
            dependencies = project.dependencyManagement.dependencies.dependencies.map { it.expandTemplates(project) }
        )
    )

    val dependencies = project.getEffectiveDependencies(dependencyManagement)

    return project
        .expandTemplates()
        .copy(
            dependencies = dependencies?.let { Dependencies(it) },
            dependencyManagement = dependencyManagement
        )
}

/**
 * @return raw project dependencies with resolved declarations.
 * Unspecified versions are resolved from the dependencyManagement section (if any),
 * Project properties used in the dependencies declaration are substituted with actual values.
 */
private fun Project.getEffectiveDependencies(dependencyManagement: DependencyManagement?): List<Dependency>? =
    dependencies
    ?.dependencies
    ?.map { it.expandTemplates(this) } // expanding properties used in groupId/artifactId
    ?.map { dep ->
        if (dep.version != null && dep.scope != null) {
            return@map if (dep.version.resolveSingleVersion() != dep.version) {
                dep.copy(version = dep.version.resolveSingleVersion())
            } else dep
        }
        dependencyManagement
            ?.dependencies
            ?.dependencies
            ?.find { it.groupId == dep.groupId && it.artifactId == dep.artifactId }
            ?.let { dependencyManagementEntry ->
                return@map dep
                    .let {
                        val dependencyManagementEntryVersion =
                            dependencyManagementEntry.version?.resolveSingleVersion()
                        if (dep.version == null && dependencyManagementEntryVersion != null) it.copy(version = dependencyManagementEntryVersion)
                        else it
                    }.let {
                        if (dep.scope == null && dependencyManagementEntry.scope != null) it.copy(scope = dependencyManagementEntry.scope)
                        else it
                    }
            }
            ?: return@map dep
    }
    ?.map { it.expandTemplates(this) }

/**
 * Resolve an effective imported dependencyManagement.
 * If several dependencies are imported, then those are merged into a single [DependencyManagement] object.
 * The first declared import dependency takes precedence over the second one and so on.
 *
 * Parent poms of imported dependencies are taken into account
 * (in a standard way of resolving dependencyManagement section)
 * Specification tells about import scope:
 *  "It indicates the dependency is to be replaced with the
 *   effective list of dependencies in the specified POM's <dependencyManagement> section."
 *  (https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#dependency-scope)
 */
private suspend fun Project.resolveImportedDependencyManagement(
    context: Context,
    resolutionLevel: ResolutionLevel,
    diagnosticsReporter: DiagnosticReporter,
    depth: Int,
): DependencyManagement? = dependencyManagement
    ?.dependencies
    ?.dependencies
    ?.map { it.expandTemplates(this) }
    ?.mapNotNull {
        if (it.scope == "import" && it.version != null) {
            val dependency = context.createOrReuseDependency(it.coordinates, isBom = true)
            if (dependency.pom.isDownloadedOrDownload(resolutionLevel, context, diagnosticsReporter)) {
                val text = dependency.pom.readText()
                val dependencyProject =
                    dependency.parsePom(text).resolve(context, resolutionLevel, diagnosticsReporter, depth + 1)
                dependencyProject.dependencyManagement
            } else {
                null
            }
        } else {
            null
        }
    }
    ?.takeIf { it.isNotEmpty() }
    ?.reduce(DependencyManagement::plus)


private fun MavenDependency.parsePom(text: String): Project =
    sanitizePom(text, coordinates).parsePom()

private fun sanitizePom(pomText: String, coordinates: MavenCoordinates): String =
    if (coordinates.groupId == "org.codehaus.plexus" && coordinates.artifactId == "plexus")
        pomText.replace("&oslash;", "ø")
    else if (coordinates.artifactId == "hadoop-project")
        // Removing Xlint: prefix (that is recognized as an unknown namespace by XML parser making entire XML invalid)
        pomText
            .replace("<Xlint:-", "<")
            .replace("<Xlint:", "<")
    else
        pomText


private val Parent.coordinates: MavenCoordinates
    get() = mavenCoordinatesTrimmed(groupId = groupId, artifactId = artifactId, version = version)

private fun Profile.isActiveByDefault(): Boolean = activation?.any { it.activeByDefault == true } == true

private fun List<Profile>.properties(): Properties? {
    var properties: Properties? = null
    forEach {
        properties += it.properties
    }
    return properties.takeIf { it?.properties?.isNotEmpty() == true }
}

private fun List<Profile>.dependencies(): Dependencies? {
    var dependencies: Dependencies? = null
    forEach {
        dependencies += it.dependencies
    }
    return dependencies.takeIf { it?.dependencies?.isNotEmpty() == true }
}

private fun List<Profile>.dependencyManagement(): DependencyManagement? {
    var dependencyManagement: DependencyManagement? = null
    forEach {
        dependencyManagement += it.dependencyManagement
    }
    return dependencyManagement.takeIf { it?.dependencies?.dependencies?.isNotEmpty() == true }
}

private suspend fun Project.activeProfiles(settings: Settings): List<Profile> {
    val profiles = this.profiles?.profiles ?: return emptyList()

    val activatedProfiles =
        profiles.filter { it.isActivated(settings) }.takeIf { it.isNotEmpty() }
            ?: profiles.filter { it.isActiveByDefault() }

    return activatedProfiles
}

internal suspend fun Profile.isActivated(settings: Settings): Boolean {
    return activation != null && activation.isActivated(settings)
}

private suspend fun List<ProfileActivation>.isActivated(settings: Settings) =
    isNotEmpty() && this.all { it.isActivated(settings) }

private suspend fun ProfileActivation.isActivated(settings: Settings): Boolean {
    val dynamicInputs = getDynamicInputs()
    return when {
        jdk != null -> jdk.isActiveJdk(settings, dynamicInputs)
        os != null -> os.isActive(dynamicInputs)
        property != null -> property.isActive(dynamicInputs)
        file != null -> file.isActive(dynamicInputs)
        else -> false
    }
}

private fun getAndRegisterSystemProperty(name: String, dynamicInputs: DynamicInputs): String? =
    dynamicInputs.readSystemProperty(name)

private fun getAndRegisterEnvironmentVariable(name: String, dynamicInputs: DynamicInputs): String? =
    dynamicInputs.readEnv(name)

private fun String.isActiveJdk(settings: Settings, dynamicInputs: DynamicInputs): Boolean {
    val actualVersion = settings.jdkVersion?.value
        ?: getAndRegisterSystemProperty("java.version", dynamicInputs).takeIf { !it.isNullOrEmpty() }
        ?: return false

    val expectedJdk = this

    return if (expectedJdk.startsWith("!")) {
        !actualVersion.startsWith(expectedJdk.substring(1))
    } else if (expectedJdk.isRange()) {
        val range = expectedJdk.getRange() ?: return false
        actualVersion.isInRange(range)
        // "Failed to determine JDK activation for profile " + profile.getId() + " due invalid JDK version: '" + version + "'")
    } else {
        actualVersion.startsWith(expectedJdk)
    }
}

private fun ActivationOS.isActive(dynamicInputs: DynamicInputs): Boolean {
    if (name == null && family == null && arch == null && version == null)
        // all properties are omitted, there is nothing to check => condition is not met
        return false

    return OsActivationParameter.OS_NAME.matches(name, dynamicInputs)
            && OsActivationParameter.OS_FAMILY.matches(family, dynamicInputs)
            && OsActivationParameter.OS_ARCH.matches(arch, dynamicInputs)
            && OsActivationParameter.OS_VERSION.matches(version, dynamicInputs)
}

private enum class OsActivationParameter(
    /**
     * Value of parameter calculated based on the current system environment.
     */
    val value: String,
    val systemProperty: String?,
) {
    OS_NAME(value = Os.OS_NAME, systemProperty = "os.name"),
    OS_FAMILY(value = Os.OS_FAMILY, systemProperty = null),
    OS_ARCH(value = Os.OS_ARCH, systemProperty = "os.arch"),
    OS_VERSION(value = Os.OS_VERSION, systemProperty = "os.version") {
        override fun matches(expectedRawValue: String?, dynamicInputs: DynamicInputs): Boolean {
            // If the expected value is not defined, => this condition matches without checking the actual value.
            if (expectedRawValue == null) return true

            systemProperty?.let { getAndRegisterSystemProperty(it, dynamicInputs) }

            val actualVersion = value.lowercase()
            return if (expectedRawValue.startsWith("regex:")) {
                actualVersion.matches(expectedRawValue.substring(REGEX_PREFIX.length).toRegex())
            } else {
                super.matches(expectedRawValue, dynamicInputs)
            }
        }
    };

    open fun matches(expectedRawValue: String?, dynamicInputs: DynamicInputs): Boolean {
        // If the expected value is not defined, => this condition matches without checking the actual value.
        if (expectedRawValue == null) return true

        // Register system property used for calculation the actual value of activation parameters as proceed
        systemProperty?.let { getAndRegisterSystemProperty(it, dynamicInputs) }

        val expectedValueNegative = expectedRawValue.startsWith("!")
        val expectedValue = if (expectedValueNegative) expectedRawValue.substring(startIndex = 1) else expectedRawValue

        val valuesMatch = value.equals(expectedValue, ignoreCase = true)

        // XOR?
        return if (expectedValueNegative) {
            // condition is met if the property value is not defined or does not match
            !valuesMatch
        } else {
            // condition is met if the property value is defined and match
            valuesMatch
        }
    }
}

private const val REGEX_PREFIX: String = "regex:"

private fun ActivationFile.isActive(dynamicInputs: DynamicInputs): Boolean {
    // todo (AB) : Support interpolation
    // todo (AB) : See https://github.com/apache/maven/blob/f1cada5c1248dc0cd6252e737c292d018bfcfa80/compat/maven-model-builder/src/main/java/org/apache/maven/model/profile/activation/FileProfileActivator.java#L90
    val (actualPath, existing) = if (!exists.isNullOrBlank()) {
        exists.toPath()  to true
    } else if (!missing.isNullOrBlank()) {
        missing.toPath()  to false
    } else {
        // both conditions are omitted, there is no path to check => condition is not met
        return false
    }

    // Path is presented but can't be parsed
    actualPath ?: return false

    // Register path used in profile activation condition as proceed
    val actualPathExists = dynamicInputs.checkPathExistence(actualPath)

    return if (existing) actualPathExists else !actualPathExists
}

private fun ActivationProperty.isActive(dynamicInputs: DynamicInputs): Boolean {
    if (name.isNullOrBlank())
        // property name is omitted, there is nothing to check => condition is not met
        return false

    val propertyNameNegative = name.startsWith("!")
    val propertyName = if (propertyNameNegative) name.substring(startIndex = 1) else name

    val actualPropertyValue = if (propertyName.startsWith("env.")) {
        propertyName.substringAfter("env.")
            .let { if (OsFamily.current.isWindows) it.uppercase() else it }
            .let { getAndRegisterEnvironmentVariable(it, dynamicInputs) }
    } else {
        // todo (AB) : Support special property 'packaging' that should be taken from the POM file of a children of this parent POM.
        getAndRegisterSystemProperty(propertyName, dynamicInputs)
    }

    return if (propertyNameNegative) {
        // condition is met if the property is not defined
        actualPropertyValue == null
    } else {
        // property should be defined
        if (value.isNullOrBlank()) {
            // condition is met if the property is defined to any value
            actualPropertyValue != null
        } else {
            // value is defined
            val expectedValueNegative = value.startsWith("!")
            val expectedValue = if (expectedValueNegative) value.substring(startIndex = 1) else value
            if (expectedValueNegative) {
                // condition is met if the property is either not defined or its value is different from the expected value
                actualPropertyValue == null || actualPropertyValue != expectedValue
            } else {
                actualPropertyValue == expectedValue
            }
        }
    }
}

private fun String.toPath() = try {
    Path(this)
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

private fun String.isRange(): Boolean {
    return startsWith("[") || startsWith("(")
}

private fun String.isInRange(range: Range): Boolean {
    val actualJdkVersion = toJdkVersion() ?: return false // todo (AB) : Report warning

    val leftBoundMatches = if (range.left.closed)
        actualJdkVersion >= range.left.version
    else
        actualJdkVersion > range.left.version

    val rightBoundMatches = if (range.right.closed)
        actualJdkVersion <= range.right.version
    else
        actualJdkVersion < range.right.version

    return leftBoundMatches && rightBoundMatches
}

private fun String.getRangeBoundVersionParts() = split(".").toList()

private val filterRedundantSymbols: Pattern = Pattern.compile("[^\\d._-]")
private val filterDelimiter: Pattern = Pattern.compile("[._-]")

private fun String.toJdkVersion(): JdkVersion? =
    JdkVersion.create(
        filterRedundantSymbols.matcher(this).replaceAll("").split(filterDelimiter)
    )

private fun String.getRange(): Range? {
    val rangeParts = this.split(",")
    if (rangeParts.size > 2) return null // todo (AB) : Report warning

    val leftBound = rangeParts[0].trim().let {
        val closed = it.startsWith("[")
        val value = if (it.startsWith("[") || it.startsWith("(")) it.substring(startIndex = 1) else it
        val version = if (value.isBlank()) JdkVersion.minJdkVersion else JdkVersion.create(value.getRangeBoundVersionParts())
            ?: return null // todo (AB) : Report warning
        RangeBound(version, closed)
    }
    val rightBound = if (rangeParts.size == 2) {
        val rightPart = rangeParts[1].trim()
        val closed = rightPart.endsWith("]")
        val value = if (rightPart.endsWith("]") || rightPart.endsWith(")"))
            rightPart.substring(startIndex = 0, endIndex = rightPart.length - 1)
        else
            rightPart
        val version = if (value.isBlank()) JdkVersion.maxJdkVersion else JdkVersion.create(value.getRangeBoundVersionParts())
            ?: return null // todo (AB) : Report warning
        RangeBound(version, closed)
    } else
        RangeBound(JdkVersion.maxJdkVersion, false)

    return Range(leftBound, rightBound)
}

private class Range(val left: RangeBound, val right: RangeBound) {
    override fun toString(): String {
        return "${if(left.closed) "[" else "("}${left.version},${right.version}${if (right.closed) "]" else ")"}"
    }
}

private data class JdkVersion private constructor(
    val parts: List<Int>
): Comparable<JdkVersion> {
    override fun compareTo(other: JdkVersion): Int {
        for (i in 0..< Math.max(parts.size, other.parts.size)) {
            val thisPart = parts.getOrElse(i) { Int.MIN_VALUE }
            val otherPart = other.parts.getOrElse(i) { Int.MIN_VALUE }
            if (thisPart != otherPart) return thisPart.compareTo(otherPart)
        }
        return 0
    }

    companion object {
        fun create(parts: List<String>): JdkVersion?{
            if (parts.isEmpty()) error("Version parts should not be empty")
            val versionParts = parts.map { it.toIntOrNull() ?: return null } // todo (AB) : Report warning
            return JdkVersion(versionParts)
        }

        val minJdkVersion = JdkVersion(listOf(Int.MIN_VALUE))
        val maxJdkVersion = JdkVersion(listOf(Int.MAX_VALUE))
    }

    override fun toString(): String {
        return parts.joinToString(".")
    }
}

private data class RangeBound(
    val version: JdkVersion,
    val closed: Boolean
)
