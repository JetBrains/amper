import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializerOrNull

@Serializable
data class Data(val a: String)

@OptIn(InternalSerializationApi::class)
inline fun <reified T : Any> checkSerializer() {
    check(T::class.serializerOrNull() != null) {
        "Serializer is missing for class ${T::class.java.name}"
    }
}

fun main() {
    checkSerializer<Data>()

    val json = Json.encodeToString(Data("Hello, World!"))
    val data = Json.decodeFromString<Data>(json)

    println(data.a)
}
