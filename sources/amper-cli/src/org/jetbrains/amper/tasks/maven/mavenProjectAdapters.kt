/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.maven

import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.factory.ArtifactFactory
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.artifact.resolver.filter.ArtifactFilter
import org.apache.maven.model.Build
import org.apache.maven.model.CiManagement
import org.apache.maven.model.Contributor
import org.apache.maven.model.Dependency
import org.apache.maven.model.DependencyManagement
import org.apache.maven.model.Developer
import org.apache.maven.model.DistributionManagement
import org.apache.maven.model.Extension
import org.apache.maven.model.IssueManagement
import org.apache.maven.model.License
import org.apache.maven.model.MailingList
import org.apache.maven.model.Model
import org.apache.maven.model.Organization
import org.apache.maven.model.Plugin
import org.apache.maven.model.PluginManagement
import org.apache.maven.model.Prerequisites
import org.apache.maven.model.Profile
import org.apache.maven.model.ReportPlugin
import org.apache.maven.model.Reporting
import org.apache.maven.model.Repository
import org.apache.maven.model.Resource
import org.apache.maven.model.Scm
import org.apache.maven.project.MavenProject
import org.apache.maven.project.ProjectBuildingRequest
import org.codehaus.plexus.classworlds.realm.ClassRealm
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.eclipse.aether.graph.DependencyFilter
import org.eclipse.aether.repository.RemoteRepository
import java.io.File
import java.io.Writer
import java.util.*

/**
 * Best attempt of delegating [MavenProject] methods.
 */
@Suppress("OVERRIDE_DEPRECATION")
open class DelegatedMavenProject(private val delegate: MavenProject) : MavenProject() {
    // This method is commented out since it is used in the [MavenProject] constructor.
//    override fun setModel(model: Model?) { delegate.model = model }

