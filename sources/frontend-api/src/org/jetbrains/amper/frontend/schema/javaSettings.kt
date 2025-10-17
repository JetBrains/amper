/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.EnumOrderSensitive
import org.jetbrains.amper.frontend.api.EnumValueFilter
import org.jetbrains.amper.frontend.api.Misnomers
import org.jetbrains.amper.frontend.api.PlatformAgnostic
import org.jetbrains.amper.frontend.api.ProductTypeSpecific
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.StringSemantics
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.types.SchemaType.StringType.Semantics

@EnumOrderSensitive(reverse = true)
@EnumValueFilter("outdated", isNegated = true)
enum class JavaVersion(
    override val schemaValue: String,
    override val outdated: Boolean = false,
    val legacyNotation: String = schemaValue,
) : SchemaEnum {
    // TODO remove entries below Java 8, as they are not supported by the Kotlin compiler options that we set from it
    //   Do we need them anyway?
    VERSION_1("1", outdated = true, legacyNotation = "1.1"),
    VERSION_2("2", outdated = true, legacyNotation = "1.2"),
    VERSION_3("3", outdated = true, legacyNotation = "1.3"),
    VERSION_4("4", outdated = true, legacyNotation = "1.4"),
    VERSION_5("5", outdated = true, legacyNotation = "1.5"),
    VERSION_6("6", outdated = true, legacyNotation = "1.6"),
    VERSION_7("7", outdated = true, legacyNotation = "1.7"),
    VERSION_8("8", legacyNotation = "1.8"),
    VERSION_9("9", outdated = true),
    VERSION_10("10", outdated = true),
    VERSION_11("11"),
    VERSION_12("12", outdated = true),
    VERSION_13("13", outdated = true),
    VERSION_14("14", outdated = true),
    VERSION_15("15", outdated = true),
    VERSION_16("16", outdated = true),
    VERSION_17("17"),
    VERSION_18("18", outdated = true),
    VERSION_19("19", outdated = true),
    VERSION_20("20", outdated = true),
    VERSION_21("21"),
    VERSION_22("22"),
    VERSION_23("23"),
    VERSION_24("24"),
    VERSION_25("25");

    /**
     * The integer representation of this version for the `--release` option of the Java compiler, and the
     * `-Xjdk-release` option of the Kotlin compiler.
     *
     * Notes:
     *  * The Java compiler only supports versions from 6 and above for the `--release` option.
     *  * Despite the documentation, the Kotlin compiler supports "8" as an alias for "1.8" in `-Xjdk-release`, but the
     *    `-jvm-target` option requires "1.8".
     */
    val releaseNumber: Int = schemaValue.toInt()

    companion object Index : EnumMap<JavaVersion, String>(JavaVersion::values, JavaVersion::schemaValue)
}

enum class DependencyMode(override val schemaValue: String, override val outdated: Boolean = false): SchemaEnum {
    CLASSES("classes"),
    JARS("jars"),
}

class JavaAnnotationProcessingSettings : SchemaNode() {

    @SchemaDoc("The list of annotation processors to use. Each item can be a path to a local module, a catalog reference, or maven coordinates")
    var processors by value<List<UnscopedDependency>>(default = emptyList())

    @Misnomers("processorSettings")
    @SchemaDoc("Options to pass to annotation processors")
    var processorOptions by value<Map<String, TraceableString>>(default = emptyMap())
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
    var freeCompilerArgs by value<List<TraceableString>>(emptyList())
}

class JvmSettings : SchemaNode() {

    @PlatformAgnostic
    @Misnomers("jdk", "source", "target", "apiVersion")
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
    var release by nullableValue(JavaVersion.VERSION_17) // TODO discuss the default

    @SchemaDoc("(Only for `jvm/app` [product type](#product-types)). The fully-qualified name of the class used to run the application")
    @ProductTypeSpecific(ProductType.JVM_APP)
    @StringSemantics(Semantics.JvmMainClass)
    var mainClass by nullableValue<String>()

    @PlatformAgnostic
    @Misnomers("parameters")
    @SchemaDoc("Enables storing formal parameter names of constructors and methods in the generated class files. " +
            "These can later be accessed using reflection. Behind the scenes, this passes the '-parameters' option " +
            "to the Java compiler, and the '-java-parameters' option to the Kotlin compiler.")
    var storeParameterNames by value(false)

    @SchemaDoc("JVM test-specific settings")
    val test: JvmTestSettings by nested()

    @PlatformAgnostic
    @SchemaDoc("Specifies how runtime classpath is constructed for the application. " +
            "The default is `jars`, which means all the dependencies including local dependencies on Amper modules will " +
            "be built as jars. The `classes` mode will use classes for local modules as part of the runtime classpath.")
    var runtimeClasspathMode by value(default = DependencyMode.JARS)
}
