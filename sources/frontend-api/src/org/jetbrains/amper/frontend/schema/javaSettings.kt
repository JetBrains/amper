/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.KnownIntValues
import org.jetbrains.amper.frontend.api.Misnomers
import org.jetbrains.amper.frontend.api.PlatformAgnostic
import org.jetbrains.amper.frontend.api.ProductTypeSpecific
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.StringSemantics
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.types.SchemaType.StringType.Semantics

enum class DependencyMode(override val schemaValue: String, override val outdated: Boolean = false): SchemaEnum {
    CLASSES("classes"),
    JARS("jars"),
}

class JavaAnnotationProcessingSettings : SchemaNode() {

    @SchemaDoc("The list of annotation processors to use. Each item can be a path to a local module, a catalog reference, or maven coordinates")
    val processors by value<List<UnscopedDependency>>(default = emptyList())

    @Misnomers("processorSettings")
    @SchemaDoc("Options to pass to annotation processors")
    val processorOptions by value<Map<String, TraceableString>>(default = emptyMap())
}

/**
 * Whether Java annotation processing should be run.
 */
val JavaAnnotationProcessingSettings.enabled: Boolean
    get() = processors.isNotEmpty()

class JavaSettings : SchemaNode() {

    @SchemaDoc("Java annotation processing settings")
    val annotationProcessing: JavaAnnotationProcessingSettings by nested()

    @Misnomers("compilation", "arguments", "options")
    @SchemaDoc("Pass any compiler option directly to the Java compiler")
    val freeCompilerArgs by value<List<TraceableString>>(emptyList())

    @Misnomers("compilation", "incremental")
    @SchemaDoc("Enables incremental compilation for Java sources")
    val compileIncrementally by value(default = false)
}

class JvmSettings : SchemaNode() {

    @PlatformAgnostic // different fragments must not use a different JDK, otherwise they disagree for 'common'
    @Misnomers("provisioning")
    @SchemaDoc("Defines requirements for the JDK to use. These requirements are used to provision a JDK or validate " +
            "whether JAVA_HOME points to a suitable one.")
    val jdk by nested<JdkSettings>()

    @PlatformAgnostic // different fragments must not target different releases, otherwise they disagree for 'common'
    @Misnomers("source", "target", "apiVersion")
    @SchemaDoc("The minimum JVM release version that the code should be compatible with. " +
            "This enforces compatibility on 3 levels.\n" +
            "* First, it is used as the target version for the bytecode generated from Kotlin and Java sources " +
            "(equivalent to -target).\n" +
            "* Second, it limits the Java platform APIs available to Kotlin and Java sources.\n" +
            "* Third, it limits the Java language constructs in Java sources (equivalent to -source).\n\n" +
            "If this is set to null, these constraints are not applied and the Java and Kotlin compiler defaults are " +
            "used. Note that those compilers have different defaults. Therefore, in mixed Java/Kotlin projects, " +
            "using 'null' will lead to different targets for Kotlin and Java code. If you really need to disable API " +
            "checks but want to align targets, use the freeCompilerArgs on both compilers to set the JVM target.")
    // null is intentionally supported, see docs
    @KnownIntValues(25, 21, 17, 11, 8)
    val release: Int? by dependentValue(::jdk) { jdk.version }

    @SchemaDoc("(Only for `jvm/app` [product type](#product-types)). The fully-qualified name of the class used to run the application")
    @ProductTypeSpecific(ProductType.JVM_APP)
    @StringSemantics(Semantics.JvmMainClass)
    val mainClass by nullableValue<String>()

    @PlatformAgnostic
    @Misnomers("parameters")
    @SchemaDoc("Enables storing formal parameter names of constructors and methods in the generated class files. " +
            "These can later be accessed using reflection. Behind the scenes, this passes the '-parameters' option " +
            "to the Java compiler, and the '-java-parameters' option to the Kotlin compiler.")
    val storeParameterNames by value(false)

    @SchemaDoc("JVM test-specific settings")
    val test: JvmTestSettings by nested()

    @PlatformAgnostic
    @SchemaDoc("Specifies how runtime classpath is constructed for the application. " +
            "The default is `jars`, which means all the dependencies including local dependencies on Amper modules will " +
            "be built as jars. The `classes` mode will use classes for local modules as part of the runtime classpath.")
    val runtimeClasspathMode by value(default = DependencyMode.JARS)
}

class JdkSettings : SchemaNode() {

    @PlatformAgnostic
    @Misnomers("majorVersion", "major")
    @SchemaDoc("The major version of the JDK to use for this module. A JDK of this version will be found or " +
            "provisioned depending on the selection mode setting.")
    val version by value(DefaultVersions.jdk)

    @PlatformAgnostic
    @Misnomers("vendors")
    @SchemaDoc("The list of acceptable JDK distributions, or null if all distributions should be accepted (the " +
            "default).")
    // We use null to mean "all distributions" here because of our current merging rules.
    // If we used a list, the user would not be able to override the list because lists are simply merged.
    // This would defeat the whole purpose of this 'distributions' property, which is to allow limiting the list.
    val distributions by value<List<TraceableEnum<JvmDistribution>>?>(default = null) // all distributions are allowed by default

