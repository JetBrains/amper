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
}