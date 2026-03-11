# Maven converter

Converts projects from Maven to Amper.

## Overview

It reads the Maven model by building plexus container. The implementation is somehow inspired by what Gradle
[did](https://github.com/gradle/gradle/blob/9139c5f77d30b0a7d37b87a504f853d23b12d5d0/platforms/software/build-init/src/main/java/org/gradle/unexported/buildinit/plugins/internal/maven/MavenProjectsCreator.java).

We treat maven subprojects with `packaging = pom` as subproject aggregators, and convert only subprojects with
`packaging = jar` to the actual Amper modules (`module.yaml`).

We use subprojects with `packaging = pom` to populate `project.yaml` with Amper submodules.

We reused trees (`org.jetbrains.amper.frontend.tree.TreeNode`) data structure from `frontend-api` module and rely on a
merging and refining process. So by traversing the Maven model, we build an Amper forest in several passes, and each
pass builds a new forest and merges it with the resulting one afterward.

## YAML comments

There is a whitelist of plugins that are supposed to be fully catered by Amper features, and the rest are delegated
to the Maven compatibility layer implemented in `amper-maven-plugins-compatibility`. During this process we might
discover that the layer does not support some of the configuration setups (like when the goal is configured
in different several executions), hence we need to notify users that we know this configuration won't work, and we try
to be explicit about that. This implies that we need to somehow reliably notify users about the problem, and YAML
comments are the most obvious way to do that. To add YAML comments into the process, it's been decided not to
incorporate them into trees data structure, but to map comments to the corresponding YAML keys and pass comments to the
renderer because trees data structure is not supposed to be used for rendering but for parsing PSI and being
intermediate structure.

## Goal discovery and type-safety

Not all goals are attached to a phase or particular execution. However, there are some use cases where you still need to
call this goal, like database migrations
in [liquibase](https://docs.liquibase.com/pro/integration-guide-4-33/maven-goals). Also, there are some maven projects
where because of plugin configuration schema evolution there were some properties left in XML configuration of the goal.
We can't just translate that into Amper because Amper rely on type-safe schema, which is generated for Maven plugins
based on `plugin.xml`. So that goal discovery also relies on `plugin.xml` parsing (it implies downloading plugins in the
process (without transitive dependencies)).

## Testing

Integration tests related with it are located in `org.jetbrains.amper.cli.test.MavenConvertTest` in the module
`amper-cli-test`.

