package org.jetbrains.deft.ide.jps

import com.intellij.configurationStore.serialize
import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RepositoryLibraryType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.importing.AbstractOpenProjectProvider
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.externalSystem.service.project.trusted.ExternalSystemTrustedProjectDialog
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.roots.JavadocOrderRootType
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.LanguageLevelProjectExtensionImpl
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.workspaceModel.jps.JpsImportedEntitySource
import com.intellij.util.DisposeAwareRunnable
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.LegacyBridgeJpsEntitySourceFactory
import com.intellij.workspaceModel.ide.toVirtualFileUrl
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.deft.ide.externalTool.DeftExternalSystemProjectAware
import org.jetbrains.deft.ide.isPot
import org.jetbrains.deft.proto.frontend.Fragment
import org.jetbrains.deft.proto.frontend.PotatoModuleFileSource
import org.jetbrains.deft.proto.frontend.YamlModelInit
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer

@Suppress("UnstableApiUsage")
class DefOpenProjectProvider : AbstractOpenProjectProvider() {

    companion object {
        private val JAVADOC_TYPE: LibraryRootTypeId = LibraryRootTypeId("JAVADOC")
        private const val DEFT_ID = "Deft"
        private val externalSource = ExternalProjectSystemRegistry.getInstance().getSourceById(DEFT_ID)
        private const val SETTINGS_GRADLE_KTS = "settings.gradle.kts"
    }

    override val systemId: ProjectSystemId
        get() = DeftExternalSystemProjectAware.systemId

    override fun canOpenProject(file: VirtualFile): Boolean =
        super.canOpenProject(file) && if (file.isDirectory) file.findChild(SETTINGS_GRADLE_KTS) == null else true

    public override fun isProjectFile(file: VirtualFile): Boolean = file.isPot()

    override fun linkToExistingProject(projectFile: VirtualFile, project: Project) {
        val projectRoot = getProjectDirectory(projectFile)
        if (isNotTrustedProject(project, projectRoot)) {
            return
        }

        enableExternalStorage(project)
        configureJdk(project)

        val builder = MutableEntityStorage.create()
        val virtualFileUrlManager = VirtualFileUrlManager.getInstance(project)
        val virtualFileManager = VirtualFileManager.getInstance()

        val model = YamlModelInit().getModel(projectRoot.toNioPath())
        for (module in model.modules) {
            val source = module.source as? PotatoModuleFileSource ?: continue
            val baseDir = virtualFileManager.findFileByNioPath(source.buildDir) ?: continue
            val baseModuleDir = baseDir.toVirtualFileUrl(virtualFileUrlManager)

            val dependencies = module.fragments.mapNotNull { fragment ->
                addModule(builder, virtualFileManager, virtualFileUrlManager, project, fragment)
            }.map {
                ModuleDependencyItem.Exportable.ModuleDependency(
                    it.symbolicId,
                    false,
                    ModuleDependencyItem.DependencyScope.COMPILE,
                    false
                )
            }.toMutableList<ModuleDependencyItem>()
            dependencies.add(0, ModuleDependencyItem.ModuleSourceDependency)
            dependencies.add(0, ModuleDependencyItem.InheritedSdkDependency)

            val moduleEntity = addModuleEntity(
                project,
                baseModuleDir,
                builder,
                module.userReadableName,
                dependencies
            )
            addContentRoot(builder, baseModuleDir, moduleEntity, listOf())
        }

        ApplicationManager.getApplication().invokeAndWait(
            DisposeAwareRunnable.create({
                ApplicationManager.getApplication().runWriteAction {
                    val workspaceModel = WorkspaceModel.getInstance(project)
                    workspaceModel.updateProjectModel("Deft update project model") { storage ->
                        storage.replaceBySource({
                            (it as? JpsImportedEntitySource)?.externalSystemId == DEFT_ID
                        }, builder)
                    }
                }
            }, project)
        )
    }

