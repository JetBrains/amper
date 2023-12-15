import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@EnabledIfEnvironmentVariable(named = "OS", matches = "Windows.*")
annotation class WindowsOnly
