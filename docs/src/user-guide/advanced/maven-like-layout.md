---
description: |
  Amper is opinionated about where to put your sources, resources, and tests.
  The maven-like module layout allows to keep the same directory structure as Maven and Gradle, which is especially 
  useful when transitioning from these tools.
---
# Maven-like module layout

Amper is opinionated about where to put your sources, resources, and tests (see the 
[standard project layout](../basics.md#project-layout)).

When transitioning from other tools, it would be tedious to move all (re)source files around in addition to converting
the build configuration files. To smoothen the transition, Amper provides an alternative layout that is compatible with
Maven and Gradle. The layout conforms to the
[Maven Standard Directory Layout](https://maven.apache.org/guides/introduction/introduction-to-the-standard-directory-layout.html).

Example:

```
module/
├── module.yaml
└── src/
    ├── main/
    │   ├── java/
    │   │   └── Main.java
    │   ├── kotlin/
    │   │   └── func.kt
    │   └── resources/
    │       └── input.txt
    └── test/
        ├── java/
        │   └── JavaTest.java
        ├── kotlin/
        │   └── KotlinTest.kt
        └── resources/
            └── test-input.txt
```

!!! note

    There is no difference between `java/` and `kotlin/` folders, Amper will look for java and kotlin sources in both
    folders. It is only necessary for the sake of transitioning simplicity.

Choosing the file layout is possible per module.

To enable the maven-like module layout, add the following to the `module.yaml` file:

```yaml
layout: maven-like
```
