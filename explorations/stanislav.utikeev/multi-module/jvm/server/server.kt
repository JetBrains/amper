import Message

private fun listenOn(port: Int = 8080, onMessage: (Message) -> Unit) = TODO()

private fun send(user: String, message: Message) = TODO()

fun main() {
    val users = mutableSetOf<String>()
    listenOn { message ->
        if (message.author !in users) {
            users.add(message.author)
        }

        (users - message.author).forEach { user ->
            send(user, message)
        }
    }
}
