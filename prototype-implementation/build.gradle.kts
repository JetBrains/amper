val aggregateTestReports by tasks.creating(Zip::class) {
    dependsOn(getTasksByName("allTests", true))
    destinationDirectory.set(buildDir)
    archiveBaseName.set("aggregatedTestReport")

    subprojects {
        this@subprojects.buildDir
            .resolve("reports/tests/allTests")
            .takeIf { it.exists() }
            ?.let {
                from(it) {
                    into(this@subprojects.path.replace(":", "/"))
                }
            }
    }
}