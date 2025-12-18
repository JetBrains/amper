---
description: A bird-eye view of the Amper plugins features and plans.
---
# Plugins in Amper

!!! tip "Important"
    1. It is a preview, and things are guaranteed to change. Try it out and give us your feedback!
    2. Support for KMP is limited; the current preview is focused on JVM-only projects mostly. 
       We may cover KMP scenarios better as the plugin subsystem evolves.

You can read this functionality summary or jump right into the [Quick Start guide](quick-start.md).

## Ideas at the core of the preview

Conceptually, any Amper plugin consists of two parts:

1. A **configurable** part that is quick to read and fully exempt from arbitrary code execution:
    - `plugin.yaml`
    - `@TaskAction` function signatures, `@Configurable` interfaces and enums

2. An **executable** part that contains arbitrary task‑action implementation code:
    - Normal code in `src/`, including its dependencies — other local modules and libraries

The *configurable* part is used to quickly and safely build the project model for tooling in a _traceable_ way.
This way we can make **project import safe and nearly transparent** and
provide **user‑friendly, actionable diagnostics** that lead to **precise problem locations** —
for example, why tasks are wired the way they are, or why certain Maven dependencies are added one way or another.
Consequently, this part has limited expressiveness; we are exploring how minimal it can be while remaining useful.

On the other hand, the *executable* part is only needed at build execution time and has no significant restrictions.

## Summary of what is currently extensible

Plugins can currently extend the build with custom build actions — [tasks](topics/tasks.md).
Such tasks can receive [configuration](topics/configuration.md), consume file‑system locations (`Path`s), or produce them.
They can _contribute_ specific typed entities to the build and/or _consume_ them from the build.

Task actions can consume:

- [Typed contents](topics/tasks.md#consuming-things-from-the-build) from the build:
    - module sources/resources (via built‑in `ModuleSources` configurable, e.g., `${module.sources}`/`${module.resources}`)
    - module compilation result (via built‑in `CompilationArtifact` configurable, e.g., `${module.jar}`)
    - module runtime/compilation classpath (via built‑in `Classpath` configurable, e.g., `${module.runtimeClasspath}`/`${module.compileClasspath}`)
    - resolve arbitrary Maven dependencies as an ad hoc classpath (via a custom `Classpath` configuration, like `myClasspath: [ "group:name:version", ... ]`)
- arbitrary file trees via specified paths

Task actions can produce:

- [Typed contents](topics/tasks.md#contributing-back-to-the-build):
    - Kotlin/Java sources (via `markOutputAs`)
    - resources (via `markOutputAs`)
- arbitrary file trees in specified paths

For more information on these features, see the KDocs on these built‑in configurable interfaces.

## Missing functionality

### Planned for upcoming releases

- Packaging and publishing plugins
- Bundled module templates in plugins to contribute to the configuration of the modules they are applied to
- A simpler way of authoring trivial plugins consisting of a single task, template, etc.
- Dependencies between plugins

### We may consider providing solutions for

- Supporting conditionals/loops in the declarative configuration
- Alternatives to YAML as the configuration language

!!! question "Your requests and reports are welcome!"
    File us a plugins-related issue [here](https://youtrack.jetbrains.com/newIssue?project=AMPER&c=Type+Bug&c=tag+amper-plugins-report).
