# :intellij-lombok: Lombok

[Project Lombok](https://projectlombok.org/) is a Java library that generates getters, setters, builders, 
and other boilerplate code from annotations.

Amper provides the `settings.lombok` option to configure Lombok conveniently in your project:
```yaml
settings:
  lombok: enabled
```

When Lombok is enabled, Amper adds the `lombok` dependency, the annotation processor for Java, 
and the Kotlin compiler plugin.

You can also customize the version of the Lombok library using the full form of the configuration:
```yaml
settings:
  lombok:
    enabled: true
    version: 1.18.42
```