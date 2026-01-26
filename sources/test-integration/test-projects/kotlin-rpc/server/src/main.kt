import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.coroutines.flow.*

data class AwesomeParameters(val stable: Boolean, val daysUntilStable: Int?)

fun main() {
    embeddedServer(Netty, 8080) {
        install(Krpc)
        routing {
            rpc("/awesome") {
                rpcConfig {
                    serialization {
                        json()
                    }
                }

                registerService<AwesomeService> {
                    AwesomeServiceImpl(AwesomeParameters(false, 23))
                }
            }
        }
    }.start(wait = true)
}

class AwesomeServiceImpl(val parameters: AwesomeParameters) : AwesomeService {
    override fun getNews(city: String): Flow<String> {
        return flow {
            emit("Today is 23 degrees!")
            emit("Harry Potter is in $city!")
            emit("New dogs cafe has opened doors to all fluffy customers!")
        }
    }

    override suspend fun daysUntilStableRelease(): Int {
        return if (parameters.stable) 0 else {
            parameters.daysUntilStable ?: error("Who says it will be stable?")
        }
    }
}