    @PlatformAgnostic
    @Misnomers("javaHome", "provisioning")
    @SchemaDoc("Defines whether to use JAVA_HOME or provision a JDK. By default, Amper will use JAVA_HOME if it's " +
            "set to a JDK that matches the criteria, or provision a matching JDK from the network otherwise.")
    val selectionMode by value(default = JdkSelectionMode.auto)

    @PlatformAgnostic
    @Misnomers("paid", "proprietary")
    @SchemaDoc("Declares that the user understands and accepts the licenses for the given JDK distributions.")
    val acknowledgedLicenses by value<List<JvmDistribution>>(default = emptyList())
}

@Suppress("EnumEntryName")
enum class JdkSelectionMode(
    override val schemaValue: String,
) : SchemaEnum {
    @SchemaDoc("This is the default. If the JAVA_HOME environment variable is set and points to a JDK that matches " +
            "the criteria, Amper uses that JDK. In any other case, Amper finds a suitable JDK matching the criteria " +
            "via the Foojay Discovery API, and downloads it (or use a cached version if it's already in the Amper " +
            "cache).")
    auto("auto"),
    @SchemaDoc("Always rely on the provisioning mechanism, and ignore the JAVA_HOME environment variable. " +
            "In this mode, Amper finds a suitable JDK matching the criteria via the Foojay Discovery API, and " +
            "downloads it (or use a cached version if it's already in the Amper cache).")
    alwaysProvision("alwaysProvision"),
    @SchemaDoc("If the JAVA_HOME environment variable is set and points to a JDK that matches the criteria, Amper " +
            "uses that JDK. Otherwise, Amper fails the build. This mode effectively disables JDK provisioning, while " +
            "still ensuring that the criteria are respected.")
    javaHome("javaHome"),
}

/**
 * A JVM distribution.
 */
enum class JvmDistribution(
    override val schemaValue: String,
    /**
     * Whether this distribution requires a commercial license to be used in production.
     */
    val requiresLicense: Boolean = false,
) : SchemaEnum {
    /*
     HEADS UP! The order matters here because it serves as a priority for the selection mechanism.
     When multiple distributions are acceptable, the first one in the list is preferred.
     */
    @Misnomers("openjdk", "eclipse", "adoptopenjdk", "adoptium")
    @SchemaDoc("The [Eclipse Temurin](https://adoptium.net) distribution.")
    EclipseTemurin(schemaValue = "temurin"),

    @Misnomers("azul")
    @SchemaDoc("The [Azul Zulu](https://www.azul.com/downloads/?package=jdk#zulu) distribution.")
    AzulZulu(schemaValue = "zulu"),

    @Misnomers("amazon")
    @SchemaDoc("The [Amazon Corretto](https://aws.amazon.com/corretto) distribution.")
    AmazonCorretto(schemaValue = "corretto"),

    @Misnomers("jbr")
    @SchemaDoc("The [JetBrains Runtime](https://github.com/JetBrains/JetBrainsRuntime) distribution.")
    JetBrainsRuntime(schemaValue = "jetbrains"),

    @SchemaDoc("The [Oracle OpenJDK](https://openjdk.org) distribution.")
    OracleOpenJdk(schemaValue = "oracleOpenJdk"),

    @SchemaDoc("The [Microsoft JDK](https://learn.microsoft.com/en-us/java/openjdk/download) distribution.")
    Microsoft(schemaValue = "microsoft"),

    @Misnomers("alibaba")
    @SchemaDoc("The [Alibaba Dragonwell](https://dragonwell-jdk.io) distribution.")
    AlibabaDragonwell(schemaValue = "dragonwell"),

    @Misnomers("bellsoft")
    @SchemaDoc("The [BellSoft Liberica](https://bell-sw.com) distribution.")
    BellSoftLiberica(schemaValue = "liberica"),

    @SchemaDoc("The [SapMachine](https://sap.github.io/SapMachine) distribution.")
    SapMachine(schemaValue = "sapMachine"),

    @Misnomers("openj9", "ibm")
    @SchemaDoc("The [IBM Semeru Runtime Open Edition](https://www.ibm.com/support/pages/semeru-runtimes-getting-started) distribution, based on the [Eclipse OpenJ9](https://eclipse.dev/openj9) JVM")
    IbmSemeru(schemaValue = "semeru"),

    @Misnomers("graalce", "graalvmce", "community")
    @SchemaDoc("The [GraalVM Community Edition](https://github.com/graalvm/graalvm-ce-builds/releases/) distribution.")
    GraalVMCommunityEdition(schemaValue = "graalVM"),

    @SchemaDoc("The [Oracle GraalVM](https://www.oracle.com/downloads/graalvm-downloads.html) distribution " +
            "(formerly known as GraalVM Enterprise Edition).")
    // https://download.oracle.com/graalvm/25/archive/graalvm-jdk-25.0.1_windows-x64_bin.zip
    OracleGraalVM(schemaValue = "oracleGraalVM", requiresLicense = true),
}
