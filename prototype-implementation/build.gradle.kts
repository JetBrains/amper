val aggregateTestReports by tasks.creating(Zip::class) {
    dependsOn(getTasksByName("jvmTest", true))
    destinationDirectory.set(buildDir)
    archiveBaseName.set("aggregatedTestReport")

    subprojects {
        this@subprojects.buildDir
            .resolve("reports/tests/jvmTest")
            .takeIf { it.exists() }
            ?.let {
                from(it) {
                    into(this@subprojects.path.replace(":", "/"))
                }
            }
    }
}