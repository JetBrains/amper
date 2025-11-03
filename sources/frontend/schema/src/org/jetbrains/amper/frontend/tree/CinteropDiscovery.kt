package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.contexts.DefaultCtxs
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.frontend.types.SchemaType
import java.io.IOException
import kotlin.io.path.relativeTo

context(buildCtx: BuildCtx)
internal fun MapLikeValue<*>.discoverCinteropDefs(): Merged {
    val discoveryAppender = CinteropDiscoveryAppender()
    val withDiscoveredDefs = discoveryAppender.transform(this) as MapLikeValue<*>
    return buildCtx.treeMerger.mergeTrees(listOf(withDiscoveredDefs))
}

private class CinteropDiscoveryAppender() : TreeTransformer<TreeState>() {

    override fun visitMapValue(value: MapLikeValue<TreeState>): TransformResult<MapLikeValue<TreeState>> {
        val transformResult = super.visitMapValue(value)
        val transformedValue = when (transformResult) {
            is Changed -> transformResult.value
            NotChanged -> value
            Removed -> return transformResult
        }

        val moduleDir = transformedValue.trace.extractPsiElementOrNull()?.containingFile?.virtualFile?.parent ?: return transformResult
        val cinteropDir = moduleDir.findChild("resources")?.findChild("cinterop")
        if (cinteropDir == null || !cinteropDir.exists() || !cinteropDir.isDirectory) {
            return transformResult
        }

        val discoveredDefFiles = try {
            cinteropDir.children.filter { it.name.endsWith(".def") }
        } catch (e: IOException) {
            return transformResult
        }

        if (discoveredDefFiles.isEmpty()) {
            return transformResult
        }

        val modulePath = moduleDir.toNioPath()
        val discoveredDefPaths = discoveredDefFiles.map {
            it.toNioPath().relativeTo(modulePath).toString().replace('\\', '/')
        }

        val cinteropProperty = transformedValue.children.find { it.key == "cinterop" }
        val cinteropNode = cinteropProperty?.value as? MapLikeValue<*>
        val defsProperty = cinteropNode?.children?.find { it.key == "defs" }
        val defsNode = defsProperty?.value as? ListValue<*>
        val existingDefValues = defsNode?.children
            ?.mapNotNull { it.asScalar?.value?.toString() }
            .orEmpty()

        val allDefPaths = (existingDefValues + discoveredDefPaths).distinct()

        val newDefNodes = allDefPaths.map { path ->
            ScalarValue<TreeState>(path, SchemaType.StringType, DefaultTrace, DefaultCtxs)
        }

        val newDefsList = ListValue(newDefNodes, SchemaType.ListType(SchemaType.StringType), DefaultTrace, DefaultCtxs)
        val newDefsMapProperty = MapLikeValue.Property("defs", DefaultTrace, newDefsList, defsProperty?.pType)

        val otherCinteropProperties = cinteropNode?.children?.filter { it.key != "defs" }.orEmpty()
        val newCinteropChildren = otherCinteropProperties + newDefsMapProperty

        val newCinteropNode = (cinteropNode ?: Owned(emptyList(), SchemaType.MapType(SchemaType.StringType), DefaultTrace, DefaultCtxs))
            .copy(children = newCinteropChildren)
        val newCinteropMapProperty = MapLikeValue.Property("cinterop", DefaultTrace, newCinteropNode, cinteropProperty?.pType)

        val finalChildren = transformedValue.children.filter { it.key != "cinterop" } + newCinteropMapProperty

        return Changed(transformedValue.copy(children = finalChildren))
    }
}