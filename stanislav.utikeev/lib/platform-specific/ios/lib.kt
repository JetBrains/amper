import cocoapods.Logging.Logger

actual fun getPlatform(): String = "iOS"

actual fun log(string: String) {
    Logger("main").info(string)
}
