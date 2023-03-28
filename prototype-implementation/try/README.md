## TL;DR
To import this gradle project you need to build and publish
to maven local following projects (simple "publishToMavenLocal" task will do the trick):
 - [util](..%2Ffrontend%2Futil)
 - [yaml](..%2Ffrontend%2Fwithout-fragments%2Fyaml)
 - [frontend-api](..%2Ffrontend-api)
 - [gradle-integration](..%2Fgradle-integration)

When it's done, just add this directory as Gradle project and it should work.

**Note**: Use `build-wf.yaml` build files to use "with fragments" frontend
and `build-wof.yaml` to use "without fragments".
To import choosen file type, see comments at [settings.gradle.kts](settings.gradle.kts).

There are numbers of limitations for now, including (but not limited to):
 - You can't play with kotlin options (you can try, but there will be some conflicts from Gradle build)
 - Android platform was not well tested, probably will crash
 - Aliases are not appearing as source sets seamlessly - you need to add dependencies for them