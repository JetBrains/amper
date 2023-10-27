
### Is it a brand-new build tool from JetBrains?
You can think of it as a tool for project configuration with the focus on the usability, onboarding experience and IDE support. It provides a configuration layer on top of Gradle and is implemented as a Gradle plugin. 

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
We plan to expand the list of supported use cases based on the demand. Please submit your requests and suggestions to the [tracker](https://youtrack.jetbrains.com/issues/AMPER) or join the [Slack channel](https://kotlinlang.slack.com/archives/C062WG3A7T8) for discussions.
Meanwhile, you can use Gradle plugins and tasks as usual. See the documentation on the [Gradle interop](Documentation.md#gradle-interop).

### Can I write a custom task or use a plugin?
Extensibility is currently not implemented, but we are exploring it. Meanwhile, you can use the Gradle backend to write tasks and use the Gradle plugins. See the documentation on the [Gradle interop](Documentation.md#gradle-interop).

### Will it be open sourced?
Yes. We'll be open sourcing the code at a later date.

### When will it be released to stable?
Right now we’re focusing on getting feedback and understanding your needs. . Based on that we’ll decide in which direction to move the projects and provide estimations.

### Shall I start my next project with it?
You are welcome to use it in any type of project. However, please understand that it is still in the experimental phase and we expect things to change.

### Shall I migrate my existing project?
Understanding real-world scenarios is crucial for us in order to provide a better experience, so from our side we’d love to hear about the challenges you may face porting existing projects. However, please understand, as mentioned above, that the project is still in the experimental phase and we cannot guarantee that all scenarios can be supported. 

### How do I report a bug?
Please report problems to [our tracker](https://youtrack.jetbrains.com/issues/AMPER). Since this project is in the experimental phase, 
we would also greatly appreciate feedback and suggestions regarding the configuration experience - join our [Slack channel](https://kotlinlang.slack.com/archives/C062WG3A7T8) for discussion.

### Is there an automated migration tool?
Not currently, but it certainly is something we’re looking into. See the [Gradle migration tutorial](GradleMigration.md) and examples. 

### Why don’t you use Kotlin for the manifest files?
Currently we use YAML as a simple and readable markup language. It allows us to experiment with the UX and the IDE support much faster. We’ll review the language choice as we proceed with the design and based. Kotlin DSL or a limited form of it is one of the possible options.
Having said that, we believe that the declarative approach to the project configuration has significant advantages over the imperative approach. Declarative configuration is easily toolable, much easier to recover from the error, and it’s very fast to interpret. These properties are critical for good UX. 
Our final language choice will be made based on the overall UX it provides.

### Why is Gradle used as a build tool?
The main focus of the Amper for now is improving the build configuration experience and toolability. Gradle is a well tested build engine and it  allowed us to start experimenting with the UX of the configuration very quickly. Also, smooth interop with Gradle allows using Amper in existing projects, which is important in order to get feedback from the real-world use-cases.

### Why not simply improve Gradle?
First and foremost, we are constantly working with the Gradle team to improve Gradle support in our IDEs and as a primary Kotlin build tool.
Gradle has its strengths and thanks to its flexibility could be used in a variety of  projects and environments. The great example is that the Amper frontend is implemented as a Gradle Plugin, with Gradle being used as a primary build tool.
On the other hand, we believe there is room for improving project configuration experience and the IDE support. That’s why we want to show you our design and get your feedback. Depending on it we’ll decide which direction to take with the design.

### What about Gradle extensibility and plugins?
Our current focus is improving the build tooling UX and IDE support for Kotlin and Kotlin Multiplatform. We aim to support the majority of the cases out of the box.
Since Gradle is used as a build tool, there is a full interop and possibility to use existing plugins and write custom tasks using Gradle. 
