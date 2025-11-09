# Templates

In modularized projects, there is often a need to have a certain common configuration for all or some modules.
Typical examples could be a testing framework used in all modules or a Kotlin language version.

Amper offers a way to extract whole sections or their parts into reusable template files. 
These files are named `<name>.module-template.yaml` and have the same structure as `module.yaml` files.

A templates is applied to a `module.yaml` file by it to the `apply:` section:

```yaml title="module.yaml"
product: jvm/app

apply: 
  - ../common.module-template.yaml
```

```yaml title="../common.module-template.yaml"
test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.10

settings:
  kotlin:
    languageVersion: 1.8
```

Sections in the template can also have `@platform`-qualifiers.

!!! note

    Template files can't have `product:` and `apply:` sections (they can't be recursive).

Templates are applied one by one, using the same rules as 
[platform-specific dependencies and settings](multiplatform.md#dependencysettings-propagation):

- Scalar values (strings, numbers etc.) are overridden.
- Mappings and lists are appended.

Settings and dependencies from the `module.yaml` file are applied last. The position of the `apply:` section doesn't matter, the `module.yaml` file content always has precedence E.g.

```yaml title="common.module-template.yaml"
dependencies:
  - ../shared

settings:
  kotlin:
    languageVersion: 1.8
  compose: enabled
```

```yaml title="module.yaml"
product: jvm/app

apply:
  - ./common.module-template.yaml

dependencies:
  - ../jvm-util

settings:
  kotlin:
    languageVersion: 1.9
  jvm:
    release: 8
```

After applying the template the resulting effective module is:

```yaml title="module.yaml"
product: jvm/app

dependencies:  # lists appended
  - ../shared
  - ../jvm-util

settings:  # objects merged
  kotlin:
    languageVersion: 1.9  # module.yaml overwrites value
  compose: enabled        # from the template
  jvm:
    release: 8   # from the module.yaml
```
