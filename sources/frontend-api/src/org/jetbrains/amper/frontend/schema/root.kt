/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.Aliases
import org.jetbrains.amper.frontend.api.ModifierAware
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Shorthand
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.api.TraceableString
import java.nio.file.Path


typealias Modifiers = Set<TraceableString>
val noModifiers = emptySet<TraceableString>()

sealed class Base : SchemaNode() {

    @SchemaDoc("The list of repositories used to look up and download the Module dependencies. [Read more](#managing-maven-repositories)")
    var repositories by nullableValue<List<Repository>>()

    @ModifierAware
    @SchemaDoc("The list of modules and libraries necessary to build the Module. [Read more](#dependencies)")
    var dependencies by nullableValue<Map<Modifiers, List<Dependency>>>()

    @ModifierAware
    @SchemaDoc("Configures the toolchains used in the build process. [Read more](#settings)")
    var settings by value(mapOf(noModifiers to Settings()))

    @ModifierAware
    @SchemaDoc("The dependencies necessary to build and run tests of the Module. [Read more](#dependencies)")
    var `test-dependencies` by nullableValue<Map<Modifiers, List<Dependency>>>()

    @ModifierAware
    @SchemaDoc("Configures the toolchains used in the build process of the module's tests. [Read more](#settings)")
    var `test-settings` by value(mapOf(noModifiers to Settings()))

    @SchemaDoc("Tasks settings. Experimental and will be replaced")
    var tasks by nullableValue<Map<String, TaskSettings>>()
}

/**
 * Common settings section.
 */
val Base.commonSettings get() = checkNotNull(settings[noModifiers]) { "Common settings should always be present in the Base node" }

class Template : Base()

class Module : Base() {

    @SchemaDoc("Defines what should be produced out of the module. Read more about the [product types](#product-types)")
    var product by value<ModuleProduct>()

    @SchemaDoc("Defines the names for the custom code sharing groups. [Read more](#aliases)")
    var aliases by nullableValue<Map<String, Set<TraceableEnum<Platform>>>>()

    @Aliases("templates")
    @SchemaDoc("Lists the templates applied to the module. [Read more](#templates)")
    var apply by nullableValue<List<TraceablePath>>()

    @SchemaDoc("Configures various aspects of the module, such as file layout")
    var module by value(::Meta)
}

class Repository : SchemaNode() {
    @Shorthand
    @SchemaDoc("The url of the repository")
    var url by value<String>()

    @SchemaDoc("The ID of the repository, used for to reference it. Defaults to the repository url")
    var id by dependentValue(::url)

    @SchemaDoc("Credentials for the authenticated repositories")
    var credentials by nullableValue<Credentials>()

    @SchemaDoc("Whether this repository can be used to publish artifacts")
    var publish by value(false)

    @SchemaDoc("Whether this repository can be used to resolve artifacts")
    var resolve by value(true)

    class Credentials : SchemaNode() {

        @SchemaDoc("A relative path to a file with the credentials. Currently, only `*.property` files are supported")
        var file by value<Path>()

        @SchemaDoc("A key in the file that holds the username")
        var usernameKey by value<String>()

        @SchemaDoc("A key in the file that holds the password")
        var passwordKey by value<String>()
    }

    companion object {
        const val SpecialMavenLocalUrl = "mavenLocal"
    }
}

class TaskSettings: SchemaNode() {
    @SchemaDoc("Adds to task dependencies")
    var dependsOn by nullableValue<List<TraceableString>>()
}

@SchemaDoc("File layout of the module. [Read more](#file-layout-with-gradle-interop)")
enum class AmperLayout(
    override var schemaValue: String,
    override val outdated: Boolean = false
) : SchemaEnum {
    @SchemaDoc("The file layout corresponds to the Gradle [Kotlin Multiplatform layout](https://kotlinlang.org/docs/multiplatform-discover-project.html#source-sets)")
    GRADLE("gradle-kmp"),

    @SchemaDoc("The file layout corresponds to the standard Gradle [JVM layout](https://docs.gradle.org/current/userguide/organizing_gradle_projects.html)")
    GRADLE_JVM("gradle-jvm"),

    @SchemaDoc("The [default Amper file layout](#project-layout) is used")
    AMPER("default"),;

    companion object : EnumMap<AmperLayout, String>(AmperLayout::values, AmperLayout::schemaValue)
}

class Meta : SchemaNode() {

    @SchemaDoc("File layout of the module. [Read more](#file-layout-with-gradle-interop)")
    var layout by value(AmperLayout.AMPER)
}
