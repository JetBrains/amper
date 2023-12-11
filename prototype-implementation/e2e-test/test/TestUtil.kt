import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.div
import kotlin.io.path.exists

object TestUtil {
    private val amperCheckoutRoot: Path by lazy {
        val start = Path.of(System.getProperty("user.dir"))

        var current: Path = start
        while (current != current.parent) {
            if (current.resolve("syncVersions.sh").exists() && current.resolve("CONTRIBUTING.md").exists()) {
                return@lazy current
            }

            current = current.parent ?: break
        }

        error("Unable to find Amper checkout root upwards from '$start'")
    }

    // Shared between different runs of testing
    // on developer's machine: some place under working copy, assuming it won't be cleared after every test run
    // on TeamCity: shared place on build agent which will be fully deleted if TeamCity lacks space on that agent
    val sharedTestCaches: Path by lazy {
        val dir = if (TeamCityHelper.isUnderTeamCity) {
            val persistentCachePath = TeamCityHelper.systemProperties["agent.persistent.cache"]
            check(!persistentCachePath.isNullOrBlank()) {
                "'agent.persistent.cache' system property is required under TeamCity"
            }
            Paths.get(persistentCachePath) / "amper-build"
        } else {
            amperCheckoutRoot / "build" / "shared-caches"
        }

        Files.createDirectories(dir)
    }
}