class JvmWorld : World {
    override val name: String
        get() = "JVM World"
}

actual fun getWorld(): World = JvmWorld()
