
### Is Amper a brand-new build tool from JetBrains?

Yes, Amper is a project configuration and build tool with a focus on the user experience and the IDE support.
Amper exists as a standalone build tool and also as a Gradle plugin for existing Gradle-based projects. 
Both versions offer a unified easy-to-use declarative configuration format.

### Do you plan to support only Kotlin?

For now, the primary target is Kotlin and Kotlin Multiplatform projects. Since Kotlin is multiplatform, Amper also
supports Java on JVM and Swift and Objective-C on iOS.
We’ll investigate other tech stacks in the future based on the demand for them.

### Which target platforms are supported?

Currently, you can create applications for the JVM, Android, iOS, macOS, and Linux. Libraries can be created for all
Kotlin Multiplatform targets.

### Does Amper support Compose Multiplatform?
Yes, you can configure Compose for Android, iOS, and desktop.

### What functionality do you plan to support?

We’re working on the following functionalities: version catalogs, Swift Package Manager support, C-interop, packaging
and publication, and extensibility.

### Feature X is not yet supported, what can I do?

We plan to expand the list of supported use cases based on demand. Please submit your requests and suggestions in
the [tracker](https://youtrack.jetbrains.com/issues/AMPER) or join
the [Slack channel](https://kotlinlang.slack.com/archives/C062WG3A7T8) for discussions. Meanwhile, you can use Gradle
plugins and tasks as usual. See the documentation on the [Gradle interop](Documentation.md#gradle-interop).

### Can I write a custom task or use a plugin?

Extensibility is not currently implemented in the standalone Amper version, but we are working on it.
Meanwhile, in the Gradle-based Amper projects, you can use any available Gradle plugin and write custom tasks. 
See the documentation on the [Gradle interop](Documentation.md#gradle-interop).

### Will Amper be open source?
Yes, Amper is already open source.

### When will Amper be released as stable?

Right now, we’re focusing on getting feedback and understanding your needs. Based on that, we’ll be able to provide a
more accurate estimate of a release date sometime in the future.

### Should I start my next project with Amper?

You’re welcome to use it in any type of project. However, please understand that Amper is still in the experimental
phase, and we expect things to change.

### Should I migrate my existing projects?

Understanding real-world scenarios is crucial for us to provide a better experience, so from our side we’d love
to hear about the challenges you may face porting existing projects. However, please understand that the project is
still in the experimental phase, and we cannot guarantee that all scenarios can be supported.

### How do I report a bug?

Please report problems to [our issue tracker](https://youtrack.jetbrains.com/issues/AMPER). Since this project is in the
experimental phase, we would also greatly appreciate feedback and suggestions regarding the configuration experience –
join our [Slack channel](https://kotlinlang.slack.com/archives/C062WG3A7T8) for discussion.

### Is there an automated migration tool?

Not currently, but it certainly is something we’re looking into. See the [Gradle migration tutorial](GradleMigration.md)
and examples.

### Why don’t you use Kotlin for the Amper configuration files?

Currently, we use YAML as a simple and readable markup language. It allows us to experiment with the UX and the IDE
support much faster. We’ll review the language choice as we proceed with the design and based on demand. The Kotlin DSL,
or a limited form thereof, is one of the possible options.

Having said that, we believe that the declarative approach to project configuration has significant advantages over the
imperative approach. Declarative configuration is easily toolable, recovery from errors is much easier, and
interpretation is much faster. These properties are critical for a good UX.

Our final language choice will be made based on the overall UX it provides.

### Why is Gradle used as the build engine?

In the initial Amper prototype, our main focus was improving the build configuration user experience and toolability.
Gradle, as a well-tested build engine, allowed us to start experimenting with the UX of the configuration very quickly.
What’s more, smooth interoperability with Gradle allows for the use of Amper in existing projects, which is important if
we want to get feedback from real-world use cases.

Amper also exists as a standalone tool which allows us to improve the IDE support and workflows even further.

### Why not simply improve Gradle?

We believe there is room to improve the project configuration experience and IDE support.
With Amper we want to show you our design and get your feedback, as it will help us to decide which direction to take
the design.

At the same time, we are also [working with the Gradle team](https://blog.gradle.org/declarative-gradle) to improve
Gradle support in our IDEs and Gradle itself.

### What about Gradle extensibility and plugins?

We aim to support most of the Kotlin and Kotlin Multiplatform use cases out of the box, 
and offer a reasonable level of extensibility.

In addition to the standalone version, Amper is offered as a Gradle plugin.  
Gradle-based Amper offers the full interoperability with Gradle, allows using Gradle plugins and write custom tasks.




