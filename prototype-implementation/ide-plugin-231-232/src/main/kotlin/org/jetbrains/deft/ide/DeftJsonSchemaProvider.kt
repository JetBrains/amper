package org.jetbrains.deft.ide

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import org.jetbrains.annotations.Nls

private const val COMMON_POT_SCHEMA_FILE = "/schema/CommonDeftSchema.json"
private const val POT_SCHEMA_FILE = "/schema/DeftSchema.json"
private const val POT_TEMPLATE_SCHEMA_FILE = "/schema/DeftTemplateSchema.json"

internal class DeftJsonSchemaProviderFactory : JsonSchemaProviderFactory, DumbAware {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> = listOf(
        DeftCommonJsonSchemaProvider(),
        DeftPotJsonSchemaProvider(),
        DeftPotTemplateJsonSchemaProvider(),
    )

    /**
     * Need to be provided so that the plugin resolves the references to the common schema.
     */
    private class DeftCommonJsonSchemaProvider : JsonSchemaFileProvider {
        override fun isAvailable(file: VirtualFile): Boolean = false

        override fun getName(): String = DeftBundle.message("Deft.CommonJsonSchemaProvider.name")

        override fun isUserVisible(): Boolean = false

        override fun getSchemaFile(): VirtualFile? = JsonSchemaProviderFactory.getResourceFile(this::class.java, COMMON_POT_SCHEMA_FILE)

        override fun getSchemaType(): SchemaType = SchemaType.schema
    }

    private class DeftPotJsonSchemaProvider : JsonSchemaFileProvider {
        override fun isAvailable(file: VirtualFile): Boolean = file.isPot()

        override fun getName(): @Nls String = DeftBundle.message("Deft.PotJsonSchemaProvider.name")

        override fun getSchemaFile(): VirtualFile? = JsonSchemaProviderFactory.getResourceFile(this::class.java, POT_SCHEMA_FILE)

        override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema
    }

    private class DeftPotTemplateJsonSchemaProvider : JsonSchemaFileProvider {
        override fun isAvailable(file: VirtualFile): Boolean = file.isPotTemplate()

        override fun getName(): @Nls String = DeftBundle.message("Deft.PotTemplateJsonSchemaProvider.name")

        override fun getSchemaFile(): VirtualFile? = JsonSchemaProviderFactory.getResourceFile(this::class.java, POT_TEMPLATE_SCHEMA_FILE)

        override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema
    }
}
