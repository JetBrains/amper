/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import org.jetbrains.amper.dependency.resolution.diagnostics.registerSerializableMessages
import java.util.*
import kotlin.reflect.KClass

object GraphJson {

    private val providers = listOf(DefaultSerializableTypesProvider()) +
            ServiceLoader.load(GraphSerializableTypesProvider::class.java)

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        allowStructuredMapKeys = true

        serializersModule = SerializersModule {
            providers.forEach {
                with (it) {
                    registerPolymorphic()
                }
            }
        }
    }
}

interface GraphSerializableTypesProvider {
    fun SerializersModuleBuilder.registerPolymorphic()
}

internal class DefaultSerializableTypesProvider: GraphSerializableTypesProvider {
    override fun SerializersModuleBuilder.registerPolymorphic() {
        moduleForDependencyNodePlainHierarchy()
        moduleForDependencyNodeHierarchy()
        moduleMessageHierarchy()
    }

    fun SerializersModuleBuilder.moduleForDependencyNodePlainHierarchy() =
        moduleForDependencyNodeHierarchy(DependencyNodePlain::class as KClass<DependencyNode>)

    fun SerializersModuleBuilder.moduleForDependencyNodeHierarchy() =
        moduleForDependencyNodeHierarchy(DependencyNode::class)

    fun SerializersModuleBuilder.moduleForDependencyNodeHierarchy(kClass: KClass<DependencyNode>) {
        polymorphic(kClass, MavenDependencyNodePlain::class, MavenDependencyNodePlain.serializer())
        polymorphic(kClass, RootDependencyNodePlain::class, RootDependencyNodePlain.serializer())
        polymorphic(kClass,MavenDependencyConstraintNodePlain::class,MavenDependencyConstraintNodePlain.serializer()        )
    }

    fun SerializersModuleBuilder.moduleMessageHierarchy() =
        registerSerializableMessages()
}