job("Build") {
    container(displayName = "Build", image = "amazoncorretto:17") {
        workDir = "prototype-implementation"
        kotlinScript {
            it.gradlew("--info", "--stacktrace", "test", "--fail-fast", "-q")
        }
    }
}
