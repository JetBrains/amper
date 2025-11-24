# Quick Start

## How to write a plugin

Here we are going to learn how to write a toy build plugin in Amper
that exposes some *external* build‑time data to the application by generating sources.

Let's define what we want from our plugin for starters.
The plugin would be able to parse a `.properties` file and generate Kotlin properties out of it.
Later we may implement additional features.

### Basic example

We will name our plugin `build-config`.
We are going to add it to our existing project.

Let's take a look at the whole project structure we aim for in advance:
<!-- We use python here just to enable annotations -->
```python
<root>/
├─ app/
│  ╰─ module.yaml
├─ build-config/
│  ├─ src/
│  │  ╰─ **.kt
│  ├─ module.yaml
│  ╰─ plugin.yaml
├─ utils/
│  ╰─ module.yaml
├─ ... #(1)!
╰─ project.yaml
```

1.    Other project modules

The `project.yaml` would look like this then:
```yaml title="project.yaml"
modules:
  - app   #(1)!
  - build-config #(2)!
  - utils #(3)!
  - ...   #(4)!

plugins: #(5)!
  - ./build-config
```

1.    Regular module, e.g., `jvm/app`
2.    Our plugin is also a normal module and needs to be listed here
3.    Regular `jvm/lib` module, that contains, e.g., generic utilities useful for most modules in the project
4.    There may be other project modules
5.    This is a block where we list our plugin dependencies to [make available](topics/structure.md#making-plugins-available-in-the-project) in the project.

And the `build-config/module.yaml` would look like:
```yaml title="build-config/module.yaml"
product: jvm/amper-plugin
```

This is already a valid (although incomplete) Amper plugin.

It has a *plugin ID* which equals the plugin module name (`build-config`) by default.
The plugin ID is the string by which the plugin is going to be referred to throughout the project, e.g., to enable/configure it.

Declaring the plugin in the `plugins` section of `project.yaml` makes it *available to the project*,
but it is not yet *enabled in* (*applied to*) any of its modules.
Learn more about it [here](topics/structure.md#making-plugins-available-in-the-project).
But it doesn't contain anything useful yet.

Let's start implementing our plugin by writing a [task action](topics/tasks.md#task-action-definition)
that would do the source generation based on the contents of the properties file.
For now, we'll do the code formatting by hand:
```kotlin title="build-config/src/generateSources.kt"
package com.example

import org.jetbrains.amper.plugins.*

import java.nio.file.Path
import java.util.*  
import kotlin.io.path.*

@TaskAction
@OptIn(ExperimentalPathApi::class)
fun generateSources(
    @Input propertiesFile: Path,
    @Output generatedSourceDir: Path,
) {
    generatedSourceDir.deleteRecursively() //(1)!
    val outputFile = generatedSourceDir / "properties.kt"

    if (!propertiesFile.isRegularFile()) {//(2)!
        println("No input")
        return
    }
    println("Generating sources")//(3)!

    val properties = propertiesFile.bufferedReader().use { reader ->  
        Properties().apply { load(reader) }  
    }.toMap()  

    outputFile.createParentDirectories()//(4)!
    val code = buildString {
        appendLine("package com.example.generated")
        appendLine("public object Config {")
        for ((key, value) in properties) {
            appendLine("    const val `$key`: String = \"$value\"")
        }
        appendLine("}")
    }
    outputFile.writeText(code)
}
```

1.    Clean the old state if any is present from the previous invocation
2.    Input file may not exist at all, need to check that
3.    Simple [logging](topics/tasks.md#logging) (structured logging support comes later)
4.    Need to ensure the output directory structure exists: Amper doesn't pre-create it for us

The code can be written in any Kotlin file in any package – there's no convention here.
`@TaskAction` is a marker for a top-level Kotlin function that can be registered as a task.
`@Input`/`@Output` are marker annotations required for `Path`‑referencing action parameters to tell Amper how to treat these paths.

!!! info
    Amper automatically uses task [execution avoidance](topics/tasks.md#execution-avoidance) based on the contents of `@Input`/`@Output`-annotated paths.

Declaring a task action does nothing by itself yet.
The task with the action must be _registered_ explicitly to become available in modules the plugin is enabled in.
To do that, we need a special file to register tasks and define how they use the plugin's configuration – `plugin.yaml`:

```yaml title="build-config/plugin.yaml"
tasks:
  generate: # (1)!
    action: !com.example.generateSources
      propertiesFile: ${module.rootDir}/config.properties # (2)!
      generatedSourceDir: ${taskOutputDir}
```

1.    Registers the `generate` task
2.    Specifies the conventional location for the source .properties file

Note that the task action's type is specified using the *type tag* — `!com.example.generateSources` — using the fully qualified function name.

As we see here, the `plugin.yaml` file allows [Amper references](topics/references.md) with the syntax `${foo.bar.baz}`.
Here we use the built‑in reference‑only property `taskOutputDir` to direct our output to the unique task‑associated output directory that Amper provides for us.
And `module.rootDir` is the directory of the module the plugin is applied to. 
Learn more about [Amper-provided reference-only properties](topics/references.md#reference-only-properties).

But we need to make Amper aware that our output is, in fact, generated Kotlin sources,
so the build tool can include them in the compilation, IDE can resolve symbols from them, etc.
To do that, we'll use the `markOutputAs` clause in our task registration:

```yaml hl_lines="6-8" title="build-config/plugin.yaml"
tasks:
  generate:
    action: !com.example.generateSources
      propertiesFile: ${module.rootDir}/config.properties
      generatedSourceDir: ${taskOutputDir}
    markOutputsAs:
      - path: ${action.generatedSourceDir}
        kind: kotlin-sources # (1)!
```

1.    `java-sources` and `jvm-resources` are also possible here

We've added an item to the `markOutputsAs` list, where we reference our `generatedSourceDir` path and state,
that `kotlin-sources` will be located there after the task is run.

That's it with the plugin for now! Let's enable it in one of our modules (`app`):

```yaml hl_lines="1-2" title="app/module.yaml"
plugins:
  build-config: enabled # (1)!

# ... Other things, like settings, dependencies, etc.
```

1.    `#!yaml <plugin-id>: enabled` is a shorthand; </br>
      `#!yaml <plugin-id>: { enabled: true }` is the full form

If we now run the build, we'll see that our generated `com.example.Config` object is present and is visible in the IDE, 
and `"Generating sources"` is being logged to the console.

Now let's explore what else we can enhance about our plugin:

- Let's use a third-party library to generate Kotlin code instead of doing it manually.
- Our plugin should also accept values directly from the user configuration in their `module.yaml`, in addition to taking them from the properties file.
- Let's introduce a toy task that just prints all the generated sources to the stdout.

### Adding library dependencies

We often don't implement a plugin from scratch but rather use the existing tool or a library and wrap around it.
Amper plugins, being normal Amper modules, can depend on other modules and/or external libraries.
Let's use the `kotlin-poet` library to make our Kotlin code generation more robust and convenient.
In addition to that, let's assume we have a `utils` module in the project.
This module is a collection of some utilities that are used across the project – we'd like to use them in our plugin
implementation as well.

```yaml hl_lines="3-5" title="build-config/module.yaml"
product: jvm/amper-plugin

dependencies:
  - com.squareup:kotlinpoet:2.2.0 # (1)!
  - ../utils # (2)!
```

1.    Plugins support external Maven dependencies.
2.    Plugins support depending on another local module, unless this introduces a dependency cycle, see below.

(For the sake of brevity, we are not going to list the code written with `kotlin‑poet` APIs here,
as the exact code is largely irrelevant in our example.)

??? info "Info: no meta‑build in Amper — plugins can depend on regular modules"
    Amper doesn't have a notion of a _meta‑build_ (e.g., "included builds"/`buildSrc`, etc.). 
    Plugin modules are built inside the same build as the other "production" modules.
    This way, plugins can easily depend on any other project modules (like `utils` in our example),
    as long as there are no physical cyclic dependencies between internal actions.
    !!! success "Example: Self‑documenting"
        A documentation plugin can technically be safely applied to itself, because when the documentation generation
        runs, the plugin's code itself can already be built and can be executed in a task to generate the docs for itself.
    !!! failure "Example: Can't generate resources for itself"
        If a plugin contributes anything to the compilation, it can't be applied to itself,
        because the cyclic dependency is detected:
        ```
        1. task `generateSources` in module `my-plugin` from plugin `my-plugin` (*)
           ╰───> depends on the compilation of its source code
        2. compilation of module `my-plugin` <───────────────╯
           ╰───> needs sources from ──────────────────╮
        3. source generation for module `my-plugin` <─╯
           ╰───> includes the directory `<project-build-dir>/tasks/_my-plugin_generateSources@my-plugin` generated by
        4. task `generateSources` in module `my-plugin` from plugin `my-plugin` (*) <───────────────────────────────╯
        ```

!!! warning
    Currently, Amper plugins can't depend on other plugins meaningfully,
    other than to share some implementation pieces.
    This is not recommended anyway – use common utility modules instead.

### Adding plugin settings

Until now, our plugin just used the fixed values/paths, hardcoded within the plugin, with no ability to change them on the module level.
Here we'll describe a way to "parameterize" the plugin, so users can configure its behavior.

Suppose we want the user to be able to:

- customize the properties file name
- provide additional properties values

Let's whip up our public plugin settings definition:
```kotlin title="build-config/src/settings.kt"
package com.example

import org.jetbrains.amper.plugins.Configurable

@Configurable
interface Settings {
   /** 
    * Properties file name (without extension) 
    * that is located in the module root.
    */
   val propertiesFileName: String get() = "config"
   
   /**
    * Extra properties to generate in addition to the ones read from the 
    * properties file. 
    */
   val additionalConfig: Map<String, String> get() = emptyMap()
}
```

!!! tip inline end "Let's be nice and use KDocs!"
    The provided KDocs are going to be visible in the IDE in the tooltips for plugin settings in `module.yaml`.

Such an interface acts like a *YAML schema* to describe the configuration our plugin may receive from the user.
For that we need the `@Configurable`-annotated public interface with the properties of [*configurable* types](topics/configuration.md#configurable-types) and
optional [*default* values](topics/configuration.md#default-values), expressed as *default getter implementations*.

Now we need to tell Amper which of our [`@Configurable` declarations](topics/configuration.md#configurable-interfaces)
is the root of the plugin settings that users can configure in their module files.
In our case, it's `com.example.Settings`:
```yaml hl_lines="5-6" title="build-config/module.yaml"
product: jvm/amper-plugin

dependencies: # ...

pluginInfo:
  settingsClass: com.example.Settings
```

!!! tip
    It is good practice to provide reasonable defaults for *all* the plugin settings if possible,
    so the user still can use the plugin right away by simply having written, e.g., `build-config: enabled`.

This way, we can now configure our plugin in the app's `module.yaml`:
```yaml hl_lines="3-6" title="app/module.yaml"
plugins:
  build-config:
    enabled: true # (1)!
    propertiesFileName: "konfig" # (2)!
    additionalConfig:
      VERSION: "1.0"
```

1.    We still need to enable our plugin explicitly.
2.    Overrides the default "config" value from Kotlin.

!!! warning
    It is not yet possible to use references (`${...}`) in `module.yaml` files or access the module configuration tree from `plugin.yaml`.
    We are planning on supporting this in some quality in the following releases.

But wait! We've added the plugin settings and even used them to customize the plugin behavior.
But we haven't wired them to our task! Let's fix that.

First on the Kotlin side:
```kotlin hl_lines="6" title="build-config/src/generateSources.kt"
// ...
@TaskAction
fun generateSources(
  @Input propertiesFile: Path,
  @Output generatedSourceDir: Path,
  additionalConfig: Map<String, String>, //(1)!
) {
  // ...
}
```

1.    `additionalConfig: Map<String, String>` parameter does not require an `@Input` annotation,
      because all "plain data" (no references to `Path` within the type) parameters are already considered as task inputs.

And on the "declarative" side:
```yaml hl_lines="5 6" title="build-config/plugin.yaml"
tasks:
  generate:
    action: !com.example.generateSources
      propertiesFile: 
        ${module.rootDir}/${pluginSettings.propertiesFileName}.properties
      additionalConfig: ${pluginSettings.additionalConfig}
      generatedSourceDir: ${taskOutputDir}
  markOutputsAs:
    - path: ${action.generatedSourceDir}
      kind: kotlin-sources
```

`pluginSettings` is a global reference-only property that contains the configured plugin settings for each module the plugin is applied to.
In our case the type of `pluginSettings` would be `com.example.Settings` which we specified in `pluginInfo.settingsClass`.

So, e.g., when the plugin is applied to the `app` module in our example when we refer to the `${pluginSettings.propertiesFileName}` in `plugin.yaml`,
we would get the `"konfig"` value the user specified in their `plugins.build-config.propertiesFileName` in `app/module.yaml`

### Adding another task

As planned, let's now add another task to the plugin that simply reads the already generated sources and prints them to stdout.

```kotlin title="build-config/src/printSources.kt"
package com.example

// import ...

@TaskAction
fun printSources(
  @Input sourceDir: Path,
) {
  sourceDir.walk().forEach { file ->
    println(file.pathString)
    println(file.readText())
  }
}
```

```yaml hl_lines="7-9" title="build-config/plugin.yaml"
tasks:
  generate:
    action: !com.example.generateSources
      # ...
      generatedSourceDir: ${taskOutputDir}

  print:
    action: !com.example.printSources
      sourceDir: ${generate.action.generatedSourceDir}
```

In the line `sourceDir: ${generate.action.generatedSourceDir}` we reference an `@Output` path of another task.
In addition to automatic execution avoidance for individual tasks, Amper automatically infers task dependencies based on matching `@Input` paths with `@Output` paths.
In our example it means that if the task `generate` has a path `/generated/sources` in the `@Output` position, and the task `print` has the *matching* path in the `@Input` position, then `print` will depend on the `generate`.
More on [task dependencies here](topics/tasks.md).

!!! info
    If a task has no declared `@Output`s (like `print` in our example), then no execution avoidance is done for it — it will always run the action.
    This is done because tasks without outputs are almost always introduced for side effects, e.g., diagnostics or deployment.

Now we've added the `print` task, we'd like to use it.
Unlike the `generate` task, it doesn't declare any outputs that are contributed back to the build, so it won't be executed automatically when build/test/run is invoked.
So, to [run](topics/tasks.md#run-a-task) a task manually, one must use the following CLI command:

```shell
$ ./amper task :app:print@build-config
```

That's it for this tutorial!
You can study some specific topics about Amper plugins and/or go try to write one yourself.

## Learn more

!!! info "_Consuming things from_ the Amper build"
    See the dedicated documentation [section](topics/tasks.md#consuming-things-from-the-build) with examples.

!!! tip
    There are plugins that we ourselves have implemented and are already [using in Amper]({{ repo_filetree_url }}/build-sources).
    Feel free to take a look!
    
    - Protobuf
    - Binary Compatibility Validator
    - a couple of purely internal ones, like `amper-distribution`

If you haven't already, check the more detailed reference on the specific topics:

- [Plugin Structure](topics/structure.md)
- [Configuration](topics/configuration.md)
- [References](topics/references.md)
- [Tasks](topics/tasks.md)
