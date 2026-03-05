import org.jetbrains.amper.plugins.*

enum class SomeEnum {
    ONE, TWO, THREE,
}

@TaskAction
fun someAction(
    stringProp: String,
    booleanProp: Boolean,
    intProp: Int,
    enumProp: SomeEnum,
) {}