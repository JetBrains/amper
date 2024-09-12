package hello.world

import Screen
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import getDatabaseBuilder
import getRoomDatabase

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Screen()
        }
    }

    suspend fun doSomethingWithRoom() {
        val db = getRoomDatabase(getDatabaseBuilder(applicationContext))
        val dao = db.getTodoDao()
        val todos = dao.getAllAsFlow()
        todos.collect {
            println(it)
        }
    }
}
