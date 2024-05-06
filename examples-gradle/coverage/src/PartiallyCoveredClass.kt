import kotlin.random.Random

class PartiallyCoveredClass {

    fun notCovered() {
        println("Not covered")
    }

    fun covered() {
        println("covered")
    }

    fun conditional() {
        if(Random.nextBoolean()) {
            println("maybe covered")
        } else {
            println("also maybe covered")
        }
    }
}
