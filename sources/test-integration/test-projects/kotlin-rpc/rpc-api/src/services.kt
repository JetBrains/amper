import kotlinx.rpc.annotations.Rpc
import kotlinx.coroutines.flow.*

@Rpc
interface AwesomeService {
    fun getNews(city: String): Flow<String>

    suspend fun daysUntilStableRelease(): Int
}
