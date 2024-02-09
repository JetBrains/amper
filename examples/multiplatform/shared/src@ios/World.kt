class iOSWorld : World {
    override val name: String
        get() = "iOS World"
}

actual fun getWorld(): World = iOSWorld()