    private fun addModule(
        builder: MutableEntityStorage,
        virtualFileManager: VirtualFileManager,
        virtualFileUrlManager: VirtualFileUrlManager,
        project: Project,
        fragment: Fragment
    ): ModuleEntity? {
        val kotlinDependency = addDependency(
            builder,
            project,
            virtualFileUrlManager,
            "KotlinJavaRuntime",
            ModuleDependencyItem.DependencyScope.COMPILE,
            RepositoryLibraryProperties(
                /* groupId = */ "org.jetbrains.kotlin",
                /* artifactId = */ "kotlin-stdlib",
                /* version = */ "1.8.20",
                /* includeTransitiveDependencies = */ true,
                /* excludedDependencies = */ emptyList()
            )
        )

        val dependencies = mutableListOf(
            ModuleDependencyItem.InheritedSdkDependency,
            ModuleDependencyItem.ModuleSourceDependency,
            kotlinDependency,
        )

        if (fragment.isTest) {
            val kotlinTestDependency = addDependency(
                builder,
                project,
                virtualFileUrlManager,
                "KotlinTest",
                ModuleDependencyItem.DependencyScope.TEST,
                RepositoryLibraryProperties(
                    /* groupId = */ "org.jetbrains.kotlin",
                    /* artifactId = */ "kotlin-test-junit5",
                    /* version = */ "1.8.20",
                    /* includeTransitiveDependencies = */ true,
                    /* excludedDependencies = */ emptyList()
                )
            )
            dependencies.add(kotlinTestDependency)
        }

        val projectFile = virtualFileManager.findFileByNioPath(fragment.src ?: return null) ?: return null
        val baseModuleDir = projectFile.toVirtualFileUrl(virtualFileUrlManager)
        val moduleEntity = addModuleEntity(project, baseModuleDir, builder, fragment.name, dependencies)

        val buildUrl = baseModuleDir.append("build")
        val classesUrl = buildUrl.append("classes")
        configureCompilerOutput(builder, moduleEntity, classesUrl)

        val contentRootEntity = exclude(builder, buildUrl, moduleEntity, baseModuleDir)
        val rootType = when {
            fragment.isTest -> JpsModuleRootModelSerializer.JAVA_TEST_ROOT_TYPE_ID
            else -> JpsModuleRootModelSerializer.JAVA_SOURCE_ROOT_TYPE_ID
        }
        addSourceRoot(builder, contentRootEntity, rootType, baseModuleDir)

        return moduleEntity
    }

    private fun addModuleEntity(
        project: Project,
        baseModuleDir: VirtualFileUrl,
        builder: MutableEntityStorage,
        moduleName: String,
        dependencies: List<ModuleDependencyItem>
    ): ModuleEntity {
        val moduleSource = LegacyBridgeJpsEntitySourceFactory.createEntitySourceForModule(
            project, baseModuleDir, externalSource
        )
        val moduleEntity = builder addEntity ModuleEntity(moduleName, dependencies, moduleSource) {
            this.type = ModuleTypeId.JAVA_MODULE
        }
        return moduleEntity
    }

    private fun isNotTrustedProject(project: Project, projectRoot: VirtualFile) =
        !ExternalSystemTrustedProjectDialog.confirmLinkingUntrustedProject(project, systemId, projectRoot.toNioPath())

    private fun enableExternalStorage(project: Project) {
        if (!project.isExternalStorageEnabled) {
            ExternalProjectsManagerImpl.getInstance(project).setStoreExternally(true)
        }
    }

    private fun configureJdk(project: Project) {
        val jdk = ProjectJdkTable.getInstance().findJdk("17") ?: ProjectJdkTable.getInstance().findJdk("corretto-17")
        if (jdk != null) {
            ApplicationManager.getApplication().runWriteAction {
                ProjectRootManager.getInstance(project).projectSdk = jdk
                LanguageLevelProjectExtensionImpl.getInstanceImpl(project).default = true
            }
        }
    }

