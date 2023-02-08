fun greet(name: String): String {
    log("Calling greeting for name: $name")
    return "Hello, $name from ${getPlatform()}"
}

expect fun getPlatform(): String

expect fun log(string: String)
