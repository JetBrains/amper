import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilder


data class AndroidBuildResultImpl(override val paths: List<String>) : AndroidBuildResult

class ApkToolingModelBuilder : ToolingModelBuilder {
    override fun canBuild(modelName: String): Boolean = AndroidBuildResult::class.java.name == modelName

    override fun buildAll(modelName: String, project: Project): AndroidBuildResult {
        val stack = ArrayDeque<Project>()
        stack.add(project)
        val alreadyTraversed = mutableSetOf<Project>()

        val paths = buildList {
            while (stack.isNotEmpty()) {
                val p = stack.removeFirst()

                // todo: find apk or aar depending on app type
                add(p.layout.buildDirectory.get().asFile.toPath().toAbsolutePath().toString())

                for (subproject in p.subprojects) {
                    if (subproject !in alreadyTraversed) {
                        stack.add(subproject)
                        alreadyTraversed.add(subproject)
                    }
                }
            }
        }

        return AndroidBuildResultImpl(paths)
    }
}