
tasks.withType<Test>().configureEach {
    // Проверяем, если задача запускается напрямую из модуля или с использованием --tests
    onlyIf {
        val directInvocation = gradle.startParameter.taskNames.any { it.startsWith(":sources") }
        val hasTestsOption = gradle.startParameter.taskRequests.any { request ->
            request.args.any { it.startsWith("--tests") }
        }
        directInvocation || hasTestsOption
    }
}
