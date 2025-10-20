/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.CanBeReferenced
import org.jetbrains.amper.frontend.api.HiddenFromCompletion
import org.jetbrains.amper.frontend.api.Misnomers
import org.jetbrains.amper.frontend.api.ModifierAware
import org.jetbrains.amper.frontend.api.PlatformAgnostic
import org.jetbrains.amper.frontend.api.ProductTypeSpecific
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Shorthand
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.plugins.PluginDeclarationSchema
import java.nio.file.Path

abstract class Base : SchemaNode() {

    @SchemaDoc("The list of repositories used to look up and download external dependencies. [Read more](#managing-maven-repositories)")
    var repositories by nullableValue<List<Repository>>()

    @ModifierAware
    @SchemaDoc("The list of modules and libraries necessary to build the Module. [Read more](#dependencies)")
    var dependencies by nullableValue<List<Dependency>>()

    @ModifierAware
    @SchemaDoc("Configures the toolchains used in the build process. [Read more](#settings)")
    val settings: Settings by nested()

    @PlatformAgnostic
    @SchemaDoc("Plugins applied in `project.yaml` can be enabled and configured here")
    val plugins: PluginSettings by nested()

    @HiddenFromCompletion
    @SchemaDoc("Tasks settings. Experimental and will be replaced")
    var tasks by nullableValue<Map<String, TaskSettings>>()
}

class Template : Base()

class Module : Base() {

    @SchemaDoc("Defines what should be produced out of the module. Read more about the [product types](#product-types)")
    var product by value<ModuleProduct>()

    @SchemaDoc("Defines names for custom groups of platforms. This is useful to share code between platforms if the " +
            "group doesn't already exist in the default hierarchy. [Read more](#aliases)")
    var aliases by nullableValue<Map<TraceableString, List<TraceableEnum<Platform>>>>()

    @Misnomers("templates")
    @SchemaDoc("Lists the templates applied to the module. [Read more](#templates)")
    var apply by nullableValue<List<TraceablePath>>()

    @SchemaDoc("File layout of the module. [Read more](#maven-like-module-layout)")
    var layout by value(AmperLayout.AMPER)

    @ProductTypeSpecific(ProductType.JVM_AMPER_PLUGIN)
    var pluginInfo by nullableValue<PluginDeclarationSchema>()
}

class Repository : SchemaNode() {
    @CanBeReferenced  // by id
    @Shorthand
    @SchemaDoc("The url of the repository")
    var url by value<String>()

    @SchemaDoc("The ID of the repository, used to reference it. Defaults to the repository url")
    var id by dependentValue(::url)

    @SchemaDoc("Credentials to connect to this repository")
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

@SchemaDoc("File layout of the module. [Read more](##maven-like-module-layout)")
enum class AmperLayout(
    override var schemaValue: String,
    override val outdated: Boolean = false
) : SchemaEnum {

    @SchemaDoc("Maven like layout. [Read more](#maven-like-module-layout)")
    MAVEN_LIKE("maven-like"),

    @SchemaDoc("The [default Amper file layout](#project-layout) is used")
    AMPER("amper"),;

    companion object : EnumMap<AmperLayout, String>(AmperLayout::values, AmperLayout::schemaValue)
}

