
tasks.withType<Test>().configureEach {
    // Проверяем, если задача запускается напрямую из модуля или с использованием --tests
    onlyIf {
        val directInvocation = gradle.startParameter.taskNames.any { it.startsWith(":sources") }
        val hasTestsOption = gradle.startParameter.taskRequests.any { request ->
            request.args.any { it.startsWith("--tests") }
        }
        directInvocation || hasTestsOption
    }

    // To build&publish the :android-integration:gradle-plugin and deps for gradle-backed Android builds
    // To build&publish the CLI distribution and scripts required for wrapper-based tests
    for (task in rootProject.getTasksByName("publishToMavenLocal", true)) {
        dependsOn(task)
    }
}
