---
description: |
  Apply Maven plugins to your Amper project to run tests, generate sources, check code style, produce reports, and more.
  Learn how to declare, enable, configure, and extend Maven plugins across single- and multi-module projects.
---

# Applying Maven plugins

Maven plugins are tools in the JVM ecosystem that perform tasks during a project's build, such as running tests,
generating source code, checking code style, or producing reports. Amper supports integrating Maven plugins into
JVM modules (modules with either `jvm/app` or `jvm/lib` product type).

!!! warning
	
	Maven plugins are only supported for JVM-only modules.

## Setup

Maven plugins are declared project-wide and then enabled per-module.

First, declare the Maven plugin coordinates in your `project.yaml` under the `mavenPlugins` key.
Each entry uses standard Maven coordinates notation (`groupId:artifactId:version`):

```yaml title="project.yaml"
modules:
  - app

mavenPlugins:
  - org.apache.maven.plugins:maven-surefire-plugin:3.5.3
  - org.apache.maven.plugins:maven-checkstyle-plugin:3.6.0
```

Each Maven plugin exposes one or more _goals_ (mojos). A mojo is enabled in the relevant `module.yaml` using the
`pluginArtifactId.goalName` key (`enabled` shortcut):

```yaml title="app/module.yaml"
product: jvm/app

mavenPlugins:
  maven-surefire-plugin.test: enabled
```

Fully qualified form:
```yaml title="app/module.yaml"
product: jvm/app

mavenPlugins:
  maven-surefire-plugin.test:
    enabled: true
```

Setting the value to `enabled` activates the goal with its default configuration.

## Configuration

To customize a goal, replace the `enabled` shorthand with a full configuration block:

```yaml title="module.yaml"
product: jvm/app

mavenPlugins:
  maven-surefire-plugin.test:
    enabled: true
    configuration:
      includes:
        - "*Smoke*"
```

The keys inside `configuration` directly correspond to the goal's parameters as documented by the respective plugin.
Amper reads the plugin's descriptor to resolve parameter types, so IDE completion and validation are available for
supported parameter types.

!!! warning
	
	Currently, complex parameter types (POJOs) are not supported.

For configuration parameters with `PlexusConfiguration` type, Amper supports passing raw XML:

```yaml title="module.yaml"
product: jvm/app

mavenPlugins:
  maven-enforcer-plugin.enforce:
    enabled: true
    configuration:
      rules: "
        <rules>
          <requireJavaVersion>
            <version>[21,)</version>
          </requireJavaVersion>
        </rules>"
```

### Adding extra plugin dependencies

Some mojos require additional dependencies that are not bundled with the plugin itself. Add them under the
`dependencies` key of the mojo configuration:

```yaml title="module.yaml"
product: jvm/app

mavenPlugins:
  maven-checkstyle-plugin.checkstyle:
    enabled: true
    dependencies:
      - io.spring.nohttp:nohttp-checkstyle:0.0.11
    configuration:
      configLocation: ./nohttp-checkstyle.xml
      includes: "**/*"
```

# Source generation capability

Maven plugins that generate sources integrate with Amper's compilation pipeline automatically.

Example of using a protobuf code generator:

```yaml title="project.yaml"
modules:
  - app

mavenPlugins:
  - io.github.ascopes:protobuf-maven-plugin:2.12.0
```

```yaml title="app/module.yaml"
product: jvm/app

dependencies:
  - com.google.protobuf:protobuf-kotlin:4.33.0

mavenPlugins:
  protobuf-maven-plugin.generate:
    enabled: true
    configuration:
      protocVersion: 4.33.0
      sourceDirectories: [ ./src ]
      kotlinEnabled: true
```

# Executing Maven goals

Each enabled goal becomes an Amper task named `pluginArtifactId.goal`, scoped to its module.

### Lifecycle integration

Mojo goals with a default Maven phase are automatically wired into the Amper build lifecycle on a best-effort basis.
For instance, the `generate-sources` phase and respective `protobuf-maven-plugin.generate` task will be run before Amper calls the compiler.

!!! warning
	
	Mojos that do not have a default phase defined are not automatically wired into the Amper lifecycle.

### Running a mojo explicitly

Use the `task` command with the fully qualified maven mojo goal task name `:moduleName:pluginArtifactId.goal`. For example:

```
./amper task :app:maven-surefire-plugin.test
./amper task :app:maven-checkstyle-plugin.checkstyle
./amper task :app:jacoco-maven-plugin.report
```

# Getting results from Maven Mojo executions

### Output files

All files produced by mojos should be written into a `maven-target/` subdirectory inside Amper's build output.
Report mojos (those implementing the Maven reporting API) additionally generate an HTML file under
`maven-target/reports/`. Example output tree:

```
build/
└── maven-target/
    ├── jacoco.exec
    └── reports/
        └── checkstyle.html
```

!!! warning
	
	Some Maven plugins that do not rely on the Maven project's build directory
	(for example, because they define custom output directories themselves) may produce output elsewhere.
	Please refer to the documentation of those plugins in such cases.

# Known limitations
- **JVM only**: Maven plugins can only be used in JVM platform modules.
- **No general guarantees**: Maven plugin mojos are executed on a best-effort basis, 
but some plugins may rely on Maven APIs or capabilities that Amper does not support.
- **Single execution per mojo only**: Currently, only a single mojo execution will be performed per Amper module. 
  There is no support for configuring multiple executions.
- **No Maven extensions**: Maven extensions modify the Maven build lifecycle, which is not suitable for Amper projects.
- **No custom dependency resolution**: Plugins that override or extend Maven's dependency resolution mechanism are not supported.
- **No report aggregation**: Currently, the only supported way to produce a report is to run the mojo directly.
