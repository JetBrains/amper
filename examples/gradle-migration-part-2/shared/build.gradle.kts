import org.jetbrains.deft.proto.gradle.LayoutMode

// (Comparing to part 1)

// There is no plugin application now.

kotlin {
    sourceSets {
        // There is a custom source set:
        val util by creating {}

        // Anyway you can access Deft source sets:
        val common by getting {
            dependsOn(util)
        }
    }
}

// Deft layout is set to COMBINED to use custom `util` source set (it is preserved).
// All "default" source sets, that are not managed by Deft are cleared.
deft {
    layout = LayoutMode.COMBINED
}

// Some properties (android) migrated to Pot.yaml file now.

// Anyway you can use custom task, no matter the layout:
val customTask by tasks.creating {
    doLast {
        println("I'm custom task!")
    }
}