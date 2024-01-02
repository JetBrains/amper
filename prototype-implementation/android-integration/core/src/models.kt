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
        val modulePath: String, // relative module path from root in format "path/to/module"
        @Serializable(with = PathAsStringSerializer::class)
        val moduleJar: Path,
        val resolvedAndroidRuntimeDependencies: List<ResolvedDependency>
)

@Serializable
data class AndroidBuildRequest(
        @Serializable(with = PathAsStringSerializer::class)
        val root: Path,
        val modules: List<AndroidModuleData>,
        val buildType: BuildType,
        val phase: Phase,

        /**
         * Module name, if not set, all modules will be built
         */
        val target: Set<String>,
) {
    enum class BuildType(val value: String) {
        Debug("debug"),
        Release("release")
    }

    enum class Phase {
        /**
         * generate R class and other things which is needed for compilation
         */
        Prepare, // todo: use it

        /**
         * build APK
         */
        Build
    }
}

interface AndroidBuildResult

interface ApkPathAndroidBuildResult: AndroidBuildResult, java.io.Serializable {
    val paths: List<String>
}

interface RClassAndroidBuildResult: AndroidBuildResult, java.io.Serializable {
    val paths: List<String>
}