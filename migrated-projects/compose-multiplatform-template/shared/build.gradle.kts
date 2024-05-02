tasks.withType<Copy> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.filter { it.name.contains("compose") }.filterIsInstance<Copy>().forEach {
    it.configure<Copy> {

    }
}