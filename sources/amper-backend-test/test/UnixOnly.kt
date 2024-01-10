import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@DisabledIfEnvironmentVariable(named = "OS", matches = "Windows.*")
annotation class UnixOnly
