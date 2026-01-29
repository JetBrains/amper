package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.contexts.DefaultContext
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.frontend.types.SchemaType
import java.io.IOException
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeTo

context(buildCtx: BuildCtx)
internal fun MapLikeValue<*>.discoverCinteropDefs(): Merged {
    val discoveryAppender = CinteropDiscoveryAppender()
    val withDiscoveredDefs = discoveryAppender.transform(this) as MapLikeValue<*>
    return buildCtx.treeMerger.mergeTrees(listOf(withDiscoveredDefs))
}

private class CinteropDiscoveryAppender : TreeTransformer<TreeState>() {

    override fun visitMapValue(value: MapLikeValue<TreeState>): TransformResult<MapLikeValue<TreeState>> {
        val transformResult = super.visitMapValue(value)
        val transformedValue = when (transformResult) {
            is Changed -> transformResult.value
            NotChanged -> value
            Removed -> return transformResult
        }

        val moduleDir = transformedValue.trace.extractPsiElementOrNull()?.containingFile?.virtualFile?.parent
            ?: return transformResult
        val cinteropDir = moduleDir.findChild("resources")?.findChild("cinterop")

        val discoveredDefFiles = if (cinteropDir != null && cinteropDir.exists() && cinteropDir.isDirectory) {
            try {
                cinteropDir.children.filter { it.name.endsWith(".def") }
            } catch (e: IOException) {
                // Log error?
                emptyList()
            }
        } else {
            emptyList()
        }

        val modulePath = moduleDir.toNioPath()
        val discoveredModules = discoveredDefFiles.associate {
            val moduleName = it.nameWithoutExtension
            val defPath = it.toNioPath().relativeTo(modulePath).invariantSeparatorsPathString
            val defFileProperty = MapLikeValue.Property(
                "defFile",
                DefaultTrace,
                ScalarValue<TreeState>(defPath, SchemaType.StringType, DefaultTrace, listOf(DefaultContext.ReactivelySet)),
                null
            )
            val moduleNode = Owned(
                children = listOf(defFileProperty),
                type = SchemaType.MapType(SchemaType.StringType),
                trace = DefaultTrace,
                contexts = listOf(DefaultContext.ReactivelySet)
            )
            moduleName to moduleNode
        }

        val cinteropProperty = transformedValue.children.find { it.key == "cinterop" }
        val explicitCinteropNode = cinteropProperty?.value as? MapLikeValue<TreeState>

        // Get explicitly defined modules
        val explicitModules = explicitCinteropNode?.children
            ?.filter { it.value is MapLikeValue<*> }
            ?.associate { it.key to it.value as MapLikeValue<TreeState> }
            .orEmpty()

        // Merge discovered and explicit modules
        val allModuleNames = discoveredModules.keys + explicitModules.keys
        val mergedModuleProperties = allModuleNames.map { moduleName ->
            val discovered = discoveredModules[moduleName]
            val explicit = explicitModules[moduleName]
            val mergedNode = when {
                discovered != null && explicit != null -> {
                    // Merge properties: explicit wins
                    val discoveredProps = discovered.children.associateBy { it.key }
                    val explicitProps = explicit.children.associateBy { it.key }
                    val allProps = (discoveredProps + explicitProps).values.toList()
                    discovered.copy(children = allProps)
                }
                else -> discovered ?: explicit!!
            }
            MapLikeValue.Property(moduleName, DefaultTrace, mergedNode, null)
        }

        if (mergedModuleProperties.isEmpty()) {
            return transformResult
        }

        val newCinteropNode = (explicitCinteropNode ?: Owned(emptyList(), SchemaType.MapType(SchemaType.StringType), DefaultTrace, emptyList()))
            .copy(children = mergedModuleProperties)
        val newCinteropMapProperty = MapLikeValue.Property("cinterop", DefaultTrace, newCinteropNode, cinteropProperty?.pType)

        val finalChildren = transformedValue.children.filter { it.key != "cinterop" } + newCinteropMapProperty

        return Changed(transformedValue.copy(children = finalChildren))
    }
}