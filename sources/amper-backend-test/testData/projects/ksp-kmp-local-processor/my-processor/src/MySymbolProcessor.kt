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
    private val platforms = environment.platforms

    override fun process(resolver: Resolver): List<KSAnnotated> {
        println("Running my local symbol processor!")

        println("Here are some test logs at different levels:")
        logger.info("info log")
        logger.warn("warn log")
        logger.error("error log")

        val annotationName = MyKspAnnotation::class.qualifiedName ?: error("")
        val (annotatedClasses, invalidClasses) = resolver
            .getSymbolsWithAnnotation(annotationName)
            .filterIsInstance<KSClassDeclaration>()
            .toList()
            .partition { it.validate() }

        println("Found ${annotatedClasses.size} annotated classes: ${annotatedClasses.map { it.qualifiedName?.asString() }}")
        println("Found ${invalidClasses.size} invalid annotated classes: ${invalidClasses.map { it.qualifiedName?.asString() }}")

        println("Generating resource file...")

        val allFilesInModuleDependencies = Dependencies(
            aggregating = false, // we don't use anything outside the current module
            *resolver.getAllFiles().toList().toTypedArray() // tells KSP we have looked at all files - don't rerun if nothing changes
        )

        // generate Kotlin source files
        annotatedClasses.forEach { c ->
            val generatedClassName = "${c.simpleName?.asString()}Generated"
            codeGenerator.createNewFile(
                dependencies = allFilesInModuleDependencies,
                packageName = "com.sample.myprocessor.gen",
                fileName = generatedClassName,
                extensionName = "kt",
            ).bufferedWriter().use { out ->
                out.write("""
                    package com.sample.myprocessor.gen
                    
                    class $generatedClassName {
                        fun generatedHello() {
                            println("Hello from generated class $generatedClassName")
                        }
                    }
                """.trimIndent())
            }
        }

        // generate a resource file
        codeGenerator.createNewFile(
            dependencies = allFilesInModuleDependencies,
            packageName = "com.sample.myprocessor.gen",
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

        if (platforms.all { it is JvmPlatformInfo }) {
            // generate Java source files only for JVM targets
            annotatedClasses.forEach { c ->
                val generatedClassName = "${c.simpleName?.asString()}GeneratedJava"
                codeGenerator.createNewFile(
                    dependencies = allFilesInModuleDependencies,
                    packageName = "com.sample.myprocessor.gen",
                    fileName = generatedClassName,
                    extensionName = "java",
                ).bufferedWriter().use { out ->
                    out.write("""
                    package com.sample.myprocessor.gen;
                    
                    public class $generatedClassName {
                        public void generatedHelloJava() {
                            System.out.println("Hello from generated Java class $generatedClassName");
                        }
                    }
                """.trimIndent())
                }
            }

            // generate a class file only for JVM targets
            codeGenerator.createNewFile(
                dependencies = allFilesInModuleDependencies,
                packageName = "com.sample.myprocessor.gen",
                fileName = "MyGeneratedClass",
                extensionName = "class",
            ).use { out ->
                val classResourcePath = "/classes-to-generate/com/sample/myprocessor/gen/MyGeneratedClass.class"
                val generatedClassContentsStream = MySymbolProcessor::class.java.getResourceAsStream(classResourcePath)
                    ?: error("Missing resource $classResourcePath")
                generatedClassContentsStream
                    .use { classContentsStream ->
                        classContentsStream.copyTo(out)
                    }
            }
        }
        return invalidClasses
    }

    private fun <T> List<T>.reversedIf(reverse: Boolean): List<T> = if (reverse) reversed() else this

    class Provider : SymbolProcessorProvider {
        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = MySymbolProcessor(environment)
    }
}
