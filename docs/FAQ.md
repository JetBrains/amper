## General

### Is Amper a brand-new build tool from JetBrains?

Yes, Amper is a new build tool with a focus on user experience and IDE support.

### Do you plan to support only Kotlin?

For now, the primary target is Kotlin and Kotlin Multiplatform projects. Since Kotlin is multiplatform, Amper also
supports Java on the JVM, and Swift and Objective-C on iOS.
We’ll investigate other tech stacks in the future based on the demand for them.

### Which target platforms are supported?

Currently, you can create applications for the JVM, Android, iOS, macOS, and Linux. Libraries can be created for all
Kotlin Multiplatform targets.

### Does Amper support Compose Multiplatform?

Yes, you can configure Compose for Android, iOS, and desktop.

### Does Amper support Kotlin/JS or Kotlin/Wasm projects?

Currently, Amper doesn't support Kotlin/JS and only supports Wasm in library modules.
Please follow [AMPER-221](https://youtrack.jetbrains.com/issue/AMPER-221) for updates on JS support, and
[AMPER-258](https://youtrack.jetbrains.com/issue/AMPER-258) for updates on Wasm support.

### What functionality do you plan to support?

We’re working on the project library catalogs, publication, and extensibility.

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

### Why don’t you use Kotlin for Amper's configuration files?

Currently, we use YAML as a simple and readable markup language. It allows us to experiment with the UX and the IDE
support much faster. We’ll review the language choice as we proceed with the design and based on demand. The Kotlin DSL,
or a limited form thereof, is one of the possible options.

Having said that, we believe that the declarative approach to project configuration has significant advantages over the
imperative approach. Declarative configuration is easily toolable, recovery from errors is much easier, and
interpretation is much faster. These properties are critical for a good UX.

Our final language choice will be made based on the overall UX it provides.

### Why did you have a Gradle-based option to use Amper?

In the initial Amper prototype, our main focus was improving the user experience and toolability of build configuration.
Gradle, as a well-tested build engine, allowed us to start experimenting with the UX of the configuration very quickly.
What’s more, smooth interoperability with Gradle allows for the use of Amper in existing projects, which is important if
we want to get feedback from real-world use cases.

Now, Amper is a standalone build tool, which allows us to improve the IDE support and workflows even further.

### Why not simply improve Gradle?

We believe there is room to improve the project configuration experience and IDE support.
With Amper, we want to show you our design and get your feedback, as it will help us to decide which direction to take
the design.

At the same time, we are also [working with the Gradle team](https://blog.gradle.org/declarative-gradle) to improve
Gradle support in our IDEs and Gradle itself.

### What about Gradle extensibility and plugins?

We aim to support most of the Kotlin and Kotlin Multiplatform use cases out of the box 
and offer a reasonable level of extensibility.

### How do Amper and Declarative Gradle relate to each other?

Both projects aim to improve the developer experience and the IDE support, but from opposite directions and with
different constraints. Amper's approach is to design, from the ground up, a tool that is easy to use for the developers
regardless of their background, with great IDE support in mind, and focused on specific use-cases.
The [Declarative Gradle project](https://blog.gradle.org/declarative-gradle) approaches the same goal from the other end, 
improving the developer experience and the IDE support in an already exising powerful tool. 

While both projects are still experimental, it's important that you provide your feedback to shape the future development.

## Usage

### What are the requirements to use Amper?

See the [setup instructions](Setup.md).

The Amper command line tool doesn't require any software preinstallation, except the Xcode toolchain if you want to 
build iOS applications. See the [usage instructions](Usage.md#using-amper-from-the-command-line).

The latest [IntelliJ IDEA EAP](https://www.jetbrains.com/idea/nextversion/) is advised to work with Amper projects.

### How do I create a new Amper project?

You have several options:

* Kick-start your project using one of the [examples](../examples)

* Download the Amper script by following the [usage instructions](Usage.md), and generate a project from a template 
  using the `amper init` command.

### How do I create a multi-module project in Amper?

See the documentation on the [project layout](Documentation.md#project-layout) and
the [comparison of Amper and Gradle project layouts](Documentation.md#gradle-vs-amper-project-layout).

### How do I migrate my existing Gradle project to Amper?

Check the [tutorial](GradleMigration.md) on migrating your Gradle project and subprojects.

### Is there an automated migration tool?

Not currently, but it's certainly something we’re looking into. See the [Gradle migration tutorial](GradleMigration.md)
and examples.

### Feature X is not yet supported, what can I do?

We plan to expand the list of supported use cases based on demand. Please submit your requests and suggestions in
the [tracker](https://youtrack.jetbrains.com/issues/AMPER) or join
the [Slack channel](https://kotlinlang.slack.com/archives/C062WG3A7T8) for discussions. Meanwhile, you can use Gradle
plugins and tasks as usual. See the documentation on the [Gradle interop](Documentation.md#gradle-interop).

### Can I write a custom task or use a plugin?

Extensibility is not currently implemented in Amper, but we are working on it.
Meanwhile, in Gradle-based Amper projects, you can use any available Gradle plugin and write custom tasks.
See the documentation on [Gradle interop](Documentation.md#gradle-interop).

### I know how to configure "X" in Gradle, how do I do the same in Amper configuration file?

If you cannot find an answer in [Amper documentation](Documentation.md) or examples, you can still
use [Gradle interop](Documentation.md#gradle-interop) and configure as usually in your build.gradle(.kts) files.

### How can I use C-interop in Amper?

For now, Amper doesn't directly support C-interop. 
You can use [Gradle interop](Documentation.md#configuring-c-interop-using-the-gradle-build-file) as a workaround.