    override fun getParentFile(): File? = delegate.parentFile
    override fun setParentFile(parentFile: File?) { delegate.parentFile = parentFile }
    override fun getArtifact(): Artifact? = delegate.artifact
    override fun setArtifact(artifact: Artifact?) { delegate.artifact = artifact }
    override fun getModel(): Model = delegate.model
    override fun getParent(): MavenProject? = delegate.parent
    override fun setParent(parent: MavenProject?) { delegate.parent = parent }
    override fun hasParent(): Boolean = delegate.hasParent()
    override fun getFile(): File? = delegate.file
    override fun setFile(file: File?) { delegate.file = file }
    override fun setPomFile(file: File?) { delegate.setPomFile(file) }
    override fun getBasedir(): File? = delegate.basedir
    override fun setDependencies(dependencies: List<Dependency?>?) { delegate.dependencies = dependencies }
    override fun getDependencies(): List<Dependency?>? = delegate.dependencies
    override fun getDependencyManagement(): DependencyManagement? = delegate.dependencyManagement
    override fun addCompileSourceRoot(path: String?) = delegate.addCompileSourceRoot(path)
    override fun addTestCompileSourceRoot(path: String?) = delegate.addTestCompileSourceRoot(path)
    override fun getCompileSourceRoots(): List<String?>? = delegate.compileSourceRoots
    override fun getTestCompileSourceRoots(): List<String?>? = delegate.testCompileSourceRoots
    override fun getCompileClasspathElements(): List<String?>? = delegate.compileClasspathElements
    override fun getTestClasspathElements(): List<String?>? = delegate.testClasspathElements
    override fun getRuntimeClasspathElements(): List<String?>? = delegate.runtimeClasspathElements
    override fun setModelVersion(pomVersion: String?) { delegate.modelVersion = pomVersion }
    override fun getModelVersion(): String? = delegate.modelVersion
    override fun getId(): String? = delegate.id
    override fun setGroupId(groupId: String?) { delegate.groupId = groupId }
    override fun getGroupId(): String? = delegate.groupId
    override fun setArtifactId(artifactId: String?) { delegate.artifactId = artifactId }
    override fun getArtifactId(): String? = delegate.artifactId
    override fun setName(name: String?) { delegate.name = name }
    override fun getName(): String? = delegate.name
    override fun setVersion(version: String?) { delegate.version = version }
    override fun getVersion(): String? = delegate.version
    override fun getPackaging(): String? = delegate.packaging
    override fun setPackaging(packaging: String?) { delegate.packaging = packaging }
    override fun setInceptionYear(inceptionYear: String?) { delegate.inceptionYear = inceptionYear }
    override fun getInceptionYear(): String? = delegate.inceptionYear
    override fun setUrl(url: String?) { delegate.url = url }
    override fun getUrl(): String? = delegate.url
    override fun getPrerequisites(): Prerequisites? = delegate.prerequisites
    override fun setIssueManagement(issueManagement: IssueManagement?) { delegate.issueManagement = issueManagement }
    override fun getCiManagement(): CiManagement? = delegate.ciManagement
    override fun setCiManagement(ciManagement: CiManagement?) { delegate.ciManagement = ciManagement }
    override fun getIssueManagement(): IssueManagement? = delegate.issueManagement
    override fun setDistributionManagement(distributionManagement: DistributionManagement?) { delegate.distributionManagement = distributionManagement }
    override fun getDistributionManagement(): DistributionManagement? = delegate.distributionManagement
    override fun setDescription(description: String?) { delegate.description = description }
    override fun getDescription(): String? = delegate.description
    override fun setOrganization(organization: Organization?) { delegate.organization = organization }
    override fun getOrganization(): Organization? = delegate.organization
    override fun setScm(scm: Scm?) { delegate.scm = scm }
    override fun getScm(): Scm? = delegate.scm
    override fun setMailingLists(mailingLists: List<MailingList?>?) { delegate.mailingLists = mailingLists }
    override fun getMailingLists(): List<MailingList?>? = delegate.mailingLists
    override fun addMailingList(mailingList: MailingList?) = delegate.addMailingList(mailingList)
    override fun setDevelopers(developers: List<Developer?>?) { delegate.developers = developers }
    override fun getDevelopers(): List<Developer?>? = delegate.developers
    override fun addDeveloper(developer: Developer?) = delegate.addDeveloper(developer)
    override fun setContributors(contributors: List<Contributor?>?) { delegate.contributors = contributors }
    override fun getContributors(): List<Contributor?>? = delegate.contributors
    override fun addContributor(contributor: Contributor?) = delegate.addContributor(contributor)
    override fun setBuild(build: Build) { delegate.build = build }
    override fun getBuild(): Build = delegate.build
    override fun getResources(): List<Resource?>? = delegate.resources
    override fun getTestResources(): List<Resource?>? = delegate.testResources
    override fun addResource(resource: Resource?) = delegate.addResource(resource)
    override fun addTestResource(testResource: Resource?) = delegate.addTestResource(testResource)
    override fun setLicenses(licenses: List<License?>?) { delegate.licenses = licenses }
    override fun getLicenses(): List<License?>? = delegate.licenses
    override fun addLicense(license: License?) = delegate.addLicense(license)
    override fun setArtifacts(artifacts: Set<Artifact?>?) { delegate.artifacts = artifacts }
    override fun getArtifacts(): Set<Artifact?>? = delegate.artifacts
    override fun getArtifactMap(): Map<String?, Artifact?>? = delegate.artifactMap
    override fun setPluginArtifacts(pluginArtifacts: Set<Artifact?>?) { delegate.pluginArtifacts = pluginArtifacts }
    override fun getPluginArtifacts(): Set<Artifact?>? = delegate.pluginArtifacts
    override fun getPluginArtifactMap(): Map<String?, Artifact?>? = delegate.pluginArtifactMap
    override fun setParentArtifact(parentArtifact: Artifact?) { delegate.parentArtifact = parentArtifact }
    override fun getParentArtifact(): Artifact? = delegate.parentArtifact
    override fun getRepositories(): List<Repository?>? = delegate.repositories
    override fun getBuildPlugins(): List<Plugin?>? = delegate.buildPlugins
    override fun getModules(): List<String?>? = delegate.modules
    override fun getPluginManagement(): PluginManagement? = delegate.pluginManagement
    override fun setRemoteArtifactRepositories(remoteArtifactRepositories: List<ArtifactRepository?>?) { delegate.remoteArtifactRepositories = remoteArtifactRepositories }
    override fun getRemoteArtifactRepositories(): List<ArtifactRepository?>? = delegate.remoteArtifactRepositories
    override fun setPluginArtifactRepositories(pluginArtifactRepositories: List<ArtifactRepository?>?) { delegate.pluginArtifactRepositories = pluginArtifactRepositories }
    override fun getPluginArtifactRepositories(): List<ArtifactRepository?>? = delegate.pluginArtifactRepositories
    override fun getDistributionManagementArtifactRepository(): ArtifactRepository? = delegate.distributionManagementArtifactRepository
    override fun getPluginRepositories(): List<Repository?>? = delegate.pluginRepositories
    override fun getRemoteProjectRepositories(): List<RemoteRepository?>? = delegate.remoteProjectRepositories
    override fun getRemotePluginRepositories(): List<RemoteRepository?>? = delegate.remotePluginRepositories
    override fun setActiveProfiles(activeProfiles: List<Profile?>?) { delegate.activeProfiles = activeProfiles }
    override fun getActiveProfiles(): List<Profile?>? = delegate.activeProfiles
    override fun setInjectedProfileIds(source: String?, injectedProfileIds: List<String?>?) = delegate.setInjectedProfileIds(source, injectedProfileIds)
    override fun getInjectedProfileIds(): Map<String?, List<String?>?>? = delegate.injectedProfileIds
    override fun addAttachedArtifact(artifact: Artifact?) = delegate.addAttachedArtifact(artifact)
    override fun getAttachedArtifacts(): List<Artifact?>? = delegate.attachedArtifacts
    override fun getGoalConfiguration(pluginGroupId: String?, pluginArtifactId: String?, executionId: String?, goalId: String?): Xpp3Dom? = delegate.getGoalConfiguration(pluginGroupId, pluginArtifactId, executionId, goalId)
    override fun getExecutionProject(): MavenProject? = delegate.executionProject
    override fun setExecutionProject(executionProject: MavenProject?) { delegate.executionProject = executionProject }
    override fun getCollectedProjects(): List<MavenProject?>? = delegate.collectedProjects
    override fun setCollectedProjects(collectedProjects: List<MavenProject?>?) { delegate.collectedProjects = collectedProjects }
    override fun getDependencyArtifacts(): Set<Artifact?>? = delegate.dependencyArtifacts
    override fun setDependencyArtifacts(dependencyArtifacts: Set<Artifact?>?) { delegate.dependencyArtifacts = dependencyArtifacts }
    override fun setReleaseArtifactRepository(releaseArtifactRepository: ArtifactRepository?) { delegate.releaseArtifactRepository = releaseArtifactRepository }
    override fun setSnapshotArtifactRepository(snapshotArtifactRepository: ArtifactRepository?) { delegate.snapshotArtifactRepository = snapshotArtifactRepository }
    override fun setOriginalModel(originalModel: Model?) { delegate.originalModel = originalModel }
    override fun getOriginalModel(): Model? = delegate.originalModel
    override fun setManagedVersionMap(map: Map<String?, Artifact?>?) { delegate.managedVersionMap = map }
    override fun getManagedVersionMap(): Map<String?, Artifact?>? = delegate.managedVersionMap
    override fun getBuildExtensions(): List<Extension?>? = delegate.buildExtensions
    override fun addProjectReference(project: MavenProject?) = delegate.addProjectReference(project)
    override fun getProperties(): Properties = delegate.properties
    override fun getFilters(): List<String?>? = delegate.filters
    override fun getProjectReferences(): Map<String?, MavenProject?>? = delegate.projectReferences
    override fun isExecutionRoot(): Boolean = delegate.isExecutionRoot
    override fun setExecutionRoot(executionRoot: Boolean) { delegate.isExecutionRoot = executionRoot }
    override fun getDefaultGoal(): String? = delegate.defaultGoal
    override fun getPlugin(pluginKey: String?): Plugin? = delegate.getPlugin(pluginKey)
    override fun toString(): String = delegate.toString()
    override fun setAttachedArtifacts(attachedArtifacts: List<Artifact?>?) = super.setAttachedArtifacts(attachedArtifacts)
    override fun setCompileSourceRoots(compileSourceRoots: List<String?>?) = super.setCompileSourceRoots(compileSourceRoots)
    override fun setTestCompileSourceRoots(testCompileSourceRoots: List<String?>?) = super.setTestCompileSourceRoots(testCompileSourceRoots)
    override fun getReleaseArtifactRepository(): ArtifactRepository? = super.getReleaseArtifactRepository()
    override fun getSnapshotArtifactRepository(): ArtifactRepository? = super.getSnapshotArtifactRepository()
    override fun setContextValue(key: String?, value: Any?) = delegate.setContextValue(key, value)
    override fun getContextValue(key: String?): Any? = delegate.getContextValue(key)
    override fun setClassRealm(classRealm: ClassRealm?) { delegate.classRealm = classRealm }
    override fun getClassRealm(): ClassRealm? = delegate.classRealm
    override fun setExtensionDependencyFilter(extensionDependencyFilter: DependencyFilter?) { delegate.extensionDependencyFilter = extensionDependencyFilter }
    override fun getExtensionDependencyFilter(): DependencyFilter? = delegate.extensionDependencyFilter
    override fun setResolvedArtifacts(artifacts: Set<Artifact?>?) = delegate.setResolvedArtifacts(artifacts)
    override fun setArtifactFilter(artifactFilter: ArtifactFilter?) = delegate.setArtifactFilter(artifactFilter)
    override fun hasLifecyclePhase(phase: String?): Boolean = delegate.hasLifecyclePhase(phase)
    override fun addLifecyclePhase(lifecyclePhase: String?) = delegate.addLifecyclePhase(lifecyclePhase)
    override fun getModulePathAdjustment(moduleProject: MavenProject?): String? = delegate.getModulePathAdjustment(moduleProject)
    override fun createArtifacts(artifactFactory: ArtifactFactory?, inheritedScope: String?, filter: ArtifactFilter?): Set<Artifact?>? = delegate.createArtifacts(artifactFactory, inheritedScope, filter)
    override fun setScriptSourceRoots(scriptSourceRoots: List<String?>?) = super.setScriptSourceRoots(scriptSourceRoots)
    override fun addScriptSourceRoot(path: String?) = delegate.addScriptSourceRoot(path)
    override fun getScriptSourceRoots(): List<String?>? = delegate.scriptSourceRoots
    override fun getCompileArtifacts(): List<Artifact?>? = delegate.compileArtifacts
    override fun getCompileDependencies(): List<Dependency?>? = delegate.compileDependencies
    override fun getTestArtifacts(): List<Artifact?>? = delegate.testArtifacts
    override fun getTestDependencies(): List<Dependency?>? = delegate.testDependencies
    override fun getRuntimeDependencies(): List<Dependency?>? = delegate.runtimeDependencies
    override fun getRuntimeArtifacts(): List<Artifact?>? = delegate.runtimeArtifacts
    override fun getSystemClasspathElements(): List<String?>? = delegate.systemClasspathElements
    override fun getSystemArtifacts(): List<Artifact?>? = delegate.systemArtifacts
    override fun getSystemDependencies(): List<Dependency?>? = delegate.systemDependencies
    override fun setReporting(reporting: Reporting?) { delegate.reporting = reporting }
    override fun getReporting(): Reporting? = delegate.reporting
    override fun setReportArtifacts(reportArtifacts: Set<Artifact?>?) { delegate.reportArtifacts = reportArtifacts }
    override fun getReportArtifacts(): Set<Artifact?>? = delegate.reportArtifacts
    override fun getReportArtifactMap(): Map<String?, Artifact?>? = delegate.reportArtifactMap
    override fun setExtensionArtifacts(extensionArtifacts: Set<Artifact?>?) { delegate.extensionArtifacts = extensionArtifacts }
    override fun getExtensionArtifacts(): Set<Artifact?>? = delegate.extensionArtifacts
    override fun getExtensionArtifactMap(): Map<String?, Artifact?>? = delegate.extensionArtifactMap
    override fun getReportPlugins(): List<ReportPlugin?>? = delegate.reportPlugins
    override fun getReportConfiguration(pluginGroupId: String?, pluginArtifactId: String?, reportSetId: String?): Xpp3Dom? = delegate.getReportConfiguration(pluginGroupId, pluginArtifactId, reportSetId)
    override fun attachArtifact(type: String?, classifier: String?, file: File?) = delegate.attachArtifact(type, classifier, file)
    override fun writeModel(writer: Writer?) = delegate.writeModel(writer)
    override fun writeOriginalModel(writer: Writer?) = delegate.writeOriginalModel(writer)
    override fun replaceWithActiveArtifact(pluginArtifact: Artifact?): Artifact? = delegate.replaceWithActiveArtifact(pluginArtifact)
    override fun getProjectBuildingRequest(): ProjectBuildingRequest? = delegate.projectBuildingRequest
    override fun setProjectBuildingRequest(projectBuildingRequest: ProjectBuildingRequest?) { delegate.projectBuildingRequest = projectBuildingRequest }
}

class MockedMavenProject(other: MavenProject) : DelegatedMavenProject(other) {
    private val _newSourceRoots = mutableListOf<String>()

    /**
     * Source roots that were added during mojo execution.
     */
    val newSourceRoots: List<String> get() = _newSourceRoots

    private val _newTestSourceRoots = mutableListOf<String>()

    /**
     * Test source roots that were added during mojo execution.
     */
    val newTestSourceRoots: List<String> get() = _newTestSourceRoots

    override fun addCompileSourceRoot(path: String?) {
        super.addCompileSourceRoot(path)
        path?.let { _newSourceRoots.add(it) }
    }

    override fun addTestCompileSourceRoot(path: String?) {
        super.addTestCompileSourceRoot(path)
        path?.let { _newTestSourceRoots.add(it) }
    }
}
