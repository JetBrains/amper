import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.name
import kotlin.io.path.walk

val versions = mapOf(
    "org.jetbrains.deft.proto.settings.plugin:gradle-integration" to "1.2.6",
    "org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin" to "1.9.20-dev-6845"
)

updateFiles(Path.of("examples"), versions)
updateFiles(Path.of("gradle-integration/settings.gradle.kts"), versions)

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
fun updateFiles(path: Path, versions: Map<String, String>) {
    path.walk(PathWalkOption.BREADTH_FIRST).forEach {
        if (it.name == "settings.gradle.kts" || it.name.endsWith(".yaml")) {
            updateFile(it, versions)
        }
    }
}

fun updateFile(file: Path, versions: Map<String, String>) {
    var original = Files.readString(file)
    var updated = original
    
    versions.forEach { dependency, version ->
        val regex = Regex("(.*)(${Regex.escape(dependency)}:)([\\w\\.\\-]*)(.*)")
        updated = updated.replace(regex, "$1$2$version$4")
    }

    if (original != updated) {
        Files.writeString(file, updated)
        println("Updated $file")
    }
}