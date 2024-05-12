import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Serializable
data class Data(val a: String)

fun main() {
    val json = Json.encodeToString(Data("Hello, World!"))
    val data = Json.decodeFromString<Data>(json)

    println(data.a)
}
