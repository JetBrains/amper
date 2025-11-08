# References

Amper uses its own value‑reference system using the `${...}` syntax.
Throughout the documentation this syntax is referred to as **Amper references** or simply **references**.
Currently, its usage is limited to plugin configuration files (`plugin.yaml`).

!!! info
    Standard YAML anchors (`&`) and aliases (`*`) are not supported in Amper and Amper references are used instead.

## Syntax

References are specified using the `${...}` syntax.
Inside the braces a **property path** is specified — one or more dot‑separated reference parts, e.g., `${start.one.two.three}`

### String interpolation

One or more references can be embedded in YAML scalar values — **string interpolation**.
This works for properties that expect either `string` or `path` types.

- For strings: `prefix-${some.name}-suffix`
- For paths: `base/${something.dir}/${file.name}.name`

References pointing to values of `string`, `path`, `integer`, or `enum` can be used in string interpolation.

!!! note
    References inside mapping keys are not yet allowed.
    So constructions like this are not permitted:
    ```yaml
    myMap:
      ${module.name}: "value"
    ```

## Reference resolution

Assume we have a reference `${foo.bar.baz}`:

- `foo` is the _starting part_
- `bar` and `baz` are the remaining parts

Reference resolution is done relative to the location of the reference in the value tree.

1. The _starting part_ is resolved, i.e., the _property_ with the name matching the _starting part_ is found,
   **searching upward in the _lexical scopes_**.
2. The remaining parts are resolved consequently as member-properties against the starting value.  

The _lexical scope_ consists of all property names in the given YAML mapping, including names that are implicitly present there.
Such implicit properties are:

- properties that are not specified explicitly but have [default values](configuration.md#default-values)
- [reference-only](#reference-only-properties) properties

!!! example "Example: how scopes are defined"
    ```yaml
    sibling-object:
      foo: 1
      bar: 4 # scopes here: (1)
    object:
      # foo: 1 (by default)
      # provided: 2 (reference-only)
      list:
        - quu: 'a'
          buu: 'b' # scopes here: (2)
      baz: 3
      bar: 4 # scopes here: (3)
    ```
    
    1.    (in the order of the lookup):
    
          1. `{foo, bar}`
          2. `{sibling-object, object}`
    
    2.    (in the order of the lookup):
    
          1. `{quu, buu}`
          2. `{foo, provided, list, baz, bar}`
          3. `{sibling-object, object}`
    
    3.    (in the order of the lookup):
          
          1. `{foo, provided, list, baz, bar}`
          2. `{sibling-object, object}`

Cyclic references are not allowed!
This includes references that ultimately point to each other or to a direct sub- or super-tree of each other.

!!! info
    There is no way to refer to the list element (using indexes or otherwise).
    So `${myList.0.foo}` is not possible.

!!! info
    Not every built‑in property in `plugin.yaml` can be referenced (be the final value that a reference resolves to).
    Some properties are there purely as configuration DSL/skeleton/sections, e.g., `markOutputsAs` list or the `tasks` map.
    Referencing these things directly makes no sense and is forbidden.
    They can, however, appear as the starting/intermediate reference part, e.g., `tasks.myTask.action.myInput`.

### Type compatibility

The resolved value's type must be assignable to the property's type:

- `null` values are assignable to nullable (`| null`) types.
- `string`, `path`, `integer`, and `enum` values are assignable to `string` properties.

## Reference-only properties

The properties below are available for referencing in `plugin.yaml` and are read‑only.
Their values are provided by Amper itself, and they are designed to provide the plugin author 
with the necessary information to configure the plugin logic.

### Global

These properties are available in the root scope:

```yaml
# module: { ... }
# pluginSettings: { ... }

tasks:
  myTask: {...}
```

| Property path              | Type                                             | Description                                                                                                |
|----------------------------|--------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `pluginSettings`           | the type specified in `pluginInfo.settingsClass` | The plugin’s settings object from the `plugins.<plugin-id>` block of the module the plugin is applied to.  |
| `module.name`              | `string`                                         | Module display name.                                                                                       |
| `module.rootDir`           | `path`                                           | Absolute path to the module root (where `module.yaml` is).                                                 |
| `module.runtimeClasspath`  | `Classpath`                                      | Resolved runtime classpath (JVM, main).                                                                    |
| `module.compileClasspath`  | `Classpath`                                      | Compile classpath plus the module’s compilation result.                                                    |
| `module.kotlinJavaSources` | `ModuleSources`                                  | Kotlin and Java sources (JVM, main).                                                                       |
| `module.resources`         | `ModuleSources`                                  | Resources (JVM, main).                                                                                     |
| `module.jar`               | `CompilationArtifact`                            | Compiled JAR (JVM, main).                                                                                  |
| `module.self`              | `Dependency.Local`                               | A dependency pointing to the module itself                                                                 |

!!! note
    `pluginSettings` is defined only if a plugin has a [settings class](configuration.md#plugin-settings).

### Task-scoped

These properties are available in the lexical scope for every task:

```yaml
tasks:
  myTask:
    # taskOutputDir
    action: {...}
```

| Property path    | Type   | Description                                   |
|------------------|--------|-----------------------------------------------|
| `taskOutputDir`  | `path` | Unique output directory for the given task.   |

!!! warning
    Sometimes a user‑defined property may clash with another one or with a reference‑only one.
    For example:
    ```yaml
    action: !myAction
      module: ${module.name}
    ```
    As the lookup of the starting part happens upwards rather than from the root, 
    this will lead to a cyclic‑reference error.
    So instead of the reference-only `module` from the root scope the resolution will find the `module` property in the current scope.
    These reference resolution shortcomings are known;
    for now, avoid name clashes to prevent such situations.