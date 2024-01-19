/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.ProductType
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.AdditionalSchemaDef
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.unsafe
import org.jetbrains.amper.frontend.reportBundleError

@AdditionalSchemaDef(productShortForm, useOneOf = true)
class ModuleProduct : SchemaNode() {
    var type by value<ProductType>()

    var platforms by value<List<Platform>>()
        .default { ::type.unsafe?.defaultPlatforms?.toList() }

    context(ProblemReporterContext)
    override fun validate() {
        // Check empty platforms.
        if (::platforms.unsafe?.isEmpty() == true)
            SchemaBundle.reportBundleError(
                ::platforms,
                "product.platforms.should.not.be.empty",
                level = Level.Fatal
            )

        // Check no platforms for lib.
        if (::type.unsafe == ProductType.LIB && ::platforms.unsafe == null)
            SchemaBundle.reportBundleError(
                ::type,
                "product.type.does.not.have.default.platforms",
                ProductType.LIB.schemaValue,
                level = Level.Fatal
            )

        // Check supported platforms.
        ::platforms.unsafe.orEmpty().forEach { platform ->
            if (platform !in type.supportedPlatforms)
                SchemaBundle.reportBundleError(
                    ::platforms,
                    "product.unsupported.platform",
                    type.schemaValue,
                    platform.pretty,
                    type.supportedPlatforms.joinToString { it.pretty },
                )
        }
    }
}

const val productShortForm = """
  {
    "enum": ["lib","app","jvm/app","android/app","ios/app","macos/app","linux/app"]
  }
"""