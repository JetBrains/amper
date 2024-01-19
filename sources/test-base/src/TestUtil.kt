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

    val amperSourcesRoot = amperCheckoutRoot / "sources"

    // Shared between different runs of testing
    // on developer's machine: some place under working copy, assuming it won't be cleared after every test run
    // on TeamCity: shared place on build agent which will be fully deleted if TeamCity lacks space on that agent
    // Always run tests in a directory with a space in the name, tests quoting in a lot of places
    val sharedTestCaches: Path by lazy {
        val dir = if (TeamCityHelper.isUnderTeamCity) {
            val persistentCachePath = TeamCityHelper.systemProperties["agent.persistent.cache"]
            check(!persistentCachePath.isNullOrBlank()) {
                "'agent.persistent.cache' system property is required under TeamCity"
            }
            Paths.get(persistentCachePath) / "amper build"
        } else {
            amperCheckoutRoot / "build" / "shared caches"
        }

        Files.createDirectories(dir)
    }

    // Always run tests in a directory with a space in the name, tests quoting in a lot of places
    val tempDir: Path by lazy {
        val dir = if (TeamCityHelper.isUnderTeamCity) {
            TeamCityHelper.tempDirectory / "amper tests"
        } else {
            amperCheckoutRoot / "build" / "tests temp"
        }
        Files.createDirectories(dir)
        println("Temp dir for tests: $dir")
        dir
    }

    // Re-use user cache root for local runs to make testing faster
    // On CI (TeamCity) make it per-build (temp directory for build is cleaned after each build run)
    val userCacheRoot: Path = if (TeamCityHelper.isUnderTeamCity) {
        TeamCityHelper.tempDirectory.resolve("amperUserCacheRoot")
    } else sharedTestCaches
}