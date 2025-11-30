# Kotlin compiler plugins

Compiler plugins are a powerful feature of Kotlin that allow you to extend the language with new features.
There is a handful of bundled compiler plugins that can be enabled in Amper.

!!! warning "Third-party compiler plugins are not supported at the moment"

## All-open

The [All-open](https://kotlinlang.org/docs/all-open-plugin.html) compiler plugin allows you to mark entire groups of
classes as `open` automatically, without having to mark each class with the `open` keyword in your sources.

To enable All-open, add the following configuration to your module file:

```yaml
settings:
  kotlin:
    allOpen:
      enabled: true
      annotations: # (1)!
        - org.springframework.context.annotation.Configuration
        - org.springframework.stereotype.Service
        - org.springframework.stereotype.Component
        - org.springframework.stereotype.Controller
        - ...
```

1.   Lists the annotations that mark classes as open.

Or you can use one of the preconfigured presets that contain all-open annotations related to specific frameworks:

```yaml
settings:
  kotlin:
    allOpen:
      enabled: true
      presets:
        - spring
        - micronaut
```

!!! success "Already covered by the [Spring Boot support](../builtin-tech/spring.md)"

    The All-open plugin is invaluable for Spring projects, because Spring needs to create proxy classes that extend
    the original classes. This is why using `springBoot: enabled` automatically enables the All-open plugin with the
    Spring preset.
 

## No-arg

The [No-arg](https://kotlinlang.org/docs/no-arg-plugin.html) compiler plugin automatically generates a no-arg 
constructor for all classes marked with the configured annotations.

To enable [No-arg](https://kotlinlang.org/docs/no-arg-plugin.html), add the following configuration:

```yaml
settings:
  kotlin:
    noArg:
      enabled: true
      annotations: 
        - jakarta.persistence.Entity
        - ...
```

Or you can use one of the preconfigured presets that contain no-arg annotations related to specific frameworks:

```yaml
settings:
  kotlin:
    noArg:
      enabled: true
      presets: 
        - jpa
```

## Power Assert

The [Power Assert](https://kotlinlang.org/docs/power-assert.html) compiler plugin enhances the output of failed 
assertions with additional information about the values of variables and expressions:

```
Incorrect length
assert(hello.length == world.substring(1, 4).length) { "Incorrect length" }
       |     |      |  |     |               |
       |     |      |  |     |               3
       |     |      |  |     orl
       |     |      |  world!
       |     |      false
       |     5
       Hello
```

To enable Power Assert, add the following configuration:

```yaml
settings:
  kotlin:
    powerAssert: enabled
```

By default, Power Assert is enabled for `kotlin.assert` function. You can enable it for other functions as well:

```yaml
settings:
  kotlin:
    powerAssert:
      enabled: true
      functions: [ kotlin.test.assertTrue, kotlin.test.assertEquals, kotlin.test.assertNull ]
```

## Compose

The Compose compiler plugin is covered in the mode general
[Compose Multiplatform](../builtin-tech/compose-multiplatform.md) section.

## Kotlinx Serialization

The Kotlinx Serialization compiler plugin is covered in the more general 
[Kotlin Serialization](../builtin-tech/kotlin-serialization.md) section.

## Parcelize

The Parcelize compiler plugin is covered in the [Android](../product-types/android-app.md) section.

## Lombok

The Lombok compiler plugin is covered in the more general [Lombok](../builtin-tech/lombok.md) section.
