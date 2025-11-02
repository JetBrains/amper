import org.jetbrains.amper.plugins.*

import java.nio.file.Path

@TaskAction
fun action1(
    @Input input1: Path,
    @Input input2: Path,
    @Input input3: Path,
) {}

@TaskAction
fun action2(
    @Output output1: Path,
    @Output output2: Path,
) {}

@TaskAction
fun action3(
    @Input input: Path?,
    @Output output: Path?,
) {}

@TaskAction
fun action4(
    @Input resources: ModuleSources,
    @Output output: Path,
) {}