class AndroidWorld : World {
    override val name: String
        get() = "Android World"
}

actual fun getWorld(): World = AndroidWorld()