    private fun addDependency(
        builder: MutableEntityStorage,
        project: Project,
        virtualFileUrlManager: VirtualFileUrlManager,
        name: String,
        dependencyScope: ModuleDependencyItem.DependencyScope,
        properties: RepositoryLibraryProperties
    ): ModuleDependencyItem.Exportable.LibraryDependency {
        val libraryId = LibraryId(name, LibraryTableId.ProjectLibraryTableId)
        if (libraryId !in builder) {
            val libraryRootsProvider = {
                JarRepositoryManager.loadDependenciesModal(
                    /* project = */ project,
                    /* libraryProps = */ properties,
                    /* loadSources = */ true,
                    /* loadJavadoc = */ true,
                    /* copyTo = */ null,
                    /* repositories = */ null
                ).map {
                    val type = when (it.type) {
                        OrderRootType.CLASSES -> LibraryRootTypeId.COMPILED
                        OrderRootType.SOURCES -> LibraryRootTypeId.SOURCES
                        is JavadocOrderRootType -> JAVADOC_TYPE
                        else -> throw IllegalStateException("Unexpected order root type")
                    }
                    LibraryRoot(it.file.toVirtualFileUrl(virtualFileUrlManager), type)
                }
            }
            val librarySource = LegacyBridgeJpsEntitySourceFactory.createEntitySourceForProjectLibrary(
                project = project,
                externalSource = externalSource
            )
            val libraryEntity = builder addEntity LibraryEntity(
                name = libraryId.name,
                tableId = libraryId.tableId,
                roots = libraryRootsProvider(),
                entitySource = librarySource
            )
            serialize(properties)?.let { libPropertiesElement ->
                val libraryKind = RepositoryLibraryType.REPOSITORY_LIBRARY_KIND
                libPropertiesElement.name = JpsLibraryTableSerializer.PROPERTIES_TAG
                val xmlTag = JDOMUtil.writeElement(libPropertiesElement)
                builder addEntity LibraryPropertiesEntity(libraryKind.kindId, libraryEntity.entitySource) {
                    library = libraryEntity
                    propertiesXmlTag = xmlTag
                }
            }
        }
        return ModuleDependencyItem.Exportable.LibraryDependency(
            library = libraryId,
            exported = false,
            scope = dependencyScope
        )
    }

    private fun exclude(
        builder: MutableEntityStorage,
        buildUrl: VirtualFileUrl,
        moduleEntity: ModuleEntity,
        baseModuleDir: VirtualFileUrl
    ): ContentRootEntity {
        val excludeUrlEntity = builder addEntity ExcludeUrlEntity(buildUrl, moduleEntity.entitySource)
        return addContentRoot(builder, baseModuleDir, moduleEntity, listOf(excludeUrlEntity))
    }

    private fun addContentRoot(
        builder: MutableEntityStorage,
        baseModuleDir: VirtualFileUrl,
        moduleEntity: ModuleEntity,
        excludes: List<ExcludeUrlEntity>
    ): ContentRootEntity {
        val contentRootEntity = builder addEntity ContentRootEntity(
            url = baseModuleDir,
            excludedPatterns = emptyList(),
            entitySource = moduleEntity.entitySource
        ) {
            excludedUrls = excludes
            module = moduleEntity
        }
        return contentRootEntity
    }

    private fun configureCompilerOutput(
        builder: MutableEntityStorage,
        moduleEntity: ModuleEntity,
        classesUrl: VirtualFileUrl?
    ) {
        builder addEntity JavaModuleSettingsEntity(
            inheritedCompilerOutput = false,
            excludeOutput = true,
            entitySource = moduleEntity.entitySource
        ) {
            module = moduleEntity
            compilerOutput = classesUrl
            compilerOutputForTests = classesUrl
            languageLevelId = null
        }
    }

    private fun addSourceRoot(
        builder: MutableEntityStorage,
        contentRootEntity: ContentRootEntity,
        rootType: String,
        url: VirtualFileUrl
    ) {
        val sourceRootEntity = builder addEntity SourceRootEntity(
            url, rootType,
            contentRootEntity.entitySource
        ) {
            this.contentRoot = contentRootEntity
        }
        builder addEntity JavaSourceRootPropertiesEntity(false, "", sourceRootEntity.entitySource) {
            this.sourceRoot = sourceRootEntity
        }
    }
}
