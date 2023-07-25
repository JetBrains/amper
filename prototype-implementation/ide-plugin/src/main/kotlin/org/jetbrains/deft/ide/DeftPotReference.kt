package org.jetbrains.deft.ide

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.findFileOrDirectory
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ArrayUtilRt
import org.jetbrains.deft.ide.DeftPotReferenceManager.PotFilter
import java.nio.file.InvalidPathException

internal class DeftPotReference(element: PsiElement, private val template: Boolean) :
    PsiReferenceBase.Poly<PsiElement>(element, false), ResolvingHint {
    private val potFilter = if (template) PotFilter.TEMPLATE else PotFilter.NON_TEMPLATE

    override fun canResolveTo(elementClass: Class<out PsiElement>?): Boolean = elementClass == PsiFile::class.java

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val referenceManager = DeftPotReferenceManager.getInstance(element.project)
        val currentPotParent = element.containingFile?.virtualFile?.parent
        val potPath = element.text?.takeIf { template || it.startsWith(".") } ?: return ResolveResult.EMPTY_ARRAY
        val referenceFile = try {
            currentPotParent?.findFileOrDirectory(potPath)
        } catch (e: InvalidPathException) {
            null
        } ?: return ResolveResult.EMPTY_ARRAY

        val files = referenceManager.findPotFiles(potFilter).filter {
            if (template) it.virtualFile == referenceFile else it.parent?.virtualFile == referenceFile
        }
        return PsiElementResolveResult.createResults(files)
    }

    override fun getVariants(): Array<Any> {
        val project = element.project
        val originalFile =
            element.containingFile.originalFile.virtualFile?.parent ?: return ArrayUtilRt.EMPTY_OBJECT_ARRAY
        val projectDir = project.guessProjectDir() ?: return ArrayUtilRt.EMPTY_OBJECT_ARRAY

        val referenceManager = DeftPotReferenceManager.getInstance(project)
        val variants = mutableListOf<LookupElement>()

        referenceManager.processPotFiles(GlobalSearchScope.projectScope(project), potFilter) { file ->
            val isTemplate = file.isPotTemplate()
            val filePath = if (isTemplate) file.virtualFile else file.parent?.virtualFile
            filePath ?: return@processPotFiles true

            val potRelativePath = (VfsUtilCore.getRelativeLocation(filePath, projectDir)
                ?: filePath.presentableUrl).takeIf { it.isNotBlank() } ?: "<root Pot>"
            val potName = potRelativePath.stripTemplateSuffix()

            val relativePath = VfsUtilCore.findRelativePath(originalFile, filePath, VfsUtilCore.VFS_SEPARATOR_CHAR)
                ?.takeIf { it.isNotBlank() }
                ?.run { if (startsWith(".")) this else ".${VfsUtilCore.VFS_SEPARATOR_CHAR}$this" }
                ?: return@processPotFiles true
            variants.add(
                LookupElementBuilder
                    .create(file, relativePath)
                    .withPresentableText(potName)
                    .withIcon(if (template) DeftIcons.TemplateFileType else DeftIcons.FileType)
            )
            true
        }
        return variants.toTypedArray()
    }
}
