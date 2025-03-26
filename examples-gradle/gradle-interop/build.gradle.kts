// Use a Gradle plugin
plugins {
    id("app.cash.sqldelight") version "2.0.0"
}

// Configure a Gradle plugin
sqldelight {
    databases {
        create("Database") {
            packageName.set("com.example")
        }
    }
}


// Write a Gradle task
tasks.register("hello") {
    doLast {
        println("Hello from Gradle task!")
    }
}

// Override settings
afterEvaluate {
    tasks.named<JavaExec>("runJvm") {
        mainClass.set("My_mainKt")
    }
}
