# Tasks

## Task action definition

Task actions are top-level Kotlin functions annotated with `@TaskAction`. 
Restrictions for a task action function:

- must be a top-level, public function
- must return `Unit`
- must not be an extension, generic, `suspend`, `inline`, or have context parameters
- parameter types must be [configurable types](configuration.md#configurable-types)

Parameters are allowed to have default values as per [configurable defaults](configuration.md#default-values).

### Path parameters

Parameters of type `Path` require an explicit role:

- `@Input` if the path points to files/directories read by the action
- `@Output` if the path points to files/directories written by the action

The requirement applies transitively: if a parameter’s type contains a `Path` anywhere inside,
the parameter must still be marked with either `@Input` or `@Output`,
so the build tool knows their semantics.

!!! example
    Examples of types that require the parameters to be annotated with input/output marker:
    
    - `List<Path>`
    - `Map<String, Path>`
    - `Distribution`, defined as:
      ```kotlin
      @Configurable interface Distribution {
        val manifestPath: Path
        val binaryPath: Path
      }
      ```
    - built‑in configurable interfaces that request files, e.g., `ModuleSources`, `Classpath`, etc.

!!! info
    Special built‑in configurable interfaces that are used to request _files_ from the build — such as `ModuleSources`, `Classpath`, or `CompilationArtifact` —
    are **always required to be annotated with `@Input`**.

All non‑`Path`‑referencing parameters are considered **inputs** and do not require any additional annotations.

## Tasks runtime

Tasks are executed inside an isolated JVM environment, and no guarantees are made about the state of static globals from
one task action invocation to the other. 

At the moment tasks are effectively executed inside the Amper JVM using an isolated plugin classloader
that contains only the plugin's runtime classpath. 
This concrete implementation is very much likely to change in the future, so it is advised not to rely on it.

### Runtime API

There is no currently available runtime API exposed for tasks, besides the configuration system.
So plugin authors are free to use whatever libraries they need to implement things.
In the future some runtime APIs that Amper can provide and support will be added.

#### Logging

Use `System.out/err`, Amper will associate the output with the task name in its build log.
Structured logging support is coming soon.

## Execution avoidance

Amper uses a built‑in execution‑avoidance mechanism for task actions by default.
When not disabled, Amper decides whether to rerun an action based on:

- the action execution classpath changes (if the action code is recompiled, tasks using the action need to be rerun)
- effective values of the action arguments that are non‑paths, including property values of configurable interfaces, recursively 
- the state of all gathered file-tree inputs and file-tree outputs declared via `@Input`/`@Output`

!!! note
    Currently, only file attributes and modification time are inspected to compute if a file tree changed.
    No file content checking is performed.  

This behavior is controlled per action by the `executionAvoidance` parameter of `@TaskAction` annotation:

- `ExecutionAvoidance.Automatic` (default) — compute up‑to‑dateness as described above. If a task action declares no outputs, it always re‑runs.
- `ExecutionAvoidance.Disabled` — always re‑run the action regardless of inputs/outputs.
   Use this if the task has side effects and/or its up‑to‑date state needs to be computed in a more complex way.

!!! tip
    When implementing the desired build logic as task actions, keep the execution avoidance in mind. For example, for
    a deployment plugin that builds and publishes a distribution, it is better to have two separate tasks: `build` and `publish`.
    The `build` task can be "incremental" and benefit from automatic execution avoidance because it's easy to declare its inputs and outputs;
    while `publish` task has undeclarable side effects and cannot be incremental at the build system level.

## Task Dependencies

Dependencies between user‑registered tasks are inferred automatically from matching `@Input` and `@Output` paths in their actions:

- If task A declares an `@Output` path and task B declares an `@Input` path that _matches_ it, Amper adds a dependency from B to A.
- Paths are considered _matching_ when they are either equal or one is an ancestor or descendant of the other.
  For example:

    - `/foo/bar` and `/foo/bar/out.txt` are matching
    - `/foo/bar` and `/foo/baz` are not matching

!!! info
    If an `@Input` points inside the build directory but no task produces a matching `@Output`, a warning is issued to help catch misconfigured paths.

There is no way to specify task dependencies **manually** for now.

### Disabling dependency inference for a particular input

Use `@Input(inferTaskDependency = false)` on a path parameter when you do not want a dependency to be inferred, even if another task writes to the same path.

!!! example
    This is needed for “baseline” files where an “update” task writes the baseline and a “check” task reads and compares it.
    If a dependency were inferred, “update” would always run before “check”, hiding problems.
    
    A good example of this case could be seen in the [Binary Compatibility Validator]({{ repo_filetree_url }}/build-sources/binary-compatibility-validator/plugin.yaml).

!!! note
    Disabling dependency inference on an input with `@Input(inferTaskDependency = false)` only affects task wiring.
    The file(s) pointed to by that input are still considered for execution‑avoidance decisions.

## Task Registration

Tasks are registered declaratively in the plugin’s `plugin.yaml` under the `tasks` map.
Each entry creates a task instance with a short name (the map key):

```yaml
tasks:
  myTaskName:
    action: !fully.qualified.function.name
      param1: ...
      param2: ...
    markOutputsAs:
      - path: ...
        kind: ...
```

The task action is specified using the `action` property.
This property requires an explicit YAML type tag, in this example `!fully.qualified.function.name`, to express
which exact action the task uses.
The tag starts with the bang (`!`) and then goes the fully qualified name of the Kotlin `@TaskAction` function.
There is a [note](configuration.md#task-action-types) on how this works on the configuration level.

The properties of the `action` object correspond to parameters of the Kotlin function.

The same task action can be registered multiple times under different task names and with different argument values.
Tasks are registered once in `plugin.yaml`, but they are instantiated per module where the plugin is enabled.

??? question "Why does the concept of a task feel split between Kotlin and YAML?"
    As we want to provide the best tooling and IDE assistance, we want the most information available "declaratively"
    so it can be easily traced and made available quickly and safely.

    The build tool still needs to preprocess Kotlin code to extract task signatures and configurable types.
    So we want to have the least amount of information in Kotlin, mostly only implementation.

    Custom task actions can be reused multiple times in different tasks.
    Also, there can be shared/built‑in universal task actions like `copy`, `download`, `unzip`, etc.
    
    So registering a new task can be as easy as dropping a few lines of YAML (for now) and reusing the ready task action.
    
    Now, inputs/outputs markup is done in Kotlin because it is inherently bound to the action itself 
    (if the action reads from the file, it is an input),
    while `markOutputsAs` is not necessarily so: a generic `unzip` action may be actually unzipping some sources, and
    only a concrete plugin may know that, so it needs to convey it at the registration site.

### Naming and addressing

The task name is local to the plugin. Different plugins may use the same task names without conflicts.

#### Internal name

In logs and in the CLI, a task is addressed as `:<module-name>:<task-name>@<plugin-id>`.
This is also what you see in tasks output and what you pass to the task command to [run a task manually](#run-a-task).

!!! info
    The plugin ID is part of the internal task name.
    By default, it is the plugin module name, unless overridden in `pluginInfo.id`. 
    See the [quick start guide](../quick_start.md#basic-example) for examples of running tasks and customizing the plugin ID.

The internal name of a task should not ideally be exposed and used externally. We are working on making it so. 

## Contributing back to the build

There are a bunch of things the Amper build can consume that can be produced by a custom task.
In order to contribute some typed files back to the build, we need the following:

1. To have a necessary path marked as an `@Output` in the task action
2. To add an entry to the `markOutputsAs` list at the registration site, referring to the path and specifying the desired content kind.
  
Currently supported content kinds:

| Content kind     | Description                                                                       |
|------------------|-----------------------------------------------------------------------------------|
| `kotlin-sources` | A directory containing **Kotlin sources** to be compiled together with the module |
| `java-sources`   | A directory containing **Java sources** to be compiled together with the module   |
| `jvm-resources`  | A directory containing **jvm resources** to be bundled together with the module   |

!!! example
    ```yaml hl_lines="9"
    tasks:
      generate:
        action: !com.example.generateSources
          propertiesFile: ${module.rootDir}/config.properties
          generatedSourceDir: ${taskOutputDir} #(1)!
      markOutputsAs:
        - path: ${action.generatedSourceDir}
          kind: kotlin-sources
    ```
    
    1.    `generatedSourceDir` is `#!kotlin @Output generatedSourceDir: Path` in Kotlin

### Advanced
One can customize the platform and/or main/test scope the content is associated with using the `fragment` clause of the `markOutputsAs` list element.
It has two properties: `modifier: string` and `isTest: boolean`.
The `modifier` string has the same semantics as the suffix one can put after the `@` in the `module.yaml` configuration or
in the names of `src` directories. For example, having:
```yaml
markOutputAs:
  - path: ${action.generatedSourceDir}
    kind: kotlin-sources
    fragment:
      isTest: true
      modifier: ios
```
would make Amper treat the generated sources as if they were put into the `test@ios` directory.

## Consuming things from the build

!!! warning
    Currently, most of the built‑in configurables used to request things from the build are JVM‑only.

### Requesting layout-agnostic module sources

To request module sources, we can use the built‑in `org.jetbrains.amper.plugins.ModuleSources` configurable.
It should always be marked as `@Input`.
The `includeGenerated` property allows the task to depend on the other code generating steps in the build,
like other custom tasks or KSP, and include their results in `sourceDirectories` as well.

!!! example

    === "someKindOfLinter.kt"
        ```kotlin
        @TaskAction
        fun someKindOfLinter(
            @Input sources: ModuleSources,
            moduleName: String,
        ) {
            sources.sourceDirectories.forEach { dir ->
                println("Module `$moduleName` has $dir as its source directory")
            }
        }
        ```
    === "plugin.yaml (just user sources)"
        ```yaml
        tasks:
          lint:
            action: !someKindOfLinter
              moduleName: ${module.name}
              sources: ${module.sources}
        ```
    === "plugin.yaml (including generated sources)"
        ```yaml
        tasks:
          lint:
            action: !someKindOfLinter
              moduleName: ${module.name}
              sources:
                from: ${module.self}
                includeGenerated: true
        ```

!!! tip
    Using `ModuleSources` even in the simple case is more robust than just referring to `${module.rootDir}/src`,
    because it takes module layout into account (and multiplatform source directories — coming soon).

### Requesting classpath/ad-hoc dependency resolution

Amper models a request to get a resolved classpath as the built‑in `org.jetbrains.amper.plugins.Classpath` configurable.
It also must always be an `@Input`.
There are a bunch of convenience reference‑only properties like `module.runtimeClasspath` or `module.compileClasspath`,
but one can also construct a `Classpath` spec to request an ad hoc dependency resolution.

!!! example

    === "packageClasspath.kt"
        ```kotlin
        @TaskAction
        fun packageClasspath(
            @Input appClasspath: Classpath,
            @Input extraClasspath: Classpath?,
        ) {
            appClasspath.resolvedFiles.forEach {  it.copyTo(...) }
            ...
        }
        ```
    === "plugin.yaml"
        ```yaml
        tasks:
          package:
            action: !packageTheApp
              appClasspath: ${module.runtimeClasspath} #(1)!
              extraClasspath: #(2)!
                - foo:bar:1.0
                - ${pluginSettings.extraDependency}
        ```

        1.    A convenience value for the `#!yaml { dependencies: [ ${module.self} ], scope: runtime }`
        2.    Resolves arbitrary dependencies (local, Maven) in the specified scope (`runtime` by default)

### Requesting a module compilation result

As opposed to the `runtimeClasspath`, sometimes we want to get just the JAR that is the result of the module's compilation.
To do that we can refer to the `${module.jar}` which has the `org.jetbrains.amper.plugins.CompilationArtifact` type.

!!! example
    === "copyJar.kt"
    ```kotlin
    @TaskAction
    fun copyJar(jar: CompilationArtifact) {
        jar.artifact.copyTo(...)
    }
    ```
    === "plugin.yaml"
    ```yaml
    tasks:
      copy:
        action: !copyJar
          jar: ${module.jar} #(1)!
    ```
    
    1.    A convenience value for the `#!yaml { from: ${module.self} }`


## Run a task

If the task [contributes](#contributing-back-to-the-build) something to the build,
it probably doesn't ever need to be invoked explicitly by hand. It is invoked automatically by the build system or tooling
to ensure that generated contents are up to date.

If the task needs to serve as an entry point to the build, then, currently,
it needs to be run manually via the Amper CLI using the task's [internal name](#internal-name):

```shell
$ ./amper task :moduleName:taskName@pluginId
```

We are working on providing a proper UX for calling plugin tasks.

## Learn more

To see more practical examples of how to write tasks,
you are welcome to check out the [quick start guide](../quick_start.md) and our
plugin samples in the `build-sources` directory of the Amper project.