package org.jetbrains.amper.ext.kspdemo

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated

/**
 * Very simple demo KSP processor.
 * When invoked, it generates a file kspGenerated.kt with a class KspGenerated.
 */
class DemoProcessor(private val environment: SymbolProcessorEnvironment) : SymbolProcessor {
    private val codeGenerator: CodeGenerator = environment.codeGenerator
    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()

        val deps = Dependencies(aggregating = false, *resolver.getAllFiles().toList().toTypedArray())
        codeGenerator.createNewFile(
            dependencies = deps,
            packageName = "", // no package requested by the task
            fileName = "kspGenerated",
            extensionName = "kt",
        ).bufferedWriter().use { out ->
            out.write(
                """
                |class KspGenerated
                |
                |""".trimMargin()
            )
        }

        generated = true
        return emptyList()
    }
}

class DemoProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = DemoProcessor(environment)
}
