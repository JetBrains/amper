job("Build") {
    container(displayName = "Build", image = "openjdk:19") {
        shellScript {
            content = """
                cd prototype-implementation
                chmod +x gradlew
                ./gradlew --no-daemon -D --info --stacktrace test
            """.trimIndent()
        }
    }
}
