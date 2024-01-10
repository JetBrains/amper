import kotlinx.serialization.Serializable
import java.nio.file.Path


@Serializable
data class ResolvedDependency(
    val group: String,
    val artifact: String,
    val version: String,
    @Serializable(with = PathAsStringSerializer::class)
    val path: Path
)

@Serializable
data class AndroidModuleData(
    val modulePath: String, // relative module path from root in Gradle format ":path:to:module"
    @Serializable(with = PathAsStringSerializer::class)
    val moduleClasses: Path? = null,
    val resolvedAndroidRuntimeDependencies: List<ResolvedDependency> = listOf()
)

@Serializable
data class AndroidBuildRequest(
    @Serializable(with = PathAsStringSerializer::class)
    val root: Path,
    val phase: Phase,
    val modules: Set<AndroidModuleData> = setOf(),
    val buildTypes: Set<BuildType> = setOf(BuildType.Debug),

    /**
     * Module name, if not set, all modules will be built
     */
    val targets: Set<String> = setOf(),
) {
    enum class BuildType(val value: String) {
        Debug("debug"),
        Release("release")
    }

    enum class Phase {
        /**
         * generate R class and other things which is needed for compilation
         */
        Prepare,

        /**
         * build APK
         */
        Build
    }
}

interface AndroidBuildResult

interface ApkPathAndroidBuildResult : AndroidBuildResult, java.io.Serializable {
    val paths: List<String>
}

interface RClassAndroidBuildResult : AndroidBuildResult, java.io.Serializable {
    val paths: List<String>
}