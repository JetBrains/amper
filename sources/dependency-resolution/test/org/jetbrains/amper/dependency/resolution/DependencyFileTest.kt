package org.jetbrains.amper.dependency.resolution

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertTrue

class DependencyFileTest {

    @field:TempDir
    lateinit var temp: File

    @Test
    fun `kotlin-test-1_9_20 module hash`() {
        val settings = Context.build {
            cache = listOf(GradleCacheDirectory(temp.toPath()))
        }.settings
        val dependency = MavenDependency(settings.fileCache, "org.jetbrains.kotlin", "kotlin-test", "1.9.20")
        val extension = "module"
        val name = getName(dependency, extension)
        val file = File(
            temp,
            "${dependency.group}/${dependency.module}/${dependency.version}/3bf4b49eb37b4aca302f99bd99769f6e310bdb2/$name"
        )
        assertTrue(file.parentFile.mkdirs(), "Unable to create directories")
        file.writeText(Path.of("testData/metadata/json/$name").readText())

        val dependencyFile = DependencyFile(
            settings.fileCache,
            dependency,
            extension
        )
        val downloaded = dependencyFile.isDownloaded(ResolutionLevel.PARTIAL, settings)
        assertTrue(dependency.messages.isEmpty(), "There must be no messages: ${dependency.messages}")
        assertTrue(downloaded, "File must be downloaded as it was created above")
    }
}
