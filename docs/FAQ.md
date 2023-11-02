
### Is Amper a brand-new build tool from JetBrains?
You can think of Amper as a tool for project configuration whose main aim is to improve user experience – this is our primary focus for the time being. We’re currently using Gradle as a build engine, but we are also exploring other build tools as backends, including a completely new build engine that we’re working on. 

### Do you plan to support only Kotlin?
For now, the primary target is Kotlin and Kotlin Multiplatform projects. Since Kotlin is multiplatform, Amper supports both Java on the JVM and Swift and Objective-C on iOS.
We’ll investigate other tech stacks in the future based on the demand for them.

### Which target platforms are supported?
Currently you can create applications for the JVM, Android, iOS, macOS, and Linux. Libraries can be created for all Kotlin Multiplatform targets.

### Does Amper support Compose Multiplatform?
Yes, you can configure Compose for Android, iOS, and desktop.

### What functionality do you plan to support?
We’re working on the following functionalities: version catalogs, Swift Package Manager support, C-interop, packaging and publication, and extensibility.

### Feature X is not yet supported, what can I do?
We plan to expand the list of supported use cases based on demand. Please submit your requests and suggestions in the [tracker](https://youtrack.jetbrains.com/issues/AMPER) or join the [Slack channel](https://kotlinlang.slack.com/archives/C062WG3A7T8) for discussions. Meanwhile you can use Gradle plugins and tasks as usual. See the documentation on the [Gradle interop](Documentation.md#gradle-interop).

### Can I write a custom task or use a plugin?
Extensibility is not currently implemented, but we are exploring it. Meanwhile, you can use the Gradle backend to write tasks and use any available Gradle plugin. See the documentation on the [Gradle interop](Documentation.md#gradle-interop).

### Will Amper be open source?
Yes, we'll be open-sourcing the code at a later date.

### When will Amper be released as stable?
Right now, we’re focusing on getting feedback and understanding your needs. Based on that, we’ll be able to provide a more accurate estimate of a release date sometime in the future.

### Should I start my next project with Amper?
You’re welcome to use it in any type of project. However, please understand that Amper is still in the experimental phase and we expect things to change.

### Should I migrate my existing projects?
Understanding real-world scenarios is crucial for us in order to provide a better experience, so from our side we’d love to hear about the challenges you may face porting existing projects. However, please understand that the project is still in the experimental phase and we cannot guarantee that all scenarios can be supported. 

### How do I report a bug?
Please report problems to [our issue tracker](https://youtrack.jetbrains.com/issues/AMPER). Since this project is in the experimental phase, we would also greatly appreciate feedback and suggestions regarding the configuration experience – join our [Slack channel](https://kotlinlang.slack.com/archives/C062WG3A7T8) for discussion.

### Is there an automated migration tool?
Not currently, but it certainly is something we’re looking into. See the [Gradle migration tutorial](GradleMigration.md) and examples. 

### Why don’t you use Kotlin for the manifest files?
Currently, we use YAML as a simple and readable markup language. It allows us to experiment with the UX and the IDE support much faster. We’ll review the language choice as we proceed with the design and based on demand. The Kotlin DSL, or a limited form thereof, is one of the possible options.
Having said that, we believe that the declarative approach to project configuration has significant advantages over the imperative approach. Declarative configuration is easily toolable, recovery from errors is much easier, and interpretation is much faster. These properties are critical for a good UX. 
Our final language choice will be made based on the overall UX it provides.

### Why is Gradle being used as a build engine?
First of all, using Gradle as a build engine allowed us to start experimenting with the configuration’s UX more quickly. Furthermore, smooth interop with Gradle allows for a smooth and gradual migration of existing projects.
We designed the frontend and the manifest to be relatively engine-agnostic, and we’ll explore alternative engines in the future. Beyond this, we’re also experimenting with a completely new build engine.

### Does this mean you’re dropping Gradle support for Kotlin and Kotlin Multiplatform? 
No, we’re committed to continuing to support Gradle for the foreseeable future. This applies both to Kotlin tooling and to our IDEs such as IntelliJ IDEA and Fleet.

### Why not simply improve Gradle?
We’re constantly working with the Gradle team to improve Gradle support in our IDEs and Gradle itself as a primary Kotlin build tool.
Gradle has its strengths, and thanks to its flexibility, it can be used in a variety of projects and environments. A great example of this is how the frontend is implemented as a Gradle plugin, with Gradle being used as a primary build tool.
On the other hand, we believe there is room for a more dedicated build tool with a focus on UX and IDE workflows, which is why we’re working on a new build engine.

### What about Gradle extensibility and plugins?
Our current focus is improving the build tooling UX and IDE support for Kotlin and Kotlin Multiplatform. We aim to support most use cases out of the box.
In addition, Gradle is currently used as a build engine. There’s smooth interoperability, as well as the option to use existing plugins and write custom tasks using Gradle. That said, we plan to investigate using other build tools as engines in the future.
