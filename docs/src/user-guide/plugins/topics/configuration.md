---
description: A guide to Amper's plugin configuration system.
---
# Configuration

This section provides details on how to configure tasks and plugin settings, how the schema works, default values and more.

!!! question "YAML?"
    Amper currently uses "Amper-flavored" YAML as the configuration language.
    YAML is flexible, and it allowed us to move fast with prototyping solutions, plugins included.
    However, we are aware of YAML's shortcomings and are exploring the possibility of replacing YAML with a custom 
    language tailored for our needs in the future.  

## Configurable types

**Configurable types** are types that Amper allows in plugin and task configurations.
Their values are set in YAML configurations, and used in Kotlin _task actions_.

The following table lists the configurable types and their Kotlin and YAML representations:

| Amper type               | Kotlin type                         | YAML structure                         |
|--------------------------|-------------------------------------|----------------------------------------|
| `string`                 | `kotlin.String`                     |scalar                                  |
| `boolean (true | false)` | `kotlin.Boolean`                    |scalar                                  |
| `integer`                | `kotlin.Int`                        |scalar                                  |
| `path`                   | `java.nio.file.Path`                |scalar                                  |
| `enum E`                 | `enum class E`                      |scalar                                  |
| `sequence [T]`           | `kotlin.collections.List<T>`        |sequence                                |
| `mapping {string : T}`   | `kotlin.collections.Map<String, T>` |mapping/sequence of pairs               |
| `object T`               | `@Configurable interface T`         |mapping + [others](#shorthand-notation) |
| `T | null`               | `T?`                                |"null" scalar                           |

Task action parameters and properties in `@Configurable` interfaces are only allowed to be of _configurable_ types. 

!!! note
    Currently, any custom configurable type — for example, a `@Configurable` interface or an enum —
    defined in plugin `A` cannot be reused in plugin `B`, even if the module of plugin `A` has module `B` as its dependency.
    The tool will issue an "Unexpected schema type" diagnostic.
    This restriction may be lifted in the future.

There are also built‑in [variant types](#variant-types), but the mechanism is not yet allowed in user code.

## Configurable interfaces

A configurable interface is a public Kotlin interface annotated with the `@Configurable` annotation.
The following restrictions apply to this interface:

- methods, superinterfaces, generics are not allowed
- it may only have read-only non-extension (`#!kotlin val`) properties
- all its properties must be of a _configurable type_ themselves
- its properties may have a default getter implementation, depending on their type.
  See the [Default values](#default-values) section. 

```kotlin title="Valid configurable declarations"
@Configurable
interface MySettings {
    /**
    * Note: KDocs on configurable entities are visible to the tooling
    */
    val booleanSetting: Boolean
    val intSetting: Int
    val stringSetting: String
    val nested: Nested
    val pathSetting: Path
    val mapSetting: Map<String, String>
    val listSetting: List<Nested>
}

@Configurable
interface Nested {
    val enumSetting: MyEnum
    val nullableStringSetting: String?
}

enum class MyEnum { Hello, Bye }
```

### Plugin settings

A `@Configurable` interface may be specified in a plugin's `module.yaml` to expose it as user‑facing plugin settings:
```yaml title="module.yaml"
pluginInfo:
  settingsClass: com.example.MyPluginSettings
```

The property with the name `enabled` is reserved in such interfaces.

Then the object with this type (e.g., `com.example.MyPluginSettings | null`) becomes available under `plugins.<plugin-id>`
in every module in the project.
An additional synthetic boolean `enabled` [shorthand](#shorthand-notation) property is present in the object to control
if the plugin is applied to the module or not.

## Enums

Generally, any enum is allowed in the configuration. You may want to specify custom names for the enum constants to be
used in configuration, using the `#!kotlin @EnumValue` annotation.
Otherwise, the `name` of the entry is used in the configuration. 

=== "Kotlin"
    ```kotlin
    enum class MyEnum {
        Hello,
        @EnumValue("byeBye")
        Bye,
    }
    ```
=== "YAML"
    ```yaml
    myEnum1: Hello
    myEnum2: byeBye
    ```

## Default values

Properties and task action parameters are allowed to have default values specified in Kotlin.

Properties use _default getter implementation_ syntax. The getter must have an _expression body_:

```kotlin
@Configurable interface Settings {
    val myBoolean get() = false
    val myString get() = "default"
}
```

Task parameters use the regular default value syntax:

```kotlin
@TaskAction fun myAction(
    myBoolean = false,
    myString = "default",
) { /*...*/ }
```

| Amper type                     | Supported explicit default values                    |
|--------------------------------|------------------------------------------------------|
| `string`, `boolean`, `integer` | Kotlin constant expression of the appropriate type   |
| `enum E`                       | enum constant references, e.g., `E.Constant`         |
| `path`                         | not supported yet                                    |
| `sequence [T]`                 | `emptyList()`                                        |
| `mapping {string : T}`         | `emptyMap()`                                         |
| `T | null`                     | `null` (not required - implicit default)             |
| `object T`                     | not supported (instantiated implicitly, see the note)|

!!! note
    Properties of object type can't have an explicit default value.
    Instead, all objects are instantiated using the default values of their properties combined with the values
    provided on the configuration (YAML) side. If any required properties (those with no default value) remain
    unconfigured, an error is reported and the configuration is rejected.

## Advanced

!!! info
    Although these features are present in the typing/configuration system and are used in some built‑in APIs,
    they are **not yet ready** to be used by plugin authors in their own Kotlin code.

You may read the following sections at your leisure if you seek to better understand
how dependency notation, task actions or "short forms" work in Amper.

### Shorthand notation

Some built‑in `@Configurable` interfaces, e.g., `Classpath`, allow the "shorthand" notation.
They have a property marked with the `#!kotlin @Shorthand` internal annotation.
This enables objects of the type to be constructed in a "short" form: not from the YAML mapping, but from the
value of the type that the shorthand property has. Other properties are then set to their default values.

For example, `#!yaml classpath: [ "foo:bar:1.0" ]` is a short form of
```yaml
classpath:
  dependencies: [ "foo:bar:1.0" ]
```
because the `dependencies` property is marked as a "shorthand".

!!! warning
    Shorthands do not currently work with references.
    E.g., if there's a property `foo` of the `sequence[Dependency]` type,
    then `#!yaml classpath: ${foo}` will not work. So, the expanded form `#!yaml classpath: { dependencies: ${foo} }` is
    required.

The special case for the shorthand notation is when the shorthand property is a boolean.
Then, instead of the `true` keyword, one needs to write the property name itself, e.g., `enabled`.
For example, [plugin settings](#plugin-settings) have an implicit synthetic `enabled` property which is a shorthand.

### Variant types

On the Kotlin side, they are modeled as a `@Configurable` `sealed` interface.
When a variant type is expected, there is a need to express which exact variant is being provided in the configuration.
This ability to express the exact type is not yet designed in Amper and generally looks unintuitive in YAML.

An example of variant type is the built‑in `Dependency` type, which can be a local module dependency (`Dependency.Local`)
or an external Maven dependency (`Dependency.Maven`). Each of these subtypes has a `@DependencyNotation`-annotated property.

The discrimination between the subtypes is done based on the value of that property:

- if the value starts with a `.`, it is read as a local module dependency 
- otherwise, it is read as a Maven dependency

#### Task action types

Technically, task's `action` property also has a variant type.
This type is synthetic, and its variants are also synthetic types
that are based on all the signatures of all the `@TaskAction` functions in the plugin,
so all the task parameters become properties with the correspodning types.
And in this case an **explicit YAML type tag** is required to communicate the exact type.