fun main() {
    val string = object {}.javaClass.getResourceAsStream("/mystuff.txt")!!.use { it.readBytes() }.decodeToString()
    println("String from resources: $string")
}