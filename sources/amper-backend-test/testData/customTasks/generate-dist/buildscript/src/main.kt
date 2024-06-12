import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.outputStream

fun main(args: Array<String>) {
    check(args.size == 2) {
        "Expected 2 arguments, but got: [${args.joinToString(", ")}]"
    }

    val (outputDirectoryString, runtimeClasspathString) = args
    val distFile = Path.of(outputDirectoryString, "dist.zip")

    val runtimeClasspath = runtimeClasspathString
        .split(File.pathSeparatorChar)
        .sorted()
        .map { Path.of(it) }

    distFile.outputStream().buffered().let { ZipOutputStream(it) }.use { zipStream ->
        for (file in runtimeClasspath) {
            println("Writing $file to $distFile")
            if (file.isRegularFile()) {
                zipStream.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { input -> input.copyTo(zipStream) }
            } else {
                zipStream.putNextEntry(ZipEntry(file.name + ".jar"))
                // in real life, we'll need to pack directory into zip and copy it to zipStream
                // but here let's skip it and just write `file.name`
                zipStream.write(file.name.toByteArray())
            }
            zipStream.closeEntry()
        }
    }
}
