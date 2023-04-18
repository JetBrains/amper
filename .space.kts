job("Build") {
    container(displayName = "Build", image = "amazoncorretto:17") {
        shellScript {
            content = """
                cd prototype-implementation
                chmod +x gradlew
                ./gradlew --no-daemon --info --stacktrace test
            """.trimIndent()
        }
    }
}
