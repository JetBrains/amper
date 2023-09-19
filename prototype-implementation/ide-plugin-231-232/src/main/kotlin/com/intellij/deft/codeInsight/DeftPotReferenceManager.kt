package com.intellij.deft.codeInsight

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.containers.ConcurrentFactoryMap
import org.jetbrains.yaml.YAMLFileType
import java.util.concurrent.ConcurrentMap

@Service(Service.Level.PROJECT)
internal class DeftPotReferenceManager(private val project: Project) {
  fun findPotFiles(potFilter: PotFilter): List<PsiFile> {
    val filterMap: ConcurrentMap<PotFilter, List<PsiFile>> =
      CachedValuesManager.getManager(project).getCachedValue(project) {
        val map = ConcurrentFactoryMap.createMap<PotFilter, List<PsiFile>> {
          findPotFiles(GlobalSearchScope.projectScope(project), it)
        }
        CachedValueProvider.Result.create(map, PsiModificationTracker.MODIFICATION_COUNT)
      }
    return filterMap[potFilter] ?: emptyList()
  }

  fun findPotFiles(scope: GlobalSearchScope, potFilter: PotFilter): List<PsiFile> = buildList {
    processPotFiles(scope, potFilter) { file ->
      add(file)
      true
    }
  }

  fun processPotFiles(searchScope: GlobalSearchScope, potFilter: PotFilter, processor: (PsiFile) -> Boolean) {
    for (file in FileTypeIndex.getFiles(YAMLFileType.YML, searchScope)) {
      if (!processFile(file, potFilter, processor)) return
    }
  }

  private fun processFile(file: VirtualFile, potFilter: PotFilter, processor: (PsiFile) -> Boolean): Boolean {
    when (potFilter) {
      PotFilter.ALL -> if (!file.isDeftFile()) return true
      PotFilter.TEMPLATE -> if (!file.isPotTemplate()) return true
      PotFilter.NON_TEMPLATE -> if (!file.isPot()) return true
    }

    val psiFile = PsiManager.getInstance(project).findFile(file) ?: return true
    return processor(psiFile)
  }

  enum class PotFilter {
    ALL,
    TEMPLATE,
    NON_TEMPLATE,
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): DeftPotReferenceManager = project.service()
  }
}
