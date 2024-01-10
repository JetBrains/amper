val aggregateTestReports by tasks.creating(Zip::class) {
    destinationDirectory.set(buildDir)
    archiveBaseName.set("aggregatedTestReport")
    subprojects {
        this@subprojects.buildDir
            .resolve("reports/tests/allTests")
            .let {
                from(it) {
                    into(this@subprojects.path.replace(":", "/"))
                }
            }
    }

    // Should run after tests, but do not depend on them.
    mustRunAfter(getTasksByName("allTests", true))
}
