buildscript {
    repositories {
        mavenCentral()
        google()
    }

    dependencies {
        classpath("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:1.8.10")
        classpath("org.yaml:snakeyaml:2.0")
        classpath("org.jetbrains.kotlin:kotlin-reflect:1.8.10")
        classpath(files("../../../gradle-integration/build/libs/gradle-integration-jvm-1.0-SNAPSHOT.jar"))
        classpath(files("../../../frontend-api/build/libs/frontend-api-jvm-1.0-SNAPSHOT.jar"))
        classpath(files("../../../frontend/plain/yaml/build/libs/yaml-jvm-1.0-SNAPSHOT.jar"))
        classpath(files("../../../frontend/util/build/libs/util-jvm-1.0-SNAPSHOT.jar"))
    }
}

plugins.apply("org.jetbrains.deft.proto.settings.plugin")