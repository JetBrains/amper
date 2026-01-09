import io.ktor.client.*
import io.ktor.http.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.rpc.krpc.ktor.client.*
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.withService

fun main() = runBlocking {
    val rpcClient = HttpClient { installKrpc() }.rpc {
        url { takeFrom("ws://localhost:8080/awesome") }

        rpcConfig {
            serialization {
                json()
            }
        }
    }

    val service = rpcClient.withService<AwesomeService>()

    val daysUntilRelease = service.daysUntilStableRelease()
    println("Days until stable release: $daysUntilRelease")

    service.getNews("KotlinBurg").collect { article ->
        println(article)
    }
}