## General

### Is it a brand-new build tool from JetBrains?
You can think of it as a build tool. Right now our primary focus is on the project configuration experience. We’re currently using Gradle under the covers. We are also exploring other build tools as backends, including a completely new build engine that we’re working on. 

### Do you plan to support only Kotlin?
For now the primary target is Kotlin and Kotlin Multiplatform projects. Since Kotlin is multiplatform, by extension we also support Java on JVM, Swift, and Objective-C on iOS.
We’ll investigate other tech stacks in future based on the demand.

### Which target platforms are supported?
Currently you can create applications for JVM, Android, iOS, macOS, and Linux. Libraries can be created for all Kotlin Multiplatform targets.

### Does it support Compose Multiplatform?
Yes. You can configure Compose for Android, iOS, and Desktop.

### Do you plan to support feature X? 
We are working on the following functionality: version catalogs, Swift Package Manager support, C-interop, packaging and publication, and extensibility.

### A feature X is not yet supported, what can I do?
We plan to expand the list of supported use cases based on the demand. Please submit your requests and suggestions in the [tracker](https://youtrack.jetbrains.com/issues/AMPER) or join the Slack channel<!LINK!> for discussions.
Meanwhile, you can use Gradle plugins and tasks as usual. See the [documentation on the Gradle interop](Documentation.md#gradle-interop).

### Can I write a custom task or use a plugin?
Extensibility is currently not implemented, we are working on it.
Meanwhile, you can use the Gradle backend to write tasks and use the Gradle plugins. See the [documentation on the Gradle interop](Documentation.md#gradle-interop).

### When will it be released to stable?
Right now we’re focusing on getting feedback and understanding your needs. 
Based on that we’ll be able to provide a more accurate estimate of a release date sometime in the future. 

### Shall I start my next project with it?
You are welcome to use it in any type of project. 
However, please understand that it is still in the experimental phase and we expect things to change.

### Shall I migrate my existing project?
Understanding real-world scenarios is crucial for us in order to provide a better experience, 
so from our side we’d love to hear about the challenges you may face porting existing projects. 
However, please understand, as mentioned above, that the project is still in the experimental phase and we cannot guarantee that all scenarios can be supported. 

### How do I report a bug?
Please report problems to link to to the tracker. Since this project is in the experimental phase, 
we would also greatly appreciate feedback and suggestions regarding the configuration experience - join our Slack channel for discussion.

### Is there an automated migration tool?
Not currently, but it certainly is something we’re looking into. See the [Gradle migration tutorial](GradleMigration.md) and examples. 

### Why don’t you use Kotlin for the manifest files?
Currently we use YAML as a simple and readable markup language. It allows us to experiment with the UX and the IDE support much faster. We’ll review the language choice as we proceed with the design and based. Kotlin DSL or a limited form of it is one of the possible options.
Having said that, we believe that the declarative approach to the project configuration has significant advantages over the imperative approach. Declarative configuration is easily toolable, much easier to recover from the error, and it’s very fast to interpret. These properties are critical for good UX. 
Our final language choice will be made based on the overall UX it provides.

### Why is Gradle used as a build engine?
On the one hand using Gradle as a build engine allowed us to start experimenting with the UX of the configuration quicker. On the other hand, smooth interop with Gradle allows for a smooth and gradual migration of existing projects.
We designed the frontend and the manifest to be relatively engine-agnostic, and we’ll explore alternative engines in future. Also, we are experimenting with a completely new build engine.

## Gradle-related

### Does this mean you’re dropping Gradle support for Kotlin and Kotlin Multiplatform? 
No. We are committed to continue to support Gradle for the foreseeable future. It applies both to Kotlin tooling and to our IDEs such as IntelliJ IDEA and Fleet.

### Why not simply improve Gradle?
We are constantly working with the Gradle team to improve Gradle support in our IDEs and as a primary Kotlin build tool.
Gradle has its strengths and thanks to its flexibility could be used in a variety of  projects and environments. The great example is that the frontend is implemented as a Gradle Plugin, with Gradle being used as a primary build tool.
On the other hand, we believe there is room for a more dedicated build tool with a focus on UX and IDE workflows and working on a new build engine.

### Gradle is very flexible and extensible with a lot of plugins. How are you going to cover all this?
Our current focus is improving the build tooling UX and IDE support for Kotlin and Kotlin Multiplatform. We aim to support the majority of the cases out of the box.
In addition,  Gradle is currently used as a build engine. There is a smooth interop and possibility to use existing plugins and write custom tasks using Gradle. We plan to investigate using other build tools as engines in future.

