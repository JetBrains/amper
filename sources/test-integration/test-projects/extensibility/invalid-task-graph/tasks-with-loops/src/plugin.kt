import org.jetbrains.amper.plugins.*

import java.nio.file.Path

@TaskAction
fun action(
    @Input input: Path?,
    @Input input1: Path?,
    @Output output: Path?,
    @Output output1: Path?,
) {}