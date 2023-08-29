import org.jetbrains.deft.proto.gradle.LayoutMode

deft {
    layout = LayoutMode.COMBINED
}

kotlin {
    sourceSets {
        val additional by creating {
        }
        val jvm by getting {
            dependsOn(additional)
        }
    }
}

val testRun by tasks.creating {
    dependsOn("run")
}