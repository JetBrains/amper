import kotlin.random.Random
import Message

private fun connectTo(url: String, onConnect: Connection.() -> Unit, onMessage: (Message) -> Unit) = TODO()

private interface Connection {
    fun send(message: Message)
}

fun main() {
    val author = "Agent Smith ${Random.nextInt()}"
    connectTo(
        url = "server:8080",
        onConnect {
            send(Message(author, Random.nextInt(), "Hello, there."))
        },
        onMessage = {
            println(it)
        },
    )
}
