package org.jetbrains.deft.ide

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.findFileOrDirectory
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ArrayUtilRt
import org.jetbrains.deft.ide.DeftPotReferenceManager.PotFilter

internal class DeftPotReference(element: PsiElement, private val template: Boolean) :
    PsiReferenceBase.Poly<PsiElement>(element, false), ResolvingHint {
    private val potFilter = if (template) PotFilter.TEMPLATE else PotFilter.NON_TEMPLATE

    override fun canResolveTo(elementClass: Class<out PsiElement>?): Boolean = elementClass == PsiFile::class.java

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val referenceManager = DeftPotReferenceManager.getInstance(element.project)
        val currentPotParent = element.containingFile?.virtualFile?.parent
        val referencePath = currentPotParent?.findFileOrDirectory(element.text) ?: return ResolveResult.EMPTY_ARRAY

        val files = referenceManager.findPotFiles(potFilter).filter {
            if (template) it.virtualFile == referencePath else it.parent?.virtualFile == referencePath
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
            val icon = AllIcons.General.Gear
            val isTemplate = file.isPotTemplate()
            val filePath = if (isTemplate) file.virtualFile else file.parent?.virtualFile
            filePath ?: return@processPotFiles true

            val potRelativePath = (VfsUtilCore.getRelativeLocation(filePath, projectDir)
                ?: filePath.presentableUrl).takeIf { it.isNotBlank() } ?: "<root Pot>"
            val potName = potRelativePath.stripTemplateSuffix()

            val relativePath = VfsUtilCore.findRelativePath(originalFile, filePath, VfsUtilCore.VFS_SEPARATOR_CHAR)
                ?.takeIf { it.isNotBlank() } ?: return@processPotFiles true
            variants.add(
                LookupElementBuilder
                    .create(file, relativePath)
                    .withPresentableText(potName)
                    .withIcon(icon)
            )
            true
        }
        return variants.toTypedArray()
    }
}
