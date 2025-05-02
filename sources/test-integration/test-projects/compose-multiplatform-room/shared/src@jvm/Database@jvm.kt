import androidx.room.*
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

// https://developer.android.com/kotlin/multiplatform/room#jvm-(desktop)
fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFile = Path(System.getProperty("java.io.tmpdir"), "my_room.db")
    return Room.databaseBuilder<AppDatabase>(
        name = dbFile.absolutePathString(),
    )
}
