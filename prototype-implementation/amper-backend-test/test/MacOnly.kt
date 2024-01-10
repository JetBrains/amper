import org.junit.jupiter.api.condition.EnabledIfSystemProperty

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@EnabledIfSystemProperty(named = "os.name", matches = "Mac OS.*")
annotation class MacOnly
