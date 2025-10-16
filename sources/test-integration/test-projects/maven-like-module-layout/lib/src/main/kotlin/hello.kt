fun hello(): String {
    return object {}.javaClass.getResourceAsStream("input.txt")
        ?.bufferedReader()
        ?.use { it.readText() }
        ?: error("Resource not found")
}
