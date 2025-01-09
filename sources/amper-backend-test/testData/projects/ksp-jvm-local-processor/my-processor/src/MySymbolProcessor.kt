package com.sample.ksp.localprocessor.processor

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.sample.ksp.localprocessor.annotation.MyKspAnnotation

class MySymbolProcessor(environment: SymbolProcessorEnvironment) : SymbolProcessor {

    private val logger = environment.logger
    private val codeGenerator = environment.codeGenerator
    private val options = environment.options

    override fun process(resolver: Resolver): List<KSAnnotated> {
        println("Running my local symbol processor!")

        println("Here are some test logs at levels info & warn (not error):")
        logger.info("info log") // apparently not logged by KSP
        logger.warn("warn log")
        // logger.error("error log") // KSP 1.0.28+ automatically fails with exit code 1 if any error is logged

        val annotationName = MyKspAnnotation::class.qualifiedName ?: error("")
        val (annotatedClasses, invalidClasses) = resolver
            .getSymbolsWithAnnotation(annotationName)
            .filterIsInstance<KSClassDeclaration>()
            .toList()
            .partition { it.validate() }

        println("Found ${annotatedClasses.size} annotated classes: ${annotatedClasses.map { it.qualifiedName?.asString() }}")
        println("Found ${invalidClasses.size} invalid annotated classes: ${invalidClasses.map { it.qualifiedName?.asString() }}")

        if (annotatedClasses.isNotEmpty()) {
            println("Generating resource file annotated-classes.txt...")

            val allFilesInModuleDependencies = Dependencies(
                aggregating = false, // we don't use anything outside the current module
                *resolver.getAllFiles().toList().toTypedArray() // tells KSP we have looked at all files - don't rerun if nothing changes
            )

            // generate a resource file
            codeGenerator.createNewFile(
                dependencies = allFilesInModuleDependencies,
                packageName = "com.sample.generated",
                fileName = "annotated-classes",
                extensionName = "txt",
            ).bufferedWriter().use { out ->
                val annotatedClassNames = annotatedClasses
                    .map { it.qualifiedName!!.asString() }
                    .sorted()
                    .reversedIf(options["${MySymbolProcessor::class.java.packageName}.reverseOrder"] == "true")
                    .joinToString("\n")
                out.write(annotatedClassNames)
            }
            println("Resource file annotated-classes.txt generation complete.")
        }
        return invalidClasses
    }

    private fun <T> List<T>.reversedIf(reverse: Boolean): List<T> = if (reverse) reversed() else this

    class Provider : SymbolProcessorProvider {
        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = MySymbolProcessor(environment)
    }
}
