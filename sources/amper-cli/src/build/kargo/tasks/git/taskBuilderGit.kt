package build.kargo.tasks.git

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.tasks.CommonTaskType
import org.jetbrains.amper.tasks.ModuleDependencies
import org.jetbrains.amper.tasks.PlatformTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.native.NativeTaskType
import org.jetbrains.amper.util.BuildType

// Git Sources task type
internal enum class GitSourcesTaskType(override val prefix: String) : PlatformTaskType {
    ProcessGitSources("processGitSources"),
}

fun ProjectTasksBuilder.setupGitTasks() {
    allModules()
        .alsoPlatforms()
        // One git-sources task per (module × platform) — shared between test and non-test.
        // This avoids fetching/building the same git dependency twice per build.
        .withEach {
            val gitSourcesTaskName = GitSourcesTaskType.ProcessGitSources.getTaskName(module, platform)
            tasks.registerTask(
                ResolveGitSourcesDependenciesTask(
                    module = module,
                    taskName = gitSourcesTaskName,
                    targetPlatforms = listOf(platform)
                )
            )

            // Wire git sources as dependency of compile tasks (both test and non-test)
            if (platform.isDescendantOf(Platform.NATIVE)) {
                for (buildType in BuildType.entries) {
                    for (isTest in listOf(false, true)) {
                        tasks.registerDependency(
                            NativeTaskType.CompileKLib.getTaskName(module, platform, isTest, buildType),
                            gitSourcesTaskName
                        )
                        tasks.registerDependency(
                            NativeTaskType.Link.getTaskName(module, platform, isTest, buildType),
                            gitSourcesTaskName
                        )
                    }
                }
            } else {
                for (isTest in listOf(false, true)) {
                    tasks.registerDependency(
                        CommonTaskType.Compile.getTaskName(module, platform, isTest),
                        gitSourcesTaskName
                    )
                }
            }
        }
}
