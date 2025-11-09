# Kotlin compiler plugins

!!! warning "Third-party compiler plugins are not supported at the moment"

## all-open

To enable [all-open](https://kotlinlang.org/docs/all-open-plugin.html), add the following configuration:

```yaml

  settings:
    kotlin:
      allOpen:
        enabled: true
        annotations: 
          - org.springframework.context.annotation.Configuration
          - org.springframework.stereotype.Service
          - org.springframework.stereotype.Component
          - org.springframework.stereotype.Controller
          - ...

```

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

## no-arg

To enable [no-arg](https://kotlinlang.org/docs/no-arg-plugin.html), add the following configuration:

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
