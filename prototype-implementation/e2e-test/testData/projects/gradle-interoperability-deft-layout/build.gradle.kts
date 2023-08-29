import org.jetbrains.deft.proto.gradle.LayoutMode

deft {
    layout = LayoutMode.DEFT
}

val testRun by tasks.creating {
    dependsOn("run")
}