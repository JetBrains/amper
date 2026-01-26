---
description: Let's migrate your Maven project to Amper with the help of our semi-automated converter.
---
# Migrating from Maven

This section describes how to convert an existing Maven project to Amper.

Amper provides a semi-automated tool to do the bulk of the conversion for you.
It works on a best-effort basis, so some projects require some additional tweaks
after the converter runs.

To run the migration tool, go to the root of your Maven project:

```bash

cd /path/to/your/maven/project

```

Download the [Amper wrapper script](../cli/provisioning.md/#whats-the-wrapper-script).

--8<-- "includes/cli-install.md"

and then run:
```bash
./amper tool convert-project
```

Path to `pom.xml` can be provided explicitly via `--pom` argument. `pom.xml` is a starting point. If it's a part 
of the reactor, all related modules are converted.

During the conversion, the tool puts Amper files into corresponding maven modules's folders. If `project.yaml` or one of
the corresponding `module.yaml` files already exists, by default converter fails. To adjust this behavior, 
`--overwrite-existing` flag can be used.

## Limitations

The converter doesn't support:

- extensions
- profiles
- Maven plugins besides `maven-compiler-plugin`, `kotlin-maven-plugin` and `spring-boot-maven-plugin